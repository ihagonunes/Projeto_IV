const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), net = require('node:net');
const { spawn } = require('node:child_process');
const loader = require('../../../scripts/db/historical-import');
const { tables } = require('../../../scripts/db/historical-contract');
const literal = value => "'" + String(value).replace(/'/g, "''") + "'";
function command(exe, args, env = {}) {
    return new Promise((resolve, reject) => {
        const process = spawn(exe, args, { env: { ...global.process.env, ...env }, windowsHide: true });
        let output = ''; process.stdout.on('data', d => output += d); process.stderr.on('data', d => output += d);
        process.on('error', reject); process.on('close', code => resolve({ code, output }));
    });
}
async function port() {
    const server = net.createServer(); server.listen(0, '127.0.0.1');
    await new Promise(resolve => server.once('listening', resolve));
    const value = server.address().port; await new Promise(resolve => server.close(resolve)); return value;
}
test('local setup boots real Flyway/API, seeds roles, imports history and safely resumes', { skip: !process.env.QSOFT_LOADER_TEST_CONFIG }, async () => {
    const settings = JSON.parse(process.env.QSOFT_LOADER_TEST_CONFIG), config = {};
    const source = { ...settings, database: 'setup_original' };
    await loader.psql(config, { ...source, database: 'postgres' }, 'CREATE DATABASE setup_original;', { write: true });
    const ddl = ['CREATE SCHEMA staging;', ...Object.entries(tables).map(([table, columns]) =>
        `CREATE TABLE staging.${table} (${Object.entries(columns).map(([name, type]) => `"${name}" ${name === 'active' ? 'text' : type}`).join(',')});`)];
    ddl.push(`INSERT INTO staging.companies(company_id,company_name) VALUES ('C1','Fixture');
        INSERT INTO staging.departments(company_id,department_id,department_name) VALUES ('C1','D1','TI'),('C1','D2','Financeiro');
        INSERT INTO staging.ai_tools(ai_tool_id) VALUES ('T1');
        INSERT INTO staging.ai_models(model_id,ai_tool_id) VALUES ('M1','T1');
        INSERT INTO staging.projects(project_id,company_id) VALUES ('P1','C1');
        CREATE TABLE public.department_departments(id uuid,tenant_id uuid,name text,status text,legacy_department_id text);
        CREATE TABLE public.iam_users(id uuid,tenant_id uuid,department_id uuid,email text,password_hash text,hierarchy_level text,is_admin boolean,status text,legacy_employee_id text);
        INSERT INTO public.department_departments VALUES
          ('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000003','TI','ACTIVE','D1'),
          ('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000003','Financeiro','ACTIVE','D2');`);
    for (const [index, role] of ['COLLABORATOR', 'LEADER', 'MANAGER', 'COLLABORATOR'].entries()) {
        const i = index + 1, dept = i === 3 ? 'D2' : 'D1', active = i === 4 ? 'False' : 'True';
        ddl.push(`INSERT INTO staging.employees(employee_id,company_id,department_id,active) VALUES ('E${i}','C1','${dept}','${active}');
            INSERT INTO public.iam_users VALUES ('00000000-0000-4000-8000-00000000001${i}',
              '00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-00000000000${i === 3 ? 2 : 1}',
              'person${i}@fixture.test','truncated','${role}',false,'${i === 4 ? 'INACTIVE' : 'ACTIVE'}','E${i}');`);
        if (i < 4) ddl.push(`INSERT INTO staging.ai_usage_logs(usage_id,company_id,employee_id,department_id,ai_tool_id,model_id,project_id,timestamp,request_count)
            VALUES ('U${i}','C1','E${i}','${dept}','T1','M1','P1','2026-07-15 12:00:00',${i});`);
    }
    await loader.psql(config, source, ddl.join('\n'), { write: true });
    const before = await loader.json(config, source, 'SELECT to_json(md5(json_agg(u ORDER BY id)::text)) FROM public.iam_users u');
    const password = 'local_fixture_123';
    async function environment(name, database) {
        const file = path.join(settings.workDir, name + '.env');
        fs.writeFileSync(file, Object.entries({ DB_HOST: settings.host, DB_PORT: settings.port, DB_USER: settings.user,
            DB_PASSWORD: settings.password, DB_NAME: database, NODE_ENV: 'development', SETUP_MOCK_PASSWORD: password,
            JWT_SECRET: 'local-test-secret-with-more-than-thirty-two-characters', SERVER_PORT: await port(), OPENAPI_ENABLED: 'false' })
            .map(([key, value]) => key + '=' + value).join('\n'));
        return file;
    }
    const emptyEnv = await environment('empty', 'setup_empty'), emptyWork = path.join(settings.workDir, 'empty');
    const args = (env, database, work) => ['scripts/setup-local.js', '--env', env, '--confirm-target', database, '--work-dir', work, '--skip-build', 'true'];
    const emptyArgs = args(emptyEnv, 'setup_empty', emptyWork);
    let result = await command(process.execPath, emptyArgs); assert.equal(result.code, 0, result.output);
    assert.ok(!result.output.includes(password));
    const empty = { ...source, database: 'setup_empty' };
    assert.equal(await loader.json(config, empty, 'SELECT count(*) FROM app_user'), 5);
    assert.equal(await loader.json(config, empty, 'SELECT max(version::int) FROM flyway_schema_history WHERE success'), 21);
    result = await command(process.execPath, emptyArgs); assert.equal(result.code, 0, result.output);
    assert.equal(await loader.json(config, empty, 'SELECT count(*) FROM app_user'), 5);
    result = await command(process.execPath, args(emptyEnv, 'setup_empty', path.join(settings.workDir, 'foreign')));
    assert.notEqual(result.code, 0); assert.match(result.output, /já contém tabelas/);
    await loader.psql(config, empty, `UPDATE iam_role_assignments a SET revoked_at=now(),revoked_by=a.user_id
        FROM iam_roles r WHERE r.id=a.role_id AND r.name='EXECUTIVE';`, { write: true });
    result = await command(process.execPath, emptyArgs); assert.notEqual(result.code, 0);
    assert.match(result.output, /concessão revogada/);
    assert.equal(await loader.json(config, empty, "SELECT count(*) FROM iam_role_assignments a JOIN iam_roles r ON r.id=a.role_id WHERE r.name='EXECUTIVE' AND a.revoked_at IS NULL"), 0);
    const targetEnv = await environment('history', 'setup_history'), work = path.join(settings.workDir, 'history');
    let sourceDb = source.database, dump;
    if (settings.useDump) {
        dump = path.join(settings.workDir, 'source.dump');
        result = await command('pg_dump', ['-Fc', '--file', dump], { PGHOST: source.host, PGPORT: String(source.port), PGUSER: source.user, PGPASSWORD: source.password, PGDATABASE: source.database });
        assert.equal(result.code, 0, 'pg_dump deve ser compatível com o servidor PostgreSQL do teste');
        sourceDb = 'setup_snapshot';
    }
    const sourceEnv = await environment('source', sourceDb);
    const historyArgs = [...args(targetEnv, 'setup_history', work), '--source-env', sourceEnv, ...(dump ? ['--source-dump', dump] : [])];
    result = await command(process.execPath, historyArgs); assert.equal(result.code, 0, result.output);
    const target = { ...source, database: 'setup_history' };
    assert.equal(await loader.json(config, target, 'SELECT sum(request_count) FROM historical.ai_usage_logs'), 6);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM iam_legacy_user_links'), 4);
    assert.equal(await loader.json(config, target, "SELECT count(*) FROM app_user WHERE status='INACTIVE'"), 1);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM app_user'), 7);
    const accounts = JSON.parse(fs.readFileSync(path.join(work, 'accounts.local.json')));
    assert.equal(accounts.find(a => a.role === 'COLLABORATOR').email, 'person1@fixture.test');
    assert.equal(accounts.find(a => a.role === 'MANAGER').email, 'person3@fixture.test');
    result = await command(process.execPath, historyArgs); assert.equal(result.code, 0, result.output);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM app_user'), 7);
    assert.equal(await loader.json(config, target, "SELECT count(*) FROM iam_role_permissions p JOIN iam_roles r ON r.id=p.role_id WHERE r.name='TENANT_ADMIN' AND p.resource='USAGE'"), 0);
    assert.equal(await loader.json(config, source, 'SELECT to_json(md5(json_agg(u ORDER BY id)::text)) FROM public.iam_users u'), before);
});
