-- LOCAL-BUDGET-001: ledger financeiro síncrono, sem FK cross-module.
CREATE TABLE department_spending_settings (
 tenant_id uuid PRIMARY KEY,
 timezone varchar(80) NOT NULL DEFAULT 'America/Sao_Paulo',
 alert_pct integer NOT NULL DEFAULT 80 CHECK (alert_pct BETWEEN 1 AND 100)
);
CREATE TABLE department_spending_plans (
 tenant_id uuid NOT NULL,
 month varchar(7) NOT NULL,
 plan jsonb NOT NULL,
 PRIMARY KEY (tenant_id,month)
);
CREATE TABLE department_spending_reservations (
 tenant_id uuid NOT NULL,
 request_id uuid NOT NULL,
 user_id uuid NOT NULL,
 month varchar(7) NOT NULL,
 reserved numeric(22,8) NOT NULL CHECK (reserved >= 0),
 actual numeric(22,8) CHECK (actual >= 0),
 exchange_rate numeric(22,8) NOT NULL CHECK (exchange_rate > 0),
 currency varchar(3) NOT NULL,
 rate_reference varchar(200) NOT NULL,
 state varchar(12) NOT NULL CHECK (state IN ('PENDING','SETTLED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id,request_id)
);
CREATE INDEX idx_spending_user_month ON department_spending_reservations(tenant_id,month,user_id);
CREATE TABLE department_spending_audit (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,
 actor_id uuid NOT NULL,
 operation varchar(40) NOT NULL,
 before_value jsonb,
 after_value jsonb,
 created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO iam_role_permissions(role_id,resource,action)
 SELECT id,'SPENDING_BUDGET','MANAGE' FROM iam_roles WHERE name='EXECUTIVE'
 ON CONFLICT DO NOTHING;
INSERT INTO iam_role_permissions(role_id,resource,action)
 SELECT id,'SPENDING_ALERT','MANAGE' FROM iam_roles WHERE name='MANAGER'
 ON CONFLICT DO NOTHING;
