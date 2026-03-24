-- Flyway migration: V1__Create_loan_origination_schema.sql
-- External LOS Service — dedicated schema
-- Does NOT modify Apache Fineract's database schema

-- ============================================================
-- LOAN APPLICATION — root aggregate
-- ============================================================
CREATE TABLE loan_application (
    id                  BIGSERIAL       PRIMARY KEY,
    application_ref     VARCHAR(50)     NOT NULL UNIQUE,
    applicant_id        VARCHAR(100)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    requested_amount    NUMERIC(15,2)   NOT NULL,
    tenor_months        INTEGER         NOT NULL,
    loan_purpose        VARCHAR(30),
    product_id          BIGINT,
    fineract_loan_id    BIGINT,
    tenant_id           VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ,
    disbursed_at        TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT chk_loan_app_status CHECK (
        status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','REFERRED','DISBURSED')
    ),
    CONSTRAINT chk_requested_amount_positive CHECK (requested_amount > 0),
    CONSTRAINT chk_tenor_positive            CHECK (tenor_months > 0)
);

CREATE INDEX idx_loan_app_tenant_status ON loan_application (tenant_id, status);
CREATE INDEX idx_loan_app_applicant     ON loan_application (applicant_id);

-- ============================================================
-- APPLICANT PROFILE
-- ============================================================
CREATE TABLE applicant_profile (
    id                          BIGSERIAL       PRIMARY KEY,
    application_id              BIGINT          NOT NULL REFERENCES loan_application(id),
    full_name                   VARCHAR(200)    NOT NULL,
    monthly_income              NUMERIC(15,2)   NOT NULL,
    employment_status           VARCHAR(30)     NOT NULL,
    employment_duration_months  INTEGER         NOT NULL DEFAULT 0,
    existing_monthly_obligations NUMERIC(15,2)  NOT NULL DEFAULT 0,
    repayment_history_rating    VARCHAR(20)     NOT NULL DEFAULT 'NO_HISTORY',
    tenant_id                   VARCHAR(100)    NOT NULL
);

CREATE INDEX idx_applicant_profile_application ON applicant_profile (application_id);

-- ============================================================
-- REQUIRED DOCUMENT
-- ============================================================
CREATE TABLE required_document (
    id              BIGSERIAL       PRIMARY KEY,
    application_id  BIGINT          NOT NULL REFERENCES loan_application(id),
    document_type   VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    file_reference  VARCHAR(500),
    uploaded_at     TIMESTAMPTZ,
    verified_at     TIMESTAMPTZ,
    tenant_id       VARCHAR(100)    NOT NULL,

    CONSTRAINT chk_document_status CHECK (
        status IN ('PENDING','UPLOADED','VERIFIED','REJECTED')
    )
);

CREATE INDEX idx_required_document_application ON required_document (application_id);

-- ============================================================
-- APPROVAL STAGE
-- ============================================================
CREATE TABLE approval_stage (
    id              BIGSERIAL       PRIMARY KEY,
    application_id  BIGINT          NOT NULL REFERENCES loan_application(id),
    stage_name      VARCHAR(50)     NOT NULL,
    stage_order     INTEGER         NOT NULL,
    assigned_officer VARCHAR(200),
    decision        VARCHAR(20),
    comments        TEXT,
    decided_at      TIMESTAMPTZ,
    tenant_id       VARCHAR(100)    NOT NULL,

    CONSTRAINT chk_approval_decision CHECK (
        decision IS NULL OR decision IN ('APPROVE','REJECT','REFER')
    )
);

CREATE INDEX idx_approval_stage_application ON approval_stage (application_id);

-- ============================================================
-- CREDIT SCORE
-- ============================================================
CREATE TABLE credit_score (
    id                  BIGSERIAL       PRIMARY KEY,
    application_id      BIGINT          NOT NULL REFERENCES loan_application(id),
    score               INTEGER         NOT NULL,
    risk_category       VARCHAR(10)     NOT NULL,
    income_ratio_score  INTEGER,
    loan_burden_score   INTEGER,
    employment_score    INTEGER,
    repayment_score     INTEGER,
    purpose_score       INTEGER,
    scored_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    tenant_id           VARCHAR(100)    NOT NULL,

    CONSTRAINT chk_credit_score_range  CHECK (score BETWEEN 0 AND 1000),
    CONSTRAINT chk_risk_category       CHECK (risk_category IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX idx_credit_score_application ON credit_score (application_id);
