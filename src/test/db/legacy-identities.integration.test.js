const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const loader = require('../../../scripts/db/historical-import');

test('identity CLI imports atomically, preserves status and refuses revoked grants', { skip: !process.env.QSOFT_LOADER_TEST_CONFIG }, async () => {
    const settings = JSON.parse(process.env.QSOFT_LOADER_TEST_CONFIG);
    const config = {};
    const source = { ...settings, database: 'qsoft_loader_source' }, target = { ...settings, database: 'qsoft_loader_destination' };
    const tenant = '00000000-0000-4000-8000-000000000001', dept = '00000000-0000-4000-8000-000000000002';
    const user = '00000000-0000-4000-8000-000000000003';
    const hash = '$2b$12$' + 'A'.repeat(53);
    await loader.psql(config, source, `CREATE SCHEMA staging;
        CREATE TABLE staging.employees(employee_id text,company_id text,department_id text);
        CREATE TABLE staging.departments(department_id text,company_id text);
        CREATE TABLE public.department_departments(id uuid,tenant_id uuid,name text,status text,legacy_department_id text);
        CREATE TABLE public.iam_users(id uuid,tenant_id uuid,department_id uuid,email text,password_hash text,hierarchy_level text,is_admin boolean,status text,legacy_employee_id text);
        INSERT INTO staging.employees VALUES ('E1','C1','D1'); INSERT INTO staging.departments VALUES ('D1','C1');
        INSERT INTO public.department_departments VALUES ('${dept}','${tenant}','TI','ACTIVE','D1');
        INSERT INTO public.iam_users VALUES ('${user}','${tenant}','${dept}','mock@legacy.test','truncated','MANAGER',false,'INACTIVE','E1');`, { write: true });
    await loader.psql(config, target, `INSERT INTO iam_tenants(id,slug,name,email_domain,status) VALUES ('${tenant}','bootstrap','Mock','bootstrap.test','ACTIVE');
        INSERT INTO iam_roles(id,tenant_id,name,created_at,updated_at) VALUES (gen_random_uuid(),'${tenant}','MANAGER',now(),now());
        INSERT INTO historical.employees(company_id,employee_id,department_id) VALUES ('${tenant}','E1','D1');
        INSERT INTO historical.departments(company_id,department_id) VALUES ('${tenant}','D1');`, { write: true });
    const file = name => path.join(settings.workDir, name);
    for (const [name, db] of [['source', source], ['target', target]]) {
        fs.writeFileSync(file(name + '.env'), Object.entries({ DB_HOST: db.host, DB_PORT: db.port, DB_USER: db.user, DB_PASSWORD: db.password, DB_NAME: db.database }).map(([k, v]) => k + '=' + v).join('\n'));
    }
    fs.writeFileSync(file('hash'), hash);
    loader.saveJson(file('manifest.json'), { state: 'COMPLETED', mapping: { C1: tenant }, source: await loader.identity(config, source), target: await loader.identity(config, target) });
    async function cli(mode) {
        return new Promise((resolve, reject) => {
            const child = spawn(process.execPath, ['scripts/db/import-legacy-identities.js', '--mode', mode, '--source-env', file('source.env'), '--target-env', file('target.env'), '--manifest', file('manifest.json'), '--report', file('report.json'), '--password-hash-file', file('hash'), '--confirm-target', target.database], { windowsHide: true });
            let output = ''; child.stdout.on('data', d => output += d); child.stderr.on('data', d => output += d);
            child.on('error', reject); child.on('close', code => resolve({ code, output }));
        });
    }
    assert.equal((await cli('plan')).code, 0);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM app_user'), 0);
    let result = await cli('load'); assert.equal(result.code, 0, result.output);
    assert.ok(!result.output.includes(hash));
    assert.deepEqual(await loader.json(config, target, "SELECT json_build_object('status',status,'length',length(password_hash)) FROM app_user"), { status: 'INACTIVE', length: 60 });
    assert.deepEqual(await loader.json(config, target, "SELECT json_build_object('scope',scope_type,'origin',origin) FROM iam_role_assignments"), { scope: 'DEPARTMENT', origin: 'MIGRATION' });
    assert.equal(await loader.json(config, target, "SELECT to_json(email_domain) FROM iam_tenants"), 'legacy.test');
    assert.equal((await cli('load')).code, 0);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM iam_role_assignments'), 1);
    await loader.psql(config, target, `UPDATE iam_role_assignments SET revoked_at=now(),revoked_by='${user}';`, { write: true });
    assert.notEqual((await cli('load')).code, 0);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM iam_role_assignments WHERE revoked_at IS NULL'), 0);
    // A collision late in the transaction must not leave a new account, link or domain.
    await loader.psql(config, target, `DELETE FROM iam_role_assignments; DELETE FROM iam_legacy_user_links; DELETE FROM app_user;
        UPDATE iam_tenants SET email_domain='bootstrap.test';
        CREATE FUNCTION reject_mock() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$;
        CREATE TRIGGER reject_mock BEFORE INSERT ON iam_role_assignments FOR EACH ROW EXECUTE FUNCTION reject_mock();`, { write: true });
    assert.notEqual((await cli('load')).code, 0);
    assert.equal(await loader.json(config, target, 'SELECT count(*) FROM app_user'), 0);
    assert.equal(await loader.json(config, target, "SELECT to_json(email_domain) FROM iam_tenants"), 'bootstrap.test');
    assert.equal(await loader.json(config, source, 'SELECT length(password_hash) FROM iam_users'), 9);
});
