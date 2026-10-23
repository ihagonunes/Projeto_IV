const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

test('branding loads the real tenant and saves logo URL preserving colors; errors never claim success', async () => {
    const nodes = new Map();
    function node(id) {
        if (!nodes.has(id)) nodes.set(id, { value: '', disabled: false, events: {},
            classList: { add() {}, remove() {}, toggle() {} },
            addEventListener(event, fn) { this.events[event] = fn; },
            setAttribute() {}, removeAttribute() {}, checkValidity: () => true,
            replaceChildren(value) { this.message = value.textContent; }
        });
        return nodes.get(id);
    }
    let init, payload, fail = false;
    const calls = [];
    const context = vm.createContext({ document: { getElementById: node, createElement: () => node('alert'),
        addEventListener(event, fn) { init = fn; } }, API: {
        async get(uri) { calls.push(uri); return uri.endsWith('/me') ? { name: 'Empresa real' }
            : { logoUrl: 'https://example.test/old.png', primaryColor: '#123456', secondaryColor: '#654321' }; },
        async put(uri, data) { assert.equal(uri, '/tenants/me/identidade-visual'); payload = data;
            if (fail) throw Error('offline'); return data; }
    }});
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/public/js/pages/identidade-visual.js'), 'utf8'), context);
    await init();
    assert.deepEqual(calls, ['/tenants/me', '/tenants/me/identidade-visual']);
    assert.equal(node('nomeExibicao').value, 'Empresa real');
    node('logoUrl').value = 'https://example.test/new.png';
    await node('identidadeForm').events.submit({ preventDefault() {} });
    assert.equal(payload.logoUrl, 'https://example.test/new.png');
    assert.equal(payload.primaryColor, '#123456');
    assert.equal(payload.secondaryColor, '#654321');
    assert.match(node('alertArea').message, /salva com sucesso/);
    fail = true;
    await node('identidadeForm').events.submit({ preventDefault() {} });
    assert.match(node('alertArea').message, /Não foi possível salvar/);
    assert.equal(node('btnSalvarIdentidade').disabled, false);
});
