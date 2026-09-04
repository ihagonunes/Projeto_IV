--
-- PostgreSQL database dump
--

\restrict 8LIcIItE65TGBoTwGa9lNsCZ9w9oQWAf8uIrMtvKfFP81FWycXlqzricQGJ0WFW

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

-- Started on 2026-09-02 14:39:39

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 6 (class 2615 OID 19574)
-- Name: staging; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA staging;


ALTER SCHEMA staging OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 238 (class 1259 OID 19228)
-- Name: agent_policies_agents; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.agent_policies_agents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    configuration jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT agent_policies_agents_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.agent_policies_agents OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 19212)
-- Name: agent_policies_document_access_scopes; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.agent_policies_document_access_scopes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_version_id uuid CONSTRAINT agent_policies_document_access_sco_document_version_id_not_null NOT NULL,
    scope_type character varying(20) NOT NULL,
    scope_id uuid NOT NULL,
    CONSTRAINT agent_policies_document_access_scopes_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['DEPARTMENT'::character varying, 'AREA'::character varying])::text[])))
);


ALTER TABLE public.agent_policies_document_access_scopes OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 19188)
-- Name: agent_policies_document_versions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.agent_policies_document_versions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    version_number integer NOT NULL,
    status character varying(30) NOT NULL,
    processed_content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT agent_policies_document_versions_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_APPROVAL'::character varying, 'ACTIVE'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


ALTER TABLE public.agent_policies_document_versions OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 19248)
-- Name: agent_policies_executions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.agent_policies_executions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    agent_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    finished_at timestamp with time zone,
    CONSTRAINT agent_policies_executions_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


ALTER TABLE public.agent_policies_executions OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 19176)
-- Name: agent_policies_knowledge_documents; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.agent_policies_knowledge_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    document_name character varying(255) NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.agent_policies_knowledge_documents OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 19095)
-- Name: ai_gateway_models; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_gateway_models (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_model_id character varying(50),
    ai_tool_id uuid NOT NULL,
    model_name character varying(100) NOT NULL,
    model_family character varying(50),
    input_price_usd_per_1k_tokens numeric(12,6),
    output_price_usd_per_1k_tokens numeric(12,6),
    status character varying(20) DEFAULT 'AVAILABLE'::character varying NOT NULL,
    CONSTRAINT ai_gateway_models_status_check CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'DISABLED'::character varying])::text[])))
);


ALTER TABLE public.ai_gateway_models OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 19113)
-- Name: ai_gateway_provider_credentials; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_gateway_provider_credentials (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    secret_reference character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ai_gateway_provider_credentials_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying])::text[])))
);


ALTER TABLE public.ai_gateway_provider_credentials OWNER TO postgres;

--
-- TOC entry 229 (class 1259 OID 19065)
-- Name: ai_gateway_providers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_gateway_providers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ai_gateway_providers_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.ai_gateway_providers OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 19078)
-- Name: ai_gateway_tools; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_gateway_tools (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_tool_id character varying(50),
    provider_id uuid,
    tool_name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ai_gateway_tools_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.ai_gateway_tools OWNER TO postgres;

--
-- TOC entry 245 (class 1259 OID 19350)
-- Name: approved_ai_tools; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.approved_ai_tools (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    legacy_tool_id character varying(50),
    approval_status character varying(30),
    approval_date date,
    allowed_departments text
);


ALTER TABLE public.approved_ai_tools OWNER TO postgres;

--
-- TOC entry 233 (class 1259 OID 19135)
-- Name: chat_conversations; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.chat_conversations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    department_id uuid NOT NULL,
    title character varying(255),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_message_at timestamp with time zone DEFAULT now() NOT NULL,
    message_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chat_conversations_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying, 'DELETED'::character varying])::text[])))
);


ALTER TABLE public.chat_conversations OWNER TO postgres;

--
-- TOC entry 234 (class 1259 OID 19155)
-- Name: chat_messages; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.chat_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    conversation_id uuid NOT NULL,
    role character varying(20) NOT NULL,
    content text NOT NULL,
    model_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chat_messages_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ASSISTANT'::character varying])::text[])))
);


ALTER TABLE public.chat_messages OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 18958)
-- Name: department_areas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department_areas (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    department_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT department_areas_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.department_areas OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 19000)
-- Name: department_budgets; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department_budgets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    department_id uuid,
    legacy_cost_center character varying(50),
    reference_year integer NOT NULL,
    reference_month integer NOT NULL,
    budget_limit_amount numeric(12,2) NOT NULL,
    currency character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT department_budgets_reference_month_check CHECK (((reference_month >= 1) AND (reference_month <= 12)))
);


ALTER TABLE public.department_budgets OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 18940)
-- Name: department_departments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department_departments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_department_id character varying(50),
    tenant_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT department_departments_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.department_departments OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 19023)
-- Name: department_model_policies; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department_model_policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    department_id uuid NOT NULL,
    allowed_model_ids uuid[] DEFAULT '{}'::uuid[] NOT NULL,
    default_model_id uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_model_policy_default CHECK ((((cardinality(allowed_model_ids) = 0) AND (default_model_id IS NULL)) OR ((cardinality(allowed_model_ids) > 0) AND (default_model_id = ANY (allowed_model_ids)))))
);


ALTER TABLE public.department_model_policies OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 18976)
-- Name: department_user_area_memberships; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department_user_area_memberships (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    department_id uuid NOT NULL,
    area_id uuid NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_at timestamp with time zone
);


ALTER TABLE public.department_user_area_memberships OWNER TO postgres;

--
-- TOC entry 252 (class 1259 OID 19440)
-- Name: engagement_ai_credits_snapshots; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.engagement_ai_credits_snapshots (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    period_year integer NOT NULL,
    period_month integer NOT NULL,
    usage_score numeric(5,2) NOT NULL,
    training_score numeric(5,2) NOT NULL,
    license_score numeric(5,2),
    license_applicable boolean DEFAULT true NOT NULL,
    final_score numeric(5,2) NOT NULL,
    calculation_version character varying(20) DEFAULT 'v1.0'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT engagement_ai_credits_snapshots_final_score_check CHECK (((final_score >= (0)::numeric) AND (final_score <= (100)::numeric))),
    CONSTRAINT engagement_ai_credits_snapshots_period_month_check CHECK (((period_month >= 1) AND (period_month <= 12)))
);


ALTER TABLE public.engagement_ai_credits_snapshots OWNER TO postgres;

--
-- TOC entry 250 (class 1259 OID 19413)
-- Name: engagement_ai_licenses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.engagement_ai_licenses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    tool_id uuid NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    assigned_by_user_id uuid,
    revoked_at timestamp with time zone,
    revoked_by_user_id uuid,
    CONSTRAINT engagement_ai_licenses_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REVOKED'::character varying])::text[])))
);


ALTER TABLE public.engagement_ai_licenses OWNER TO postgres;

--
-- TOC entry 251 (class 1259 OID 19429)
-- Name: engagement_training_records; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.engagement_training_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_training_id character varying(50),
    tenant_id uuid NOT NULL,
    user_id uuid,
    legacy_employee_id character varying(50),
    training_identifier character varying(200) NOT NULL,
    status character varying(30) NOT NULL,
    enrolled_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT engagement_training_records_status_check CHECK (((status)::text = ANY ((ARRAY['ENROLLED'::character varying, 'COMPLETED'::character varying])::text[])))
);


ALTER TABLE public.engagement_training_records OWNER TO postgres;

--
-- TOC entry 253 (class 1259 OID 19464)
-- Name: governance_score_snapshots; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.governance_score_snapshots (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    department_id uuid,
    period_year integer NOT NULL,
    period_month integer NOT NULL,
    governance_score numeric(5,2) NOT NULL,
    adoption_score numeric(5,2) NOT NULL,
    training_score numeric(5,2) NOT NULL,
    utilization_score numeric(5,2) NOT NULL,
    final_score numeric(5,2) NOT NULL,
    calculation_version character varying(20) DEFAULT 'v1.0'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT governance_score_snapshots_final_score_check CHECK (((final_score >= (0)::numeric) AND (final_score <= (100)::numeric))),
    CONSTRAINT governance_score_snapshots_period_month_check CHECK (((period_month >= 1) AND (period_month <= 12)))
);


ALTER TABLE public.governance_score_snapshots OWNER TO postgres;

--
-- TOC entry 243 (class 1259 OID 19331)
-- Name: historical_ai_billing; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_ai_billing (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_billing_id character varying(100),
    tenant_id uuid NOT NULL,
    billing_month character varying(7),
    provider character varying(100),
    legacy_tool_id character varying(50),
    legacy_model_id character varying(50),
    cost_center character varying(50),
    usage_quantity numeric,
    total_cost numeric(14,2),
    currency character varying(3)
);


ALTER TABLE public.historical_ai_billing OWNER TO postgres;

--
-- TOC entry 242 (class 1259 OID 19319)
-- Name: historical_ai_usage_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_ai_usage_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_usage_id character varying(100),
    tenant_id uuid NOT NULL,
    legacy_employee_id character varying(50),
    legacy_department_id character varying(50),
    legacy_tool_id character varying(50),
    legacy_model_id character varying(50),
    legacy_project_id character varying(50),
    application character varying(100),
    request_count integer,
    input_tokens integer,
    output_tokens integer,
    latency_ms integer,
    request_status character varying(50),
    cost_center character varying(50),
    occurred_at timestamp with time zone NOT NULL
);


ALTER TABLE public.historical_ai_usage_logs OWNER TO postgres;

--
-- TOC entry 248 (class 1259 OID 19387)
-- Name: historical_builds; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_builds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_build_id character varying(50),
    project_id uuid NOT NULL,
    occurred_at timestamp with time zone,
    status character varying(20),
    duration_seconds integer,
    tests_executed integer,
    tests_failed integer
);


ALTER TABLE public.historical_builds OWNER TO postgres;

--
-- TOC entry 249 (class 1259 OID 19400)
-- Name: historical_deployments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_deployments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_deployment_id character varying(50),
    project_id uuid NOT NULL,
    occurred_at timestamp with time zone,
    environment character varying(20),
    status character varying(20)
);


ALTER TABLE public.historical_deployments OWNER TO postgres;

--
-- TOC entry 247 (class 1259 OID 19374)
-- Name: historical_issues; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_issues (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_issue_id character varying(50),
    project_id uuid NOT NULL,
    legacy_employee_id character varying(50),
    issue_type character varying(30),
    priority character varying(20),
    created_at timestamp with time zone,
    closed_at timestamp with time zone,
    status character varying(30)
);


ALTER TABLE public.historical_issues OWNER TO postgres;

--
-- TOC entry 246 (class 1259 OID 19361)
-- Name: historical_pull_requests; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historical_pull_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_pr_id character varying(50),
    project_id uuid NOT NULL,
    legacy_employee_id character varying(50),
    created_at timestamp with time zone,
    merged_at timestamp with time zone,
    status character varying(30),
    files_changed integer,
    additions integer,
    deletions integer,
    review_count integer
);


ALTER TABLE public.historical_pull_requests OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 18860)
-- Name: iam_tenants; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.iam_tenants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(100) NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT iam_tenants_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.iam_tenants OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 18911)
-- Name: iam_user_permission_overrides; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.iam_user_permission_overrides (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    permission_code character varying(100) NOT NULL,
    effect character varying(10) DEFAULT 'GRANT'::character varying NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT iam_user_permission_overrides_effect_check CHECK (((effect)::text = ANY ((ARRAY['GRANT'::character varying, 'DENY'::character varying])::text[])))
);


ALTER TABLE public.iam_user_permission_overrides OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 18878)
-- Name: iam_users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.iam_users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_employee_id character varying(50),
    tenant_id uuid NOT NULL,
    department_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    hierarchy_level character varying(30) NOT NULL,
    is_admin boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT iam_users_hierarchy_level_check CHECK (((hierarchy_level)::text = ANY ((ARRAY['COLLABORATOR'::character varying, 'LEADER'::character varying, 'MANAGER'::character varying, 'EXECUTIVE'::character varying])::text[]))),
    CONSTRAINT iam_users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


ALTER TABLE public.iam_users OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 19295)
-- Name: observability_budget_alerts; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.observability_budget_alerts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    department_id uuid NOT NULL,
    reference_year integer NOT NULL,
    reference_month integer NOT NULL,
    alert_type character varying(30) NOT NULL,
    threshold integer NOT NULL,
    actual_cost numeric(12,2) NOT NULL,
    budget_amount numeric(12,2) NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT observability_budget_alerts_alert_type_check CHECK (((alert_type)::text = ANY ((ARRAY['BUDGET_THRESHOLD_REACHED'::character varying, 'BUDGET_EXCEEDED'::character varying])::text[]))),
    CONSTRAINT observability_budget_alerts_reference_month_check CHECK (((reference_month >= 1) AND (reference_month <= 12))),
    CONSTRAINT observability_budget_alerts_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ACKNOWLEDGED'::character varying])::text[])))
);


ALTER TABLE public.observability_budget_alerts OWNER TO postgres;

--
-- TOC entry 240 (class 1259 OID 19268)
-- Name: observability_usage_records; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.observability_usage_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    request_id character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    department_id uuid NOT NULL,
    provider_id uuid NOT NULL,
    model_id uuid NOT NULL,
    tool_id uuid,
    project_id uuid,
    usage_origin character varying(30) NOT NULL,
    input_tokens integer NOT NULL,
    output_tokens integer NOT NULL,
    total_cost_estimated numeric(12,6) NOT NULL,
    currency character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    latency_ms integer NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT observability_usage_records_usage_origin_check CHECK (((usage_origin)::text = ANY ((ARRAY['CHAT'::character varying, 'AGENT'::character varying])::text[])))
);


ALTER TABLE public.observability_usage_records OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 19046)
-- Name: project_projects; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.project_projects (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_project_id character varying(50),
    tenant_id uuid NOT NULL,
    department_id uuid,
    project_name character varying(255) NOT NULL,
    project_type character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    start_date date,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT project_projects_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


ALTER TABLE public.project_projects OWNER TO postgres;

--
-- TOC entry 244 (class 1259 OID 19341)
-- Name: security_events; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.security_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    legacy_event_id character varying(50),
    tenant_id uuid NOT NULL,
    legacy_employee_id character varying(50),
    legacy_tool_id character varying(50),
    occurred_at timestamp with time zone,
    event_type character varying(100),
    severity character varying(20),
    policy_id character varying(50),
    action_taken character varying(100)
);


ALTER TABLE public.security_events OWNER TO postgres;

--
-- TOC entry 262 (class 1259 OID 19620)
-- Name: ai_billing; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_billing (
    billing_id text,
    company_id text,
    billing_month text,
    provider text,
    ai_tool_id text,
    model_id text,
    cost_center text,
    usage_quantity bigint,
    total_cost double precision,
    currency text
);


ALTER TABLE staging.ai_billing OWNER TO postgres;

--
-- TOC entry 263 (class 1259 OID 19625)
-- Name: ai_budgets; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_budgets (
    company_id text,
    cost_center text,
    year bigint,
    month bigint,
    budget_amount double precision
);


ALTER TABLE staging.ai_budgets OWNER TO postgres;

--
-- TOC entry 258 (class 1259 OID 19600)
-- Name: ai_licenses; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_licenses (
    company_id text,
    employee_id text,
    ai_tool_id text,
    license_type text,
    assigned_date text,
    expiration_date text,
    license_status text
);


ALTER TABLE staging.ai_licenses OWNER TO postgres;

--
-- TOC entry 257 (class 1259 OID 19595)
-- Name: ai_models; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_models (
    model_id text,
    ai_tool_id text,
    model_name text,
    model_family text,
    input_price_usd_per_1k_tokens double precision,
    output_price_usd_per_1k_tokens double precision
);


ALTER TABLE staging.ai_models OWNER TO postgres;

--
-- TOC entry 256 (class 1259 OID 19590)
-- Name: ai_tools; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_tools (
    ai_tool_id text,
    tool_name text,
    provider text,
    tool_category text,
    licensing_type text
);


ALTER TABLE staging.ai_tools OWNER TO postgres;

--
-- TOC entry 261 (class 1259 OID 19615)
-- Name: ai_usage_logs; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.ai_usage_logs (
    usage_id text,
    "timestamp" text,
    employee_id text,
    company_id text,
    department_id text,
    ai_tool_id text,
    model_id text,
    application text,
    request_count bigint,
    input_tokens bigint,
    output_tokens bigint,
    latency_ms double precision,
    request_status text,
    project_id text,
    cost_center text
);


ALTER TABLE staging.ai_usage_logs OWNER TO postgres;

--
-- TOC entry 264 (class 1259 OID 19630)
-- Name: approved_ai_tools; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.approved_ai_tools (
    company_id text,
    ai_tool_id text,
    approval_status text,
    approval_date text,
    allowed_departments text
);


ALTER TABLE staging.approved_ai_tools OWNER TO postgres;

--
-- TOC entry 268 (class 1259 OID 19650)
-- Name: builds; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.builds (
    build_id text,
    project_id text,
    "timestamp" text,
    status text,
    duration bigint,
    tests_executed bigint,
    tests_failed bigint
);


ALTER TABLE staging.builds OWNER TO postgres;

--
-- TOC entry 254 (class 1259 OID 19575)
-- Name: companies; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.companies (
    company_id text,
    company_name text,
    industry text,
    company_size text,
    employee_count bigint
);


ALTER TABLE staging.companies OWNER TO postgres;

--
-- TOC entry 255 (class 1259 OID 19580)
-- Name: departments; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.departments (
    department_id text,
    company_id text,
    department_name text
);


ALTER TABLE staging.departments OWNER TO postgres;

--
-- TOC entry 269 (class 1259 OID 19655)
-- Name: deployments; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.deployments (
    deployment_id text,
    project_id text,
    "timestamp" text,
    environment text,
    status text
);


ALTER TABLE staging.deployments OWNER TO postgres;

--
-- TOC entry 270 (class 1259 OID 19660)
-- Name: employees; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.employees (
    employee_id text,
    company_id text,
    department_id text,
    job_role text,
    seniority text,
    hire_date text,
    cost_center text,
    active text
);


ALTER TABLE staging.employees OWNER TO postgres;

--
-- TOC entry 267 (class 1259 OID 19645)
-- Name: issues; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.issues (
    issue_id text,
    project_id text,
    employee_id text,
    issue_type text,
    priority text,
    created_at text,
    closed_at text,
    status text
);


ALTER TABLE staging.issues OWNER TO postgres;

--
-- TOC entry 260 (class 1259 OID 19610)
-- Name: projects; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.projects (
    project_id text,
    company_id text,
    project_name text,
    project_type text,
    start_date text,
    status text
);


ALTER TABLE staging.projects OWNER TO postgres;

--
-- TOC entry 266 (class 1259 OID 19640)
-- Name: pull_requests; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.pull_requests (
    pull_request_id text,
    project_id text,
    employee_id text,
    created_at text,
    merged_at text,
    status text,
    files_changed bigint,
    additions bigint,
    deletions bigint,
    review_count double precision
);


ALTER TABLE staging.pull_requests OWNER TO postgres;

--
-- TOC entry 265 (class 1259 OID 19635)
-- Name: security_events; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.security_events (
    event_id text,
    company_id text,
    employee_id text,
    "timestamp" text,
    ai_tool_id text,
    event_type text,
    severity text,
    policy_id text,
    action_taken text
);


ALTER TABLE staging.security_events OWNER TO postgres;

--
-- TOC entry 259 (class 1259 OID 19605)
-- Name: training_records; Type: TABLE; Schema: staging; Owner: postgres
--

CREATE TABLE staging.training_records (
    training_id text,
    employee_id text,
    company_id text,
    course_name text,
    course_category text,
    enrollment_date text,
    completion_date text,
    completion_status text,
    score double precision,
    duration_hours double precision
);


ALTER TABLE staging.training_records OWNER TO postgres;

--
-- TOC entry 5132 (class 2606 OID 19247)
-- Name: agent_policies_agents agent_policies_agents_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_agents
    ADD CONSTRAINT agent_policies_agents_pkey PRIMARY KEY (id);


--
-- TOC entry 5130 (class 2606 OID 19222)
-- Name: agent_policies_document_access_scopes agent_policies_document_access_scopes_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_document_access_scopes
    ADD CONSTRAINT agent_policies_document_access_scopes_pkey PRIMARY KEY (id);


--
-- TOC entry 5125 (class 2606 OID 19203)
-- Name: agent_policies_document_versions agent_policies_document_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_document_versions
    ADD CONSTRAINT agent_policies_document_versions_pkey PRIMARY KEY (id);


--
-- TOC entry 5134 (class 2606 OID 19262)
-- Name: agent_policies_executions agent_policies_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_executions
    ADD CONSTRAINT agent_policies_executions_pkey PRIMARY KEY (id);


--
-- TOC entry 5123 (class 2606 OID 19187)
-- Name: agent_policies_knowledge_documents agent_policies_knowledge_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_knowledge_documents
    ADD CONSTRAINT agent_policies_knowledge_documents_pkey PRIMARY KEY (id);


--
-- TOC entry 5112 (class 2606 OID 19106)
-- Name: ai_gateway_models ai_gateway_models_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_models
    ADD CONSTRAINT ai_gateway_models_pkey PRIMARY KEY (id);


--
-- TOC entry 5115 (class 2606 OID 19129)
-- Name: ai_gateway_provider_credentials ai_gateway_provider_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_provider_credentials
    ADD CONSTRAINT ai_gateway_provider_credentials_pkey PRIMARY KEY (id);


--
-- TOC entry 5105 (class 2606 OID 19077)
-- Name: ai_gateway_providers ai_gateway_providers_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_providers
    ADD CONSTRAINT ai_gateway_providers_name_key UNIQUE (name);


--
-- TOC entry 5107 (class 2606 OID 19075)
-- Name: ai_gateway_providers ai_gateway_providers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_providers
    ADD CONSTRAINT ai_gateway_providers_pkey PRIMARY KEY (id);


--
-- TOC entry 5109 (class 2606 OID 19088)
-- Name: ai_gateway_tools ai_gateway_tools_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_tools
    ADD CONSTRAINT ai_gateway_tools_pkey PRIMARY KEY (id);


--
-- TOC entry 5154 (class 2606 OID 19359)
-- Name: approved_ai_tools approved_ai_tools_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.approved_ai_tools
    ADD CONSTRAINT approved_ai_tools_pkey PRIMARY KEY (id);


--
-- TOC entry 5117 (class 2606 OID 19153)
-- Name: chat_conversations chat_conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.chat_conversations
    ADD CONSTRAINT chat_conversations_pkey PRIMARY KEY (id);


--
-- TOC entry 5120 (class 2606 OID 19169)
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);


--
-- TOC entry 5088 (class 2606 OID 18970)
-- Name: department_areas department_areas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_areas
    ADD CONSTRAINT department_areas_pkey PRIMARY KEY (id);


--
-- TOC entry 5093 (class 2606 OID 19015)
-- Name: department_budgets department_budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_budgets
    ADD CONSTRAINT department_budgets_pkey PRIMARY KEY (id);


--
-- TOC entry 5084 (class 2606 OID 18955)
-- Name: department_departments department_departments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_departments
    ADD CONSTRAINT department_departments_pkey PRIMARY KEY (id);


--
-- TOC entry 5097 (class 2606 OID 19040)
-- Name: department_model_policies department_model_policies_department_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_model_policies
    ADD CONSTRAINT department_model_policies_department_id_key UNIQUE (department_id);


--
-- TOC entry 5099 (class 2606 OID 19038)
-- Name: department_model_policies department_model_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_model_policies
    ADD CONSTRAINT department_model_policies_pkey PRIMARY KEY (id);


--
-- TOC entry 5090 (class 2606 OID 18988)
-- Name: department_user_area_memberships department_user_area_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_user_area_memberships
    ADD CONSTRAINT department_user_area_memberships_pkey PRIMARY KEY (id);


--
-- TOC entry 5170 (class 2606 OID 19461)
-- Name: engagement_ai_credits_snapshots engagement_ai_credits_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.engagement_ai_credits_snapshots
    ADD CONSTRAINT engagement_ai_credits_snapshots_pkey PRIMARY KEY (id);


--
-- TOC entry 5165 (class 2606 OID 19427)
-- Name: engagement_ai_licenses engagement_ai_licenses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.engagement_ai_licenses
    ADD CONSTRAINT engagement_ai_licenses_pkey PRIMARY KEY (id);


--
-- TOC entry 5168 (class 2606 OID 19439)
-- Name: engagement_training_records engagement_training_records_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.engagement_training_records
    ADD CONSTRAINT engagement_training_records_pkey PRIMARY KEY (id);


--
-- TOC entry 5174 (class 2606 OID 19484)
-- Name: governance_score_snapshots governance_score_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.governance_score_snapshots
    ADD CONSTRAINT governance_score_snapshots_pkey PRIMARY KEY (id);


--
-- TOC entry 5149 (class 2606 OID 19340)
-- Name: historical_ai_billing historical_ai_billing_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_ai_billing
    ADD CONSTRAINT historical_ai_billing_pkey PRIMARY KEY (id);


--
-- TOC entry 5146 (class 2606 OID 19329)
-- Name: historical_ai_usage_logs historical_ai_usage_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_ai_usage_logs
    ADD CONSTRAINT historical_ai_usage_logs_pkey PRIMARY KEY (id);


--
-- TOC entry 5161 (class 2606 OID 19394)
-- Name: historical_builds historical_builds_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_builds
    ADD CONSTRAINT historical_builds_pkey PRIMARY KEY (id);


--
-- TOC entry 5163 (class 2606 OID 19407)
-- Name: historical_deployments historical_deployments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_deployments
    ADD CONSTRAINT historical_deployments_pkey PRIMARY KEY (id);


--
-- TOC entry 5159 (class 2606 OID 19381)
-- Name: historical_issues historical_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_issues
    ADD CONSTRAINT historical_issues_pkey PRIMARY KEY (id);


--
-- TOC entry 5157 (class 2606 OID 19368)
-- Name: historical_pull_requests historical_pull_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_pull_requests
    ADD CONSTRAINT historical_pull_requests_pkey PRIMARY KEY (id);


--
-- TOC entry 5070 (class 2606 OID 18875)
-- Name: iam_tenants iam_tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_tenants
    ADD CONSTRAINT iam_tenants_pkey PRIMARY KEY (id);


--
-- TOC entry 5072 (class 2606 OID 18877)
-- Name: iam_tenants iam_tenants_slug_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_tenants
    ADD CONSTRAINT iam_tenants_slug_key UNIQUE (slug);


--
-- TOC entry 5080 (class 2606 OID 18927)
-- Name: iam_user_permission_overrides iam_user_permission_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_user_permission_overrides
    ADD CONSTRAINT iam_user_permission_overrides_pkey PRIMARY KEY (id);


--
-- TOC entry 5074 (class 2606 OID 18901)
-- Name: iam_users iam_users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_users
    ADD CONSTRAINT iam_users_pkey PRIMARY KEY (id);


--
-- TOC entry 5142 (class 2606 OID 19316)
-- Name: observability_budget_alerts observability_budget_alerts_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.observability_budget_alerts
    ADD CONSTRAINT observability_budget_alerts_pkey PRIMARY KEY (id);


--
-- TOC entry 5138 (class 2606 OID 19290)
-- Name: observability_usage_records observability_usage_records_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.observability_usage_records
    ADD CONSTRAINT observability_usage_records_pkey PRIMARY KEY (id);


--
-- TOC entry 5140 (class 2606 OID 19292)
-- Name: observability_usage_records observability_usage_records_request_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.observability_usage_records
    ADD CONSTRAINT observability_usage_records_request_id_key UNIQUE (request_id);


--
-- TOC entry 5103 (class 2606 OID 19062)
-- Name: project_projects project_projects_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.project_projects
    ADD CONSTRAINT project_projects_pkey PRIMARY KEY (id);


--
-- TOC entry 5152 (class 2606 OID 19348)
-- Name: security_events security_events_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_pkey PRIMARY KEY (id);


--
-- TOC entry 5144 (class 2606 OID 19318)
-- Name: observability_budget_alerts uk_budget_alert; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.observability_budget_alerts
    ADD CONSTRAINT uk_budget_alert UNIQUE (department_id, reference_year, reference_month, alert_type);


--
-- TOC entry 5172 (class 2606 OID 19463)
-- Name: engagement_ai_credits_snapshots uk_credits_period; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.engagement_ai_credits_snapshots
    ADD CONSTRAINT uk_credits_period UNIQUE (tenant_id, user_id, period_year, period_month);


--
-- TOC entry 5095 (class 2606 OID 19017)
-- Name: department_budgets uk_dept_budget_period; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_budgets
    ADD CONSTRAINT uk_dept_budget_period UNIQUE (department_id, reference_year, reference_month, legacy_cost_center);


--
-- TOC entry 5127 (class 2606 OID 19205)
-- Name: agent_policies_document_versions uk_doc_version; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_document_versions
    ADD CONSTRAINT uk_doc_version UNIQUE (document_id, version_number);


--
-- TOC entry 5176 (class 2606 OID 19486)
-- Name: governance_score_snapshots uk_governance_snapshot; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.governance_score_snapshots
    ADD CONSTRAINT uk_governance_snapshot UNIQUE (tenant_id, department_id, period_year, period_month);


--
-- TOC entry 5082 (class 2606 OID 18929)
-- Name: iam_user_permission_overrides uk_user_permission_override; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_user_permission_overrides
    ADD CONSTRAINT uk_user_permission_override UNIQUE (tenant_id, user_id, permission_code);


--
-- TOC entry 5078 (class 2606 OID 18903)
-- Name: iam_users uk_users_tenant_email; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_users
    ADD CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email);


--
-- TOC entry 5113 (class 1259 OID 19112)
-- Name: idx_ai_models_legacy_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_models_legacy_id ON public.ai_gateway_models USING btree (legacy_model_id);


--
-- TOC entry 5110 (class 1259 OID 19094)
-- Name: idx_ai_tools_legacy_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_tools_legacy_id ON public.ai_gateway_tools USING btree (legacy_tool_id);


--
-- TOC entry 5155 (class 1259 OID 19360)
-- Name: idx_approved_tools_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_approved_tools_tenant ON public.approved_ai_tools USING btree (tenant_id);


--
-- TOC entry 5118 (class 1259 OID 19154)
-- Name: idx_conversations_owner; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_conversations_owner ON public.chat_conversations USING btree (tenant_id, user_id);


--
-- TOC entry 5085 (class 1259 OID 18957)
-- Name: idx_departments_legacy_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_departments_legacy_id ON public.department_departments USING btree (legacy_department_id);


--
-- TOC entry 5086 (class 1259 OID 18956)
-- Name: idx_departments_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_departments_tenant ON public.department_departments USING btree (tenant_id);


--
-- TOC entry 5147 (class 1259 OID 19330)
-- Name: idx_hist_usage_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_hist_usage_tenant ON public.historical_ai_usage_logs USING btree (tenant_id);


--
-- TOC entry 5121 (class 1259 OID 19175)
-- Name: idx_messages_conversation; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_messages_conversation ON public.chat_messages USING btree (conversation_id);


--
-- TOC entry 5100 (class 1259 OID 19064)
-- Name: idx_projects_legacy_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_projects_legacy_id ON public.project_projects USING btree (legacy_project_id);


--
-- TOC entry 5101 (class 1259 OID 19063)
-- Name: idx_projects_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_projects_tenant ON public.project_projects USING btree (tenant_id);


--
-- TOC entry 5150 (class 1259 OID 19349)
-- Name: idx_security_events_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_security_events_tenant ON public.security_events USING btree (tenant_id);


--
-- TOC entry 5091 (class 1259 OID 18999)
-- Name: idx_uam_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_uam_user ON public.department_user_area_memberships USING btree (user_id) WHERE (removed_at IS NULL);


--
-- TOC entry 5135 (class 1259 OID 19294)
-- Name: idx_usage_department; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_usage_department ON public.observability_usage_records USING btree (department_id);


--
-- TOC entry 5136 (class 1259 OID 19293)
-- Name: idx_usage_tenant_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_usage_tenant_time ON public.observability_usage_records USING btree (tenant_id, occurred_at);


--
-- TOC entry 5075 (class 1259 OID 18910)
-- Name: idx_users_legacy_employee_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_legacy_employee_id ON public.iam_users USING btree (legacy_employee_id);


--
-- TOC entry 5076 (class 1259 OID 18909)
-- Name: idx_users_tenant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_tenant ON public.iam_users USING btree (tenant_id);


--
-- TOC entry 5166 (class 1259 OID 19428)
-- Name: uk_one_active_license; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_one_active_license ON public.engagement_ai_licenses USING btree (tenant_id, user_id, tool_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- TOC entry 5128 (class 1259 OID 19211)
-- Name: uk_one_active_version_per_document; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_one_active_version_per_document ON public.agent_policies_document_versions USING btree (document_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- TOC entry 5190 (class 2606 OID 19223)
-- Name: agent_policies_document_access_scopes agent_policies_document_access_scopes_document_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_document_access_scopes
    ADD CONSTRAINT agent_policies_document_access_scopes_document_version_id_fkey FOREIGN KEY (document_version_id) REFERENCES public.agent_policies_document_versions(id);


--
-- TOC entry 5189 (class 2606 OID 19206)
-- Name: agent_policies_document_versions agent_policies_document_versions_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_document_versions
    ADD CONSTRAINT agent_policies_document_versions_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.agent_policies_knowledge_documents(id);


--
-- TOC entry 5191 (class 2606 OID 19263)
-- Name: agent_policies_executions agent_policies_executions_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.agent_policies_executions
    ADD CONSTRAINT agent_policies_executions_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agent_policies_agents(id);


--
-- TOC entry 5186 (class 2606 OID 19107)
-- Name: ai_gateway_models ai_gateway_models_ai_tool_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_models
    ADD CONSTRAINT ai_gateway_models_ai_tool_id_fkey FOREIGN KEY (ai_tool_id) REFERENCES public.ai_gateway_tools(id);


--
-- TOC entry 5187 (class 2606 OID 19130)
-- Name: ai_gateway_provider_credentials ai_gateway_provider_credentials_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_provider_credentials
    ADD CONSTRAINT ai_gateway_provider_credentials_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.ai_gateway_providers(id);


--
-- TOC entry 5185 (class 2606 OID 19089)
-- Name: ai_gateway_tools ai_gateway_tools_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_gateway_tools
    ADD CONSTRAINT ai_gateway_tools_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.ai_gateway_providers(id);


--
-- TOC entry 5188 (class 2606 OID 19170)
-- Name: chat_messages chat_messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.chat_conversations(id);


--
-- TOC entry 5180 (class 2606 OID 18971)
-- Name: department_areas department_areas_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_areas
    ADD CONSTRAINT department_areas_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department_departments(id);


--
-- TOC entry 5183 (class 2606 OID 19018)
-- Name: department_budgets department_budgets_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_budgets
    ADD CONSTRAINT department_budgets_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department_departments(id);


--
-- TOC entry 5184 (class 2606 OID 19041)
-- Name: department_model_policies department_model_policies_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_model_policies
    ADD CONSTRAINT department_model_policies_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department_departments(id);


--
-- TOC entry 5181 (class 2606 OID 18994)
-- Name: department_user_area_memberships department_user_area_memberships_area_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_user_area_memberships
    ADD CONSTRAINT department_user_area_memberships_area_id_fkey FOREIGN KEY (area_id) REFERENCES public.department_areas(id);


--
-- TOC entry 5182 (class 2606 OID 18989)
-- Name: department_user_area_memberships department_user_area_memberships_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department_user_area_memberships
    ADD CONSTRAINT department_user_area_memberships_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department_departments(id);


--
-- TOC entry 5194 (class 2606 OID 19395)
-- Name: historical_builds historical_builds_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_builds
    ADD CONSTRAINT historical_builds_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.project_projects(id);


--
-- TOC entry 5195 (class 2606 OID 19408)
-- Name: historical_deployments historical_deployments_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_deployments
    ADD CONSTRAINT historical_deployments_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.project_projects(id);


--
-- TOC entry 5193 (class 2606 OID 19382)
-- Name: historical_issues historical_issues_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_issues
    ADD CONSTRAINT historical_issues_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.project_projects(id);


--
-- TOC entry 5192 (class 2606 OID 19369)
-- Name: historical_pull_requests historical_pull_requests_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historical_pull_requests
    ADD CONSTRAINT historical_pull_requests_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.project_projects(id);


--
-- TOC entry 5178 (class 2606 OID 18930)
-- Name: iam_user_permission_overrides iam_user_permission_overrides_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_user_permission_overrides
    ADD CONSTRAINT iam_user_permission_overrides_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenants(id);


--
-- TOC entry 5179 (class 2606 OID 18935)
-- Name: iam_user_permission_overrides iam_user_permission_overrides_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_user_permission_overrides
    ADD CONSTRAINT iam_user_permission_overrides_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.iam_users(id);


--
-- TOC entry 5177 (class 2606 OID 18904)
-- Name: iam_users iam_users_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.iam_users
    ADD CONSTRAINT iam_users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenants(id);


-- Completed on 2026-09-02 14:39:39

--
-- PostgreSQL database dump complete
--

\unrestrict 8LIcIItE65TGBoTwGa9lNsCZ9w9oQWAf8uIrMtvKfFP81FWycXlqzricQGJ0WFW