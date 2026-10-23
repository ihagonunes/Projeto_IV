const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { options, initEnv } = require('../../../scripts/setup-local');
fs.mkdirSync(path.resolve('target'), { recursive: true });
const directory = fs.mkdtempSync(path.resolve('target/setup-options-'));
const file = path.join(directory, '.env.local');
function environment(change = {}) {
    fs.writeFileSync(file, Object.entries({ DB_HOST: 'localhost', DB_PORT: '5432', DB_NAME: 'team_test', DB_USER: 'mock',
        DB_PASSWORD: 'fixture_only', NODE_ENV: 'development', SETUP_MOCK_PASSWORD: 'fixture_only', JWT_SECRET: 'a'.repeat(48), ...change })
        .map(([key, value]) => key + '=' + value).join('\n'));
}
test('local setup requires explicit development configuration and exact target confirmation', () => {
    environment();
    const valid = options(['--env', file, '--confirm-target', 'team_test']);
    assert.equal(valid.target.database, 'team_test');
    assert.equal(valid.workDir, path.resolve('tmp/setup-team_test'));
    assert.throws(() => options(['--env', file]));
    assert.throws(() => options(['--env', file, '--confirm-target', 'other']));
    assert.throws(() => options(['--env', file, '--confirm-target', 'team_test', '--skip-build', 'false']));
    for (const change of [{ DB_HOST: 'production.example.test' }, { NODE_ENV: 'production' }, { JWT_SECRET: 'short' },
        { SETUP_MOCK_PASSWORD: '' }, { SETUP_MOCK_PASSWORD: 'x'.repeat(73) }, { SERVER_PORT: 'invalid' }, { DB_NAME: 'bad;sql' }]) {
        environment(change); assert.throws(() => options(['--env', file, '--confirm-target', 'team_test']));
    }
});
test('private reports and snapshot inputs cannot be confused with the target', () => {
    environment();
    const base = ['--env', file, '--confirm-target', 'team_test'];
    assert.throws(() => options([...base, '--work-dir', 'docs/setup']));
    assert.throws(() => options([...base, '--work-dir', '.env.work']));
    assert.throws(() => options([...base, '--source-env', 'config.env']));
    assert.throws(() => options([...base, '--source-db', 'snapshot']));
    assert.throws(() => options([...base, '--source-env', file]));
    assert.throws(() => options([...base, '--env', file]));
    assert.throws(() => options(['--init-env', '.env.example']));
});
test('configuration generation keeps existing files and uses a different JWT secret for each developer', () => {
    const a = path.join(directory, 'a.env'), b = path.join(directory, 'b.env');
    initEnv(a); initEnv(b);
    const first = fs.readFileSync(a, 'utf8'), second = fs.readFileSync(b, 'utf8');
    assert.notEqual(first.match(/^JWT_SECRET=(.+)$/m)[1], second.match(/^JWT_SECRET=(.+)$/m)[1]);
    assert.match(first, /NODE_ENV=development/);
    assert.match(first, /DB_NAME=qsoft_team_dev/);
    assert.throws(() => initEnv(a));
    assert.equal(fs.readFileSync(a, 'utf8'), first);
});
