"""Unica CLI: fixture incorporada, sem caminhos/credenciais/entrada externa."""
import json
from sklearn.metrics import adjusted_rand_score
from fixtures import dados_artificiais
from pipeline import executar, composicao_empresas

def exemplo():
    principal=executar(dados_artificiais(),finalidade='TESTE_SINTETICO')
    sens=executar(dados_artificiais(),finalidade='TESTE_SINTETICO',sensibilidade=True)
    return dict(aviso='EXEMPLO ARTIFICIAL; NAO E RESULTADO CIENTIFICO',
        principal=principal.agregado(),sensibilidade=sens.agregado(),
        composicao_artificial_k2=composicao_empresas(principal,'kmeans',2),
        comparacao_principal_sensibilidade=[dict(algoritmo=alg,k=k,
            ari=float(adjusted_rand_score(principal.rotulos[(alg,k)],sens.rotulos[(alg,k)])))
            for alg,k in principal.rotulos if (alg,k) in sens.rotulos])

if __name__=='__main__': print(json.dumps(exemplo(),ensure_ascii=True,allow_nan=False,indent=2))
