const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');
const layouts = require('express-ejs-layouts');
const cookieParser = require('cookie-parser');
const path = require('node:path');
const { createBrowserSession } = require('../../main/resources/static/modules/auth/browser-session');

test('onboarding renders only the buttons for the authenticated role', async () => {
    const app = express();
    app.set('views', [path.resolve(__dirname, '../../main/resources/static/views'),
        path.resolve(__dirname, '../../main/resources/static/modules')]);
    app.set('view engine', 'ejs');
    app.use(layouts);
    app.use(cookieParser());
    const session = createBrowserSession({ async get(uri, config) {
        const papel = config.headers.Authorization.slice('Bearer '.length);
        if (papel === 'invalid') throw { response: { status: 401 } };
        return { data: { concessoes: [{ papel }] } };
    }});
    app.get('/onboarding', session.requireSession);
    app.use(require('../../main/resources/static/routes/auth.routes'));
    const server = app.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    const url = 'http://127.0.0.1:' + server.address().port;
    try {
        for (const [role, count] of [['COLLABORATOR', 2], ['LEADER', 2], ['MANAGER', 3], ['EXECUTIVE', 3], ['TENANT_ADMIN', 3]]) {
            const response = await fetch(url + '/onboarding?profile=executivo', { headers: { cookie: 'qsoft_access=' + role } });
            assert.equal(response.status, 200);
            const html = await response.text();
            assert.equal(['/dashboard', '/login', '/convites', '/tenant'].filter(href => html.includes('href="' + href + '"')).length, count);
            assert.equal(html.includes('href="/dashboard"'), role !== 'TENANT_ADMIN');
            assert.ok(html.includes('href="/login"'));
            assert.equal(html.includes('href="/convites"'), count >= 3);
            assert.equal(html.includes('href="/tenant"'), role === 'TENANT_ADMIN');
            assert.equal(html.includes('href="/tenant/identidade-visual"'), role === 'TENANT_ADMIN');
        }
        for (const cookie of ['', 'qsoft_access=invalid']) {
            const response = await fetch(url + '/onboarding', { headers: { cookie }, redirect: 'manual' });
            assert.equal(response.status, 302);
            assert.equal(response.headers.get('location'), '/login');
        }
    } finally { await new Promise(resolve => server.close(resolve)); }
});
