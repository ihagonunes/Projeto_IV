"""Pipeline em memoria. Sem banco, arquivos de entrada, exportacao ou CLI real."""
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, localcontext
from itertools import combinations
from uuid import UUID
import warnings
import numpy as np
from sklearn.cluster import KMeans, AgglomerativeClustering
from sklearn.preprocessing import RobustScaler
from sklearn.metrics import (silhouette_score, davies_bouldin_score,
    calinski_harabasz_score, adjusted_rand_score)
from sklearn.exceptions import ConvergenceWarning
from threadpoolctl import threadpool_limits

METRICAS=('request_count','input_tokens','output_tokens')
FEATURES=tuple('media_mensal_'+m for m in METRICAS)
SENSIBILIDADE='media_modelos_distintos_mes'
SEMENTES=(17,29,43)
KS=tuple(range(2,9))

class Bloqueio(ValueError):
    """Mensagem fixa; nunca inclui identificadores ou valores da entrada."""

@dataclass(frozen=True,repr=False)
class Usuario:
    chave: tuple[str,str]
    meses_observados: int
    medias: tuple
    media_modelos: Decimal | None

@dataclass(repr=False)
class Preparacao:
    usuarios: tuple
    features: tuple
    log_transformado: np.ndarray
    matriz: np.ndarray
    scaler: RobustScaler

@dataclass(repr=False)
class Resultado:
    preparacao: Preparacao
    avaliacoes: list
    ignorados: list
    rotulos: dict
    comparacao_algoritmos: list

    def agregado(self):
        # IDs individuais e nomes/IDs empresariais nao atravessam esta projecao.
        return dict(aviso='ARTIFICIAL - TESTE DE SOFTWARE, SEM CONCLUSAO CIENTIFICA',
            k_selecionado=None,features=list(self.preparacao.features),sementes=list(SEMENTES),
            usuarios=len(self.preparacao.usuarios),
            meses_observados_histograma=dict(sorted(Counter(u.meses_observados for u in self.preparacao.usuarios).items())),
            avaliacoes=self.avaliacoes,ignorados=self.ignorados,
            comparacao_algoritmos=self.comparacao_algoritmos,
            estabilidade_escopo='ARI: mesmas observacoes, sementes e permutacoes; nao e estabilidade por reamostragem')


def numero(value, *, contador=False):
    if value is None: return None
    if type(value) not in (int,str,Decimal): raise Bloqueio('TIPO_NUMERICO_INVALIDO')
    try: result=Decimal(value)
    except (InvalidOperation,ValueError): raise Bloqueio('NUMERO_INVALIDO') from None
    if not result.is_finite(): raise Bloqueio('NUMERO_NAO_FINITO')
    if contador and (result<0 or result!=result.to_integral_value()):
        raise Bloqueio('CONTADOR_INVALIDO')
    return result


def consolidar(linhas):
    """Recorte de colunas usuario-mes-1.1, recebido SOMENTE em memoria.

    Nenhum mes calendario ausente e criado. Null/parcial/negativo em qualquer mes
    invalida a media correspondente; a organizacao preserva None, nao zero.
    """
    grupos=defaultdict(list); vistos=set()
    for row in linhas:
        try:
            tenant=str(UUID(str(row['tenant_id'])))
            employee=row['legacy_employee_id']
            if type(employee) is not str or not employee.strip(): raise ValueError()
            month=row['mes_utc']
            if type(month) is str: month=date.fromisoformat(month)
            if type(month) is not date or month.day!=1: raise ValueError()
        except (KeyError,ValueError,TypeError,AttributeError):
            raise Bloqueio('CHAVE_MENSAL_INVALIDA') from None
        chave=(tenant,employee)
        if (*chave,month) in vistos: raise Bloqueio('USUARIO_MES_DUPLICADO')
        vistos.add((*chave,month))
        registros=numero(row.get('registros_mes'),contador=True)
        if registros is None or registros<1: raise Bloqueio('MES_SEM_REGISTROS')
        valores=[]
        for metric in METRICAS:
            soma=numero(row.get('soma_'+metric+'_mes'))
            presentes=numero(row.get(metric+'_presentes_mes'),contador=True)
            nulos=numero(row.get(metric+'_nulos_mes'),contador=True)
            negativos=numero(row.get(metric+'_negativos_mes'),contador=True)
            if presentes is None or nulos is None or negativos is None:
                valores.append(None); continue
            if presentes+nulos!=registros or negativos>presentes:
                raise Bloqueio('QUALIDADE_MENSAL_INCONSISTENTE')
            if soma is None or soma<0 or nulos>0 or negativos>0:
                valores.append(None)
            else: valores.append(soma)
        modelos=numero(row.get('modelos_nao_nulos_distintos_mes'),contador=True)
        if modelos is not None and modelos>registros: raise Bloqueio('MODELOS_INCONSISTENTES')
        grupos[chave].append((valores,modelos))
    usuarios=[]
    with localcontext() as context:
        context.prec=50
        for chave,meses in sorted(grupos.items()):
            n=len(meses)
            means=tuple(None if any(m[0][j] is None for m in meses)
                        else sum((m[0][j] for m in meses),Decimal(0))/n for j in range(3))
            modelos=None if any(m[1] is None for m in meses) else sum((m[1] for m in meses),Decimal(0))/n
            usuarios.append(Usuario(chave,n,means,modelos))
    return tuple(usuarios)


def preparar(usuarios, *, sensibilidade=False):
    if type(sensibilidade) is not bool: raise Bloqueio('SENSIBILIDADE_EXPLICITA')
    if not usuarios: raise Bloqueio('ENTRADA_VAZIA')
    valores=[u.medias+((u.media_modelos,) if sensibilidade else ()) for u in usuarios]
    if any(v is None or v<0 for row in valores for v in row):
        raise Bloqueio('MEDIAS_AUSENTES_OU_INVALIDAS')
    try:
        matriz=np.array(valores,dtype=np.float64)
    except (ValueError,OverflowError,TypeError): raise Bloqueio('CONVERSAO_INVALIDA') from None
    if not np.isfinite(matriz).all(): raise Bloqueio('CONVERSAO_NAO_FINITA')
    log=matriz.copy()
    log[:,:3]=np.log1p(log[:,:3])
    # Quarta variavel, apenas opt-in: escala robusta, sem log adicional.
    scaler=RobustScaler(with_centering=True,with_scaling=True,quantile_range=(25.,75.),unit_variance=False)
    scaled=scaler.fit_transform(log)
    if not np.isfinite(scaled).all(): raise Bloqueio('ESCALA_INVALIDA')
    return Preparacao(tuple(usuarios),FEATURES+((SENSIBILIDADE,) if sensibilidade else ()),log,scaled,scaler)


def _modelo(algoritmo,k,seed):
    if algoritmo=='kmeans':
        return KMeans(n_clusters=k,init='k-means++',n_init=20,max_iter=300,tol=1e-4,
                      algorithm='lloyd',random_state=seed)
    return AgglomerativeClustering(n_clusters=k,linkage='ward',metric='euclidean')


def avaliar(preparacao):
    """Funcoes numericas para fixtures confiaveis; nao sao controle de acesso."""
    X=preparacao.matriz; n=len(X); unicos=len(np.unique(X,axis=0))
    avaliacoes=[]; ignorados=[]; rotulos={}; comparacao=[]
    with threadpool_limits(limits=1),warnings.catch_warnings():
        warnings.simplefilter('error',ConvergenceWarning)
        for k in KS:
            if n<=k or unicos<k:
                ignorados.append(dict(k=k,motivo='EXIGE_N_MAIOR_K_E_K_VETORES_DISTINTOS'))
                continue
            for algorithm in ('kmeans','ward'):
                runs=[]; scores=[]
                for seed in SEMENTES:
                    # Permutacao mede tambem sensibilidade da Ward a empates/ordem;
                    # Ward nao possui random_state nem aleatoriedade intrinseca.
                    order=np.random.default_rng(seed).permutation(n)
                    try:
                        permuted=_modelo(algorithm,k,seed).fit_predict(X[order])
                    except ConvergenceWarning:
                        runs=[]; break
                    labels=np.empty(n,dtype=np.int64); labels[order]=permuted
                    if len(np.unique(labels))!=k:
                        runs=[]; break
                    runs.append(labels)
                    scores.append(dict(semente=seed,
                        silhueta=float(silhouette_score(X,labels,metric='euclidean')),
                        davies_bouldin=float(davies_bouldin_score(X,labels)),
                        calinski_harabasz=float(calinski_harabasz_score(X,labels)),
                        tamanhos=sorted(int(c) for c in Counter(labels).values()),
                        usuarios_por_cluster={str(int(label)):int(count) for label,count in sorted(Counter(labels).items())}))
                if not runs:
                    ignorados.append(dict(k=k,algoritmo=algorithm,motivo='PARTICAO_DEGENERADA'))
                    continue
                pairs=[dict(sementes=[SEMENTES[i],SEMENTES[j]],ari=float(adjusted_rand_score(runs[i],runs[j])))
                       for i,j in combinations(range(len(runs)),2)]
                avaliacoes.append(dict(algoritmo=algorithm,k=k,execucoes=scores,
                    estabilidade_ari_pares=pairs,
                    estabilidade_ari_media=float(np.mean([p['ari'] for p in pairs])),
                    estabilidade_ari_minima=min(p['ari'] for p in pairs)))
                rotulos[(algorithm,k)]=runs[0]
            if ('kmeans',k) in rotulos and ('ward',k) in rotulos:
                comparacao.append(dict(k=k,ari_kmeans_ward=float(adjusted_rand_score(rotulos[('kmeans',k)],rotulos[('ward',k)]))))
    return Resultado(preparacao,avaliacoes,ignorados,rotulos,comparacao)


def composicao_empresas(resultado,algoritmo,k):
    """Analise posterior em memoria; nenhuma alteracao da matriz ou do modelo.

    Empresas recebem aliases locais; labels se referem a primeira semente.
    """
    labels=resultado.rotulos[(algoritmo,k)]
    tenants=sorted({u.chave[0] for u in resultado.preparacao.usuarios})
    aliases={tenant:'EMPRESA_'+str(i+1) for i,tenant in enumerate(tenants)}
    counts=Counter((aliases[u.chave[0]],int(label)) for u,label in zip(resultado.preparacao.usuarios,labels,strict=True))
    totals=Counter(aliases[u.chave[0]] for u in resultado.preparacao.usuarios)
    cluster_totals=Counter(int(label) for label in labels)
    return [dict(empresa_alias=t,cluster=c,usuarios=counts[t,c],
                 fracao_da_empresa=counts[t,c]/totals[t],
                 fracao_do_cluster=counts[t,c]/cluster_totals[c])
            for t in sorted(totals) for c in sorted(cluster_totals)]


def executar(linhas, *, finalidade=None, sensibilidade=False):
    # Declaracao do chamador nao atesta proveniencia. Nao existe leitor de dados,
    # conector ou CLI para dados reais nesta entrega.
    if finalidade!='TESTE_SINTETICO': raise Bloqueio('EXECUCAO_REAL_NAO_HABILITADA')
    return avaliar(preparar(consolidar(linhas),sensibilidade=sensibilidade))
