const { test } = require('node:test');
const assert = require('node:assert/strict');
const { prepare, script } = require('../../../scripts/db/import-legacy-identities');
const tenant = '00000000-0000-4000-8000-000000000001';
const dept = '00000000-0000-4000-8000-000000000002';
const id = '00000000-0000-4000-8000-000000000003';
const hash = '$2b$12$' + 'A'.repeat(53);
const departments = [{ id: dept, old_tenant_id: 'old', company_id: 'C1', legacy_department_id: 'D1', name: 'TI', status: 'ACTIVE' }];
const user = { id, old_tenant_id: 'old', company_id: 'C1', department_id: dept, employee_department_id: 'D1', legacy_employee_id: 'E1', email: 'user@example.test', password_hash: 'truncated', hierarchy_level: 'COLLABORATOR', is_admin: false, status: 'ACTIVE' };
const run = (users = [user], mapping = { C1: tenant }, replacement = hash) => prepare(users, departments, mapping, replacement);

test('invalid mock hashes need an explicit valid replacement; valid hashes and inactive status survive', () => {
    assert.throws(() => prepare([user], departments, { C1: tenant }));
    assert.throws(() => run([user], undefined, 'invalid'));
    assert.equal(run().users[0].password_hash, hash);
    assert.equal(run().users[0].password_reset, true);
    const preserved = run([{ ...user, password_hash: hash, status: 'INACTIVE' }]).users[0];
    assert.equal(preserved.status, 'INACTIVE');
    assert.equal(preserved.password_reset, false);
    assert.equal(preserved.scope_type, 'SELF');
});

test('identity import fails closed on privilege ambiguity, wrong department and duplicate identities', () => {
    for (const change of [{ is_admin: true }, { hierarchy_level: 'UNKNOWN' }, { employee_department_id: 'D2' }, { old_tenant_id: 'other' }, { company_id: 'other' }]) {
        assert.throws(() => run([{ ...user, ...change }]));
    }
    assert.throws(() => run([user, { ...user, id: '00000000-0000-4000-8000-000000000004' }]));
    assert.throws(() => run([user], { C1: tenant, C2: tenant }));
    assert.equal(run([{ ...user, hierarchy_level: 'MANAGER' }]).users[0].scope_type, 'DEPARTMENT');
});

test('SQL transaction refuses divergent accounts and revoked grants without updating role permissions', () => {
    const sql = script(run());
    assert.match(sql, /^BEGIN;/);
    assert.match(sql, /COMMIT;/);
    assert.match(sql, /revoked_at IS NULL/);
    assert.match(sql, /nenhuma sobrescrita automática/);
    assert.doesNotMatch(sql, /(?:UPDATE|INSERT INTO|DELETE FROM) iam_role_permissions/i);
});
