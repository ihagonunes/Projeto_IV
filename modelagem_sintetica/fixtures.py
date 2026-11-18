"""Fixture artificial deterministica. Nao representa populacao ou resultados cientificos."""
from pipeline import METRICAS

def mensal(tenant,usuario,mes,totais=(10,100,30),modelos=1):
    row=dict(tenant_id=tenant,legacy_employee_id=usuario,mes_utc=mes,registros_mes=10,
             modelos_nao_nulos_distintos_mes=modelos)
    for metric,value in zip(METRICAS,totais,strict=True):
        row['soma_'+metric+'_mes']=value
        row[metric+'_presentes_mes']=10
        row[metric+'_nulos_mes']=0
        row[metric+'_negativos_mes']=0
    return row

def dados_artificiais(n=24):
    rows=[]
    for i in range(n):
        tenant='00000000-0000-4000-8000-'+str(1+i%3).zfill(12)
        # Algumas IDs de usuario iguais entre tenants, propositalmente.
        user='ARTIFICIAL_'+str(i//3)
        for j in range(1+i%4):
            level=(i%4+1)**3
            rows.append(mensal(tenant,user,f'2025-{1+2*j:02d}-01',
                (level*10+i+j,level*90+3*i+2*j,level*25+7*i+j),1+i%5))
    return rows
