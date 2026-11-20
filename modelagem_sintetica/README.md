# Clustering exclusivamente sintetico

Copia isolada do pipeline existente, sem alteracao de `pipeline.py`, `fixtures.py`,
`test_pipeline.py` ou `demo_sintetica.py`. Nao integra o backend Java nem acessa banco.
Nao fornecer dados reais. A CLI usa somente a fixture deterministica incorporada.

No PowerShell, a partir desta pasta, com Python 3.14:

```powershell
py -3.14 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.lock.txt
.\.venv\Scripts\python.exe -B -m unittest discover -s . -v
.\.venv\Scripts\python.exe -B entrega_sintetica.py --saida minha_execucao_sintetica
```

A saida deve ser uma pasta nova; arquivos existentes nao sao sobrescritos.
`resultados_sinteticos/` contem uma execucao efetiva para comparacao. JSON contem
metricas por algoritmo/k/semente, distribuicao, ARI entre sementes e algoritmos,
e medias/medianas por cluster na escala original. SVGs mostram indices e estabilidade.
Nenhum rotulo ou identificador individual e exportado. Sentinelas bloqueiam rede.

Fluxo: 60 linhas usuario-mes artificiais → 24 usuarios → tres medias mensais
(requisicoes, tokens de entrada e saida) → log1p → RobustScaler → K-Means/Ward.
k=2..8; sementes 17/29/43; K-Means n_init=20; uma thread numerica.
Perfis usam a primeira semente (17); rotulos sao locais a cada algoritmo/k.
Graficos de indices usam a media das tres sementes. Nao ha selecao automatica de k.
Estabilidade ARI mede inicializacao/permutacao, nao reamostragem; Ward nao tem random_state.

Dependencias sao as mesmas do ambiente original, fixadas no lock; nenhum pacote
grafico foi adicionado. SVG e gerado pela biblioteca padrao. Para executar a demo
anterior com sensibilidade de quatro features: `python -B demo_sintetica.py`.

Resultados sao demonstracao de software com dados sinteticos, sem conclusao sobre
usuarios reais. PostgreSQL e validacao usuario-mes continuam fora deste comando.
SQLs homologados, comparador e contrato nao fazem parte desta copia nem sao alterados.
