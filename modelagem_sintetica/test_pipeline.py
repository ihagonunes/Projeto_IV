"""Somente dados artificiais. Sentinelas impedem rede e leitura de arquivos de dados."""
from copy import deepcopy
from decimal import Decimal
from hashlib import sha256
import json
from pathlib import Path
import socket
import sys
import unittest
from unittest.mock import patch
import numpy as np
from sklearn.metrics import adjusted_rand_score
import pipeline as p
from fixtures import dados_artificiais, mensal

A='00000000-0000-4000-8000-000000000001'
B='00000000-0000-4000-8000-000000000002'
PATCHES=[]; GUARDS=[]

def setUpModule():
    for target in ('socket','create_connection','getaddrinfo'):
        q=patch.object(socket,target,side_effect=AssertionError('REDE_PROIBIDA'))
        PATCHES.append(q); GUARDS.append(q.start())

def tearDownModule():
    try:
        for guard in GUARDS: guard.assert_not_called()
    finally:
        for q in reversed(PATCHES): q.stop()

class TestPreparacao(unittest.TestCase):
    def one(self,**kw):
        row=mensal(A,'ARTIFICIAL','2025-01-01'); row.update(kw); return row
    def test_media_por_meses_observados_sem_preencher_lacuna(self):
        u=p.consolidar([mensal(A,'X','2025-01-01',(10,100,30)),
                       mensal(A,'X','2025-05-01',(30,200,50))])[0]
        self.assertEqual(u.meses_observados,2)
        self.assertEqual(u.medias,(Decimal(20),Decimal(150),Decimal(40)))
    def test_mes_unico_nao_excluido(self):
        us=p.consolidar([self.one(),mensal(B,'OUTRO','2025-01-01'),mensal(B,'OUTRO','2025-03-01')])
        self.assertEqual([u.meses_observados for u in us],[1,2])
    def test_chave_composta_mesmo_usuario_empresas_distintas(self):
        us=p.consolidar([self.one(),mensal(B,'ARTIFICIAL','2025-01-01')])
        self.assertEqual(len(us),2)
        self.assertNotEqual(us[0].chave,us[1].chave)
    def test_duplicata_bloqueada(self):
        with self.assertRaisesRegex(p.Bloqueio,'USUARIO_MES_DUPLICADO'): p.consolidar([self.one(),self.one()])
    def test_data_e_identificacao_invalidas(self):
        for kw in (dict(mes_utc='2025-01-02'),dict(tenant_id='INVALIDO'),dict(legacy_employee_id='')):
            with self.assertRaises(p.Bloqueio): p.consolidar([self.one(**kw)])
    def test_nulo_preservado_e_bloqueia_treino(self):
        us=p.consolidar([self.one(soma_request_count_mes=None)])
        self.assertIsNone(us[0].medias[0])
        with self.assertRaisesRegex(p.Bloqueio,'MEDIAS_AUSENTES'): p.preparar(us)
    def test_nulo_parcial_nao_vira_media_parcial(self):
        row=self.one(request_count_presentes_mes=9,request_count_nulos_mes=1)
        us=p.consolidar([row,mensal(A,'ARTIFICIAL','2025-03-01')])
        self.assertIsNone(us[0].medias[0])
        with self.assertRaises(p.Bloqueio): p.preparar(us)
    def test_soma_negativa_bloqueia(self):
        us=p.consolidar([self.one(soma_input_tokens_mes=-1)])
        with self.assertRaises(p.Bloqueio): p.preparar(us)
    def test_negativo_mascarado_por_soma_positiva(self):
        us=p.consolidar([self.one(input_tokens_negativos_mes=1)])
        self.assertIsNone(us[0].medias[1])
    def test_campo_ausente_nao_zero(self):
        row=self.one(); del row['soma_output_tokens_mes']
        self.assertIsNone(p.consolidar([row])[0].medias[2])
    def test_qualidade_ausente_bloqueia(self):
        row=self.one(); del row['output_tokens_nulos_mes']
        with self.assertRaises(p.Bloqueio): p.preparar(p.consolidar([row]))
    def test_contadores_inconsistentes(self):
        for kw in (dict(request_count_presentes_mes=9),dict(registros_mes=0),dict(input_tokens_negativos_mes=11)):
            with self.assertRaises(p.Bloqueio): p.consolidar([self.one(**kw)])
    def test_zero_valido_nao_e_nulo(self):
        us=p.consolidar([mensal(A,'X','2025-01-01',(0,0,0))])
        np.testing.assert_array_equal(p.preparar(us).matriz,[[0,0,0]])
    def test_nao_finitos_bool_float_rejeitados(self):
        for value in ('NaN','Infinity',True,1.5):
            with self.assertRaises(p.Bloqueio): p.consolidar([self.one(soma_request_count_mes=value)])
    def test_log_e_escala_robusta_com_calculo_independente(self):
        rows=[mensal(A,str(i),'2025-01-01',(x,x*2,x*3)) for i,x in enumerate((0,3,8,15,99))]
        prep=p.preparar(p.consolidar(rows))
        expected=np.log1p(np.array([[x,x*2,x*3] for x in (0,3,8,15,99)],dtype=float))
        q25,q75=np.percentile(expected,[25,75],axis=0)
        np.testing.assert_allclose(prep.log_transformado,expected)
        np.testing.assert_allclose(prep.matriz,(expected-np.median(expected,axis=0))/(q75-q25))
    def test_sensibilidade_apenas_optin(self):
        us=p.consolidar(dados_artificiais())
        main=p.preparar(us); sens=p.preparar(us,sensibilidade=True)
        self.assertEqual(main.matriz.shape[1],3)
        self.assertEqual(sens.matriz.shape[1],4)
        np.testing.assert_allclose(main.matriz,sens.matriz[:,:3])
        self.assertEqual(sens.features[-1],p.SENSIBILIDADE)
    def test_media_modelos_dividida_por_meses(self):
        us=p.consolidar([mensal(A,'X','2025-01-01',modelos=1),mensal(A,'X','2025-03-01',modelos=3)])
        self.assertEqual(us[0].media_modelos,Decimal(2))
    def test_modelos_ausentes_nao_impedem_principal(self):
        row=self.one(); del row['modelos_nao_nulos_distintos_mes']
        us=p.consolidar([row]); p.preparar(us)
        with self.assertRaises(p.Bloqueio): p.preparar(us,sensibilidade=True)
    def test_entrada_vazia(self):
        with self.assertRaises(p.Bloqueio): p.preparar(p.consolidar([]))
    def test_bloqueio_finalidade_real(self):
        for flag in (None,'REAL','AUTORIZADO'):
            with self.assertRaisesRegex(p.Bloqueio,'EXECUCAO_REAL_NAO_HABILITADA'):
                p.executar(dados_artificiais(),finalidade=flag)
    def test_erros_nao_vazam_identificadores(self):
        with self.assertRaises(p.Bloqueio) as err:
            p.consolidar([self.one(tenant_id='SEGREDO_ARTIFICIAL')])
        self.assertNotIn('SEGREDO_ARTIFICIAL',str(err.exception))
    def test_entrada_nao_modificada(self):
        rows=dados_artificiais(); before=deepcopy(rows); p.consolidar(rows)
        self.assertEqual(before,rows)

class TestModelagem(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.result=p.executar(dados_artificiais(),finalidade='TESTE_SINTETICO')
    def test_kmeans_ward_k2_a_k8(self):
        self.assertEqual({(a['algoritmo'],a['k']) for a in self.result.avaliacoes},
            {(alg,k) for alg in ('kmeans','ward') for k in range(2,9)})
    def test_metricas_finitas_e_tamanhos(self):
        for evaluation in self.result.avaliacoes:
            for run in evaluation['execucoes']:
                self.assertTrue(all(np.isfinite(run[m]) for m in ('silhueta','davies_bouldin','calinski_harabasz')))
                self.assertTrue(-1<=run['silhueta']<=1)
                self.assertGreaterEqual(run['davies_bouldin'],0)
                self.assertGreaterEqual(run['calinski_harabasz'],0)
                self.assertEqual(sum(run['tamanhos']),24)
                self.assertEqual(sum(run['usuarios_por_cluster'].values()),24)
                self.assertEqual(len(run['tamanhos']),evaluation['k'])
    def test_estabilidade_ari_todas_execucoes(self):
        for evaluation in self.result.avaliacoes:
            self.assertEqual(len(evaluation['estabilidade_ari_pares']),3)
            for pair in evaluation['estabilidade_ari_pares']: self.assertTrue(-1<=pair['ari']<=1)
        self.assertEqual(len(self.result.comparacao_algoritmos),7)
    def test_reprodutibilidade_identica(self):
        other=p.executar(dados_artificiais(),finalidade='TESTE_SINTETICO')
        self.assertEqual(self.result.agregado(),other.agregado())
        for key in self.result.rotulos:
            self.assertEqual(adjusted_rand_score(self.result.rotulos[key],other.rotulos[key]),1)
    def test_ordem_entrada_reprodutivel(self):
        other=p.executar(list(reversed(dados_artificiais())),finalidade='TESTE_SINTETICO')
        self.assertEqual(self.result.agregado(),other.agregado())
    def test_empresa_e_usuario_nao_entram_nas_features(self):
        rows=dados_artificiais()
        for i,row in enumerate(rows):
            row['tenant_id']='00000000-0000-4000-8000-000000000099'
            row['legacy_employee_id']='RENOMEADO_'+str(i)
        # Mantem um ID por unidade original (nao por mes).
        mapping={}; index=0
        for row,old in zip(rows,dados_artificiais(),strict=True):
            key=(old['tenant_id'],old['legacy_employee_id'])
            if key not in mapping: mapping[key]='RENOMEADO_'+str(index).zfill(3); index+=1
            row['legacy_employee_id']=mapping[key]
        before=sorted(map(tuple,self.result.preparacao.matriz))
        after=sorted(map(tuple,p.preparar(p.consolidar(rows)).matriz))
        self.assertEqual(before,after)
        self.assertEqual(self.result.preparacao.features,p.FEATURES)
    def test_composicao_posterior_por_empresa(self):
        before=self.result.preparacao.matriz.copy()
        table=p.composicao_empresas(self.result,'kmeans',2)
        self.assertEqual(sum(row['usuarios'] for row in table),24)
        for alias in ('EMPRESA_1','EMPRESA_2','EMPRESA_3'):
            self.assertAlmostEqual(sum(row['fracao_da_empresa'] for row in table if row['empresa_alias']==alias),1)
        np.testing.assert_array_equal(before,self.result.preparacao.matriz)
    def test_poucos_usuarios_avalia_apenas_k_possivel(self):
        result=p.executar(dados_artificiais(3),finalidade='TESTE_SINTETICO')
        self.assertEqual({r['k'] for r in result.avaliacoes},{2})
        self.assertEqual({r['k'] for r in result.ignorados},set(range(3,9)))
    def test_duplicacao_vetorial_sem_clusters_naturais_assumidos(self):
        rows=[mensal(A,str(i),'2025-01-01') for i in range(4)]
        result=p.executar(rows,finalidade='TESTE_SINTETICO')
        self.assertEqual(result.avaliacoes,[])
        self.assertEqual(result.agregado()['k_selecionado'],None)
    def test_execucao_sensibilidade(self):
        result=p.executar(dados_artificiais(),finalidade='TESTE_SINTETICO',sensibilidade=True)
        self.assertEqual(len(result.avaliacoes),14)
        self.assertEqual(len(result.preparacao.features),4)
    def test_relatorio_sem_identificadores_ou_artefatos(self):
        out=json.dumps(self.result.agregado(),allow_nan=False)
        self.assertIn('ARTIFICIAL',out)
        for token in ('tenant_id','legacy_employee_id','00000000-0000','ARTIFICIAL_0'):
            self.assertNotIn(token,out)
        self.assertNotIn('psycopg',sys.modules)
    def test_sem_leitura_dados_ou_persistencia(self):
        folder=Path(__file__).parent
        files={f.name:sha256(f.read_bytes()).hexdigest() for f in folder.iterdir() if f.is_file()}
        with patch('builtins.open',side_effect=AssertionError('ARQUIVO_PROIBIDO')):
            p.executar(dados_artificiais(5),finalidade='TESTE_SINTETICO')
        self.assertEqual(files,{f.name:sha256(f.read_bytes()).hexdigest() for f in folder.iterdir() if f.is_file()})

if __name__=='__main__': unittest.main(verbosity=2)
