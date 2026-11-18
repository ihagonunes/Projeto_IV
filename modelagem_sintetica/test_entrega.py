import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from entrega_sintetica import resultados, salvar


class TestEntrega(unittest.TestCase):
    def test_agregados_e_graficos(self):
        data = resultados()
        self.assertEqual(data['linhas_usuario_mes'], 60)
        self.assertEqual(data['principal']['usuarios'], 24)
        self.assertEqual(data['rede_chamadas'], 0)
        self.assertEqual(len(data['perfis']), 70)
        for alg in ('kmeans', 'ward'):
            for k in range(2, 9):
                profiles = [p for p in data['perfis'] if p['algoritmo']==alg and p['k']==k]
                self.assertEqual(sum(p['usuarios'] for p in profiles), 24)
                self.assertEqual(len(profiles), k)
        serialized = json.dumps(data)
        for token in ('tenant_id', 'legacy_employee_id', '00000000-0000', 'ARTIFICIAL_0'):
            self.assertNotIn(token, serialized)
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / 'resultados'
            salvar(folder, data)
            self.assertEqual(json.loads((folder/'metricas_perfis_sinteticos.json').read_text(encoding='utf-8')), json.loads(serialized))
            for svg in folder.glob('*.svg'):
                self.assertEqual(ET.parse(svg).getroot().tag, '{http://www.w3.org/2000/svg}svg')
            self.assertEqual(len(list(folder.glob('*.svg'))), 5)
            with self.assertRaises(FileExistsError):
                salvar(folder, data)
