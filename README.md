# fineract-loan-origination-poc

> **GSoC 2026 Proof-of-Concept** | Apache Fineract | Loan Origination System (LOS)
>
> Candidate: Sujan Kumar MV | Mentor: James Dailey | Module: `fineract-loan-origination`

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/KRYSTALM7/fineract-loan-origination-poc)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)](https://spring.io/projects/spring-boot)
[![JIRA](https://img.shields.io/badge/JIRA-FINERACT--2442-blue)](https://issues.apache.org/jira/browse/FINERACT-2442)

---

## What This Is

Apache Fineract is a mature core banking platform — but it has **no Loan Origination System (LOS)**. Every institution using Fineract must build origination from scratch using custom code or expensive commercial tools.

This repository is a **standalone Spring Boot POC** that demonstrates how a standardized origination workflow can be layered on top of Fineract's existing REST APIs — without touching Fineract's core codebase.

It covers:
- Loan application lifecycle (DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED → DISBURSED)
- Multi-stage approval workflow (Loan Officer → Branch Manager → Credit Committee)
- Rule-based credit scoring with pluggable strategy interface
- Document tracking and validation
- Disbursement bridge that calls Fineract's `POST /loans` on approval

This is the exploratory reference implementation for [FINERACT-2442](https://issues.apache.org/jira/browse/FINERACT-2442). The long-term path is migration into the `fineract-loan-origination` placeholder module once the community aligns on the design.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              External LOS Service (This Repo)               │
│                                                             │
│  REST API Layer  →  Workflow Engine  →  Credit Engine       │
│        ↓                  ↓                  ↓              │
│   LoanApplication    ApprovalStage      CreditScore         │
│        ↓                                                    │
│  DisbursementBridge ──────────────────────────────────────► │
└─────────────────────────────────────────────────────────────┘
                                                    │
                              ┌─────────────────────▼──────┐
                              │    Apache Fineract Core    │
                              │    POST /loans             │
                              │    GET /clients/{id}       │
                              └────────────────────────────┘
```

**Key design principle:** Zero changes to Fineract core. All integration happens through published REST APIs.

---

## Loan Application State Machine

```
DRAFT ──► SUBMITTED ──► UNDER_REVIEW ──► APPROVED ──► DISBURSED
                                    └──► REJECTED
                                    └──► REFERRED ──► UNDER_REVIEW
```

| Transition | Trigger |
|---|---|
| DRAFT → SUBMITTED | Applicant submits complete application |
| SUBMITTED → UNDER_REVIEW | Loan officer picks up application |
| UNDER_REVIEW → APPROVED | All approval stages pass |
| UNDER_REVIEW → REJECTED | Any stage returns REJECT |
| UNDER_REVIEW → REFERRED | Referred back for more info |
| APPROVED → DISBURSED | DisbursementBridge creates loan in Fineract |

---

## Sample API Payloads

### Submit a Loan Application

**POST** `/api/v1/loan-applications`

Request:
```json
{
  "tenantId": "default",
  "applicantId": "CLI-2024-00123",
  "requestedAmount": 50000.00,
  "tenorMonths": 12,
  "loanPurpose": "BUSINESS",
  "applicantProfile": {
    "name": "Priya Sharma",
    "monthlyIncome": 25000.00,
    "employmentStatus": "SELF_EMPLOYED",
    "employmentDurationMonths": 36,
    "existingLoanObligations": 5000.00
  }
}
```

Response `201 Created`:
```json
{
  "applicationId": "LOS-2026-00101",
  "status": "DRAFT",
  "requestedAmount": 50000.00,
  "tenorMonths": 12,
  "tenantId": "default",
  "createdAt": "2026-05-26T10:30:00Z",
  "nextAction": "SUBMIT"
}
```

---

### Get Application with Credit Score

**GET** `/api/v1/loan-applications/LOS-2026-00101`

Response `200 OK`:
```json
{
  "applicationId": "LOS-2026-00101",
  "status": "UNDER_REVIEW",
  "requestedAmount": 50000.00,
  "tenorMonths": 12,
  "tenantId": "default",
  "applicantProfile": {
    "name": "Priya Sharma",
    "monthlyIncome": 25000.00,
    "employmentStatus": "SELF_EMPLOYED"
  },
  "creditScore": {
    "score": 720,
    "riskCategory": "LOW",
    "factors": {
      "incomeToLoanRatio": { "score": 85, "weight": 0.30 },
      "existingLoanBurden": { "score": 78, "weight": 0.25 },
      "employmentStability": { "score": 70, "weight": 0.20 },
      "repaymentHistory":   { "score": 90, "weight": 0.15 },
      "loanPurposeRisk":    { "score": 80, "weight": 0.10 }
    },
    "scoredAt": "2026-05-26T10:31:00Z"
  },
  "documents": [
    { "type": "INCOME_PROOF", "status": "VERIFIED" },
    { "type": "ID_PROOF",     "status": "VERIFIED" },
    { "type": "ADDRESS_PROOF","status": "PENDING"  }
  ],
  "approvalHistory": [
    {
      "stage": "LOAN_OFFICER",
      "officer": "rajesh.kumar@mfi.org",
      "decision": "APPROVE",
      "comments": "Income verified. Proceeding to branch review.",
      "decidedAt": "2026-05-27T09:00:00Z"
    }
  ]
}
```

---

### Approve at Current Stage

**POST** `/api/v1/loan-applications/LOS-2026-00101/approve`

Request:
```json
{
  "officerId": "rajesh.kumar@mfi.org",
  "comments": "All documents verified. Income-to-loan ratio acceptable.",
  "stage": "LOAN_OFFICER"
}
```

Response `200 OK`:
```json
{
  "applicationId": "LOS-2026-00101",
  "status": "UNDER_REVIEW",
  "currentStage": "BRANCH_MANAGER",
  "message": "LOAN_OFFICER stage approved. Escalated to BRANCH_MANAGER."
}
```

---

### Disbursement Bridge — Final Approval

When application reaches `APPROVED`, the bridge auto-calls Fineract:

**POST** `{fineract-host}/fineract-provider/api/v1/loans` (Fineract API)

```json
{
  "clientId": 123,
  "productId": 1,
  "principal": 50000.00,
  "loanTermFrequency": 12,
  "loanTermFrequencyType": 2,
  "loanType": "individual",
  "numberOfRepayments": 12,
  "repaymentFrequencyType": 2,
  "interestRatePerPeriod": 18,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy",
  "expectedDisbursementDate": "01 June 2026",
  "submittedOnDate": "26 May 2026",
  "externalId": "LOS-2026-00101"
}
```

Fineract response maps back to:
```json
{
  "applicationId": "LOS-2026-00101",
  "status": "DISBURSED",
  "fineractLoanId": 458,
  "disbursedAt": "2026-06-01T09:00:00Z"
}
```

---

## REST API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/loan-applications` | Submit new application |
| GET | `/api/v1/loan-applications/{id}` | Get application by ID |
| GET | `/api/v1/loan-applications` | List with filters (status, branch, date) |
| PATCH | `/api/v1/loan-applications/{id}/status` | Update status (state machine validated) |
| POST | `/api/v1/loan-applications/{id}/documents` | Upload document |
| GET | `/api/v1/loan-applications/{id}/documents` | List documents |
| DELETE | `/api/v1/loan-applications/{id}/documents/{docId}` | Remove document |
| POST | `/api/v1/loan-applications/{id}/approve` | Record approval at current stage |
| POST | `/api/v1/loan-applications/{id}/reject` | Reject application |
| POST | `/api/v1/loan-applications/{id}/refer` | Refer back for more info |
| GET | `/api/v1/loan-applications/{id}/credit-score` | Get computed credit score |

Full Swagger UI available at: `http://localhost:8085/swagger-ui.html`

---

## Credit Scoring Engine

Rule-based scoring designed for low-resource MFI environments — no cloud dependencies, no external API calls.

| Factor | Weight | Description |
|--------|--------|-------------|
| Income-to-loan ratio | 30% | Requested amount vs. monthly income |
| Existing loan burden | 25% | Current obligations vs. income |
| Employment stability | 20% | Employment type and duration |
| Repayment history | 15% | Past behavior from Fineract records |
| Loan purpose risk | 10% | Category-based risk classification |

The scoring engine implements `CreditScoringStrategy` — a pluggable interface for future bureau or ML model integration.

**Score Bands:**

| Score | Risk Category | Recommended Action |
|-------|--------------|-------------------|
| 750–1000 | LOW | Auto-approve eligible |
| 500–749 | MEDIUM | Manual review required |
| 0–499 | HIGH | Refer or reject |

---

## Multi-Tenancy

Every entity carries a `tenantId` column. All queries are filtered by `tenantId` extracted from the `X-Fineract-Platform-TenantId` request header — matching Fineract's own multi-tenancy convention. Tenant data is fully isolated at the query layer.

---

## Security

| Concern | Approach |
|---------|----------|
| Authentication | JWT Bearer token validation |
| Authorization | Role-based: `ROLE_LOAN_OFFICER`, `ROLE_BRANCH_MANAGER`, `ROLE_CREDIT_COMMITTEE`, `ROLE_APPLICANT` |
| Data in transit | HTTPS enforced in production profile |
| Tenant isolation | `tenantId` enforced on every query |
| Sensitive fields | Income and personal data excluded from list responses |

---

## Running Locally

**Prerequisites:** Java 21, Docker, Docker Compose

```bash
git clone https://github.com/KRYSTALM7/fineract-loan-origination-poc.git
cd fineract-loan-origination-poc

# Start PostgreSQL
docker-compose up -d postgres

# Run the service
./gradlew bootRun

# Swagger UI
open http://localhost:8085/swagger-ui.html
```

**With Fineract running locally:**
```bash
# Set Fineract host in application.properties
fineract.base-url=http://localhost:8080/fineract-provider/api/v1

# Run full stack
docker-compose up -d
```

---

## Project Structure

```
fineract-loan-origination-poc/
├── src/main/java/org/apache/fineract/los/
│   ├── api/                  # REST controllers
│   ├── domain/               # Entities + state machine
│   ├── service/              # Business logic
│   ├── workflow/             # Approval workflow engine
│   ├── scoring/              # Credit scoring engine + strategy interface
│   ├── bridge/               # Fineract disbursement integration
│   └── config/               # Security, multi-tenancy, Swagger config
├── src/main/resources/
│   ├── db/migration/         # Flyway SQL migrations
│   └── application.properties
├── src/test/                 # Unit + integration tests
├── docs/
│   ├── ARCHITECTURE.md       # Full architecture documentation
│   ├── API_CONTRACTS.md      # Complete API contract reference
│   ├── SURVEY.md             # LOS model survey (MFI, Bank, Fintech)
│   └── WORKFLOW.md           # Approval workflow design document
├── docker-compose.yml
├── build.gradle
└── README.md
```

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Unit test coverage | ≥ 80% |
| Integration test coverage | All 11 API endpoints |
| State machine transitions | All 6 valid + invalid transition rejection tested |
| Credit scoring scenarios | ≥ 10 distinct applicant profiles (Low/Medium/High) |
| API response time (p95) | < 200ms for read endpoints |
| Concurrent submissions | Handles 20 simultaneous applications without data corruption |

---

## Roadmap to Fineract Core

This POC is explicitly exploratory. The path to upstream integration:

1. **POC phase (this repo):** Validate workflow design, gather community feedback
2. **Community review:** Share findings on `dev@fineract.apache.org`, incorporate feedback
3. **Core migration:** Move entities and services into `fineract-loan-origination` placeholder module
4. **API alignment:** Align REST contracts with FINERACT-2418 once resolved
5. **Upstream PR:** Submit to `apache/fineract` with full test coverage and migration scripts

---

## GSoC 2026 Deliverable Tracking

| Deliverable | Status | Week |
|-------------|--------|------|
| D1 — LOS Survey & Design Doc | 🔄 In Progress | 1–2 |
| D2 — External Service + Core Entities | ⏳ Planned | 3–4 |
| D3 — REST API Phase 1 | ⏳ Planned | 5–7 |
| D4 — Approval Workflow | ⏳ Planned | 8–9 |
| D5 — Credit Scoring Engine | ⏳ Planned | 10–11 |
| D6 — Disbursement Bridge | ⏳ Planned | 11–12 |
| D7 — Documentation + FIGMA | ⏳ Planned | 13–14 |

---

## Related JIRA Tickets

- [FINERACT-2442](https://issues.apache.org/jira/browse/FINERACT-2442) — Loan Origination POC (this project)
- [FINERACT-2418](https://issues.apache.org/jira/browse/FINERACT-2418) — New LOS APIs (disbursement bridge target)
- [FINERACT-2515](https://issues.apache.org/jira/browse/FINERACT-2515) — PR: Remove stale TODO stubs (merged)
- [FINERACT-2543](https://issues.apache.org/jira/browse/FINERACT-2543) — PR: Fix SBOM generation errors

---

## License

Apache License 2.0 — see [LICENSE](LICENSE)

---

## Author

**Sujan Kumar MV** | [GitHub](https://github.com/KRYSTALM7) | [Portfolio](https://sujan-space.vercel.app) | [LinkedIn](https://linkedin.com/in/sujankumar2003)

*GSoC 2026 Contributor — Apache Software Foundation (Fineract)*
