"""Entrega da fixture incorporada: somente agregados e SVGs, sem dados externos."""
import argparse
import json
from pathlib import Path
import socket
from unittest.mock import patch
from contextlib import ExitStack
import numpy as np
from fixtures import dados_artificiais
from pipeline import executar


def resultados():
    with ExitStack() as stack:
        guards = [stack.enter_context(patch.object(socket, name,
                  side_effect=AssertionError('REDE_PROIBIDA')))
                  for name in ('socket', 'create_connection', 'getaddrinfo')]
        rows = dados_artificiais()
        result = executar(rows, finalidade='TESTE_SINTETICO')
        profiles = []
        raw = np.array([[float(v) for v in u.medias]
                        for u in result.preparacao.usuarios])
        for (algorithm, k), labels in sorted(result.rotulos.items()):
            for label in sorted(set(labels)):
                values = raw[labels == label]
                profiles.append(dict(algoritmo=algorithm, k=k, semente=17,
                    cluster=int(label), usuarios=len(values),
                    medias=dict(zip(result.preparacao.features,
                                    values.mean(axis=0).tolist())),
                    medianas=dict(zip(result.preparacao.features,
                                      np.median(values, axis=0).tolist()))))
        for guard in guards:
            guard.assert_not_called()
    return dict(aviso='SOMENTE DADOS SINTETICOS; NAO VALIDA DADOS REAIS',
                linhas_usuario_mes=len(rows), rede_chamadas=0,
                principal=result.agregado(), perfis=profiles)


def grafico(series, title):
    """SVG agregado, sem dependencias graficas adicionais."""
    colors = ('#2563eb', '#dc2626')
    values = [v for points in series.values() for _, v in points]
    low, high = min(values), max(values)
    span = high - low or 1
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="760" height="420" viewBox="0 0 760 420">',
           '<rect width="760" height="420" fill="white"/>',
           f'<text x="35" y="28" font-size="18">SINTETICO — {title}</text>',
           '<path d="M70 60 V340 H710" fill="none" stroke="#444"/>']
    for index in range(5):
        value = low + span * index / 4
        y = 340 - index * 65
        svg.append(f'<text x="5" y="{y}" font-size="11">{value:.3g}</text>')
    for index, (name, points) in enumerate(series.items()):
        coords = ' '.join(f'{90+(k-2)*98},{340-(v-low)/span*260}' for k, v in points)
        svg.append(f'<polyline points="{coords}" fill="none" stroke="{colors[index]}" stroke-width="2"/>')
        svg.append(f'<text x="{100+index*260}" y="400" fill="{colors[index]}">{name}</text>')
    for k in range(2, 9):
        svg.append(f'<text x="{90+(k-2)*98}" y="365">{k}</text>')
    svg.append('<text x="690" y="385">k</text></svg>')
    return '\n'.join(svg)


def salvar(folder, data):
    # Pasta nova evita sobrescrever resultados ou arquivos existentes.
    folder.mkdir(parents=True, exist_ok=False)
    (folder / 'metricas_perfis_sinteticos.json').write_text(
        json.dumps(data, ensure_ascii=False, allow_nan=False, indent=2)+'\n', encoding='utf-8')
    for metric in ('silhueta', 'davies_bouldin', 'calinski_harabasz'):
        series = {alg: [(a['k'], float(np.mean([r[metric] for r in a['execucoes']])))
                       for a in data['principal']['avaliacoes'] if a['algoritmo']==alg]
                  for alg in ('kmeans', 'ward')}
        (folder / (metric+'.svg')).write_text(grafico(series, metric+' (media das sementes)'), encoding='utf-8')
    for alg in ('kmeans', 'ward'):
        points = [(a['k'], a['estabilidade_ari_media'])
                  for a in data['principal']['avaliacoes'] if a['algoritmo']==alg]
        # Estabilidade e comparacao no mesmo formato, sem escolher k.
        series = {alg: points}
        (folder / ('estabilidade_'+alg+'.svg')).write_text(grafico(series, 'ARI '+alg), encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--saida', type=Path, default=Path('resultados_sinteticos'))
    args = parser.parse_args()
    data = resultados()
    salvar(args.saida, data)
    print('SINTETICO: 24 usuarios; 3 features; k=2..8; resultados em '+str(args.saida))
