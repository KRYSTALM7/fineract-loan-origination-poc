# fineract-loan-origination-poc

> **Proposal-Phase Proof of Concept** | Apache Fineract | Loan Origination System (LOS)
>
> Author: Sujan Kumar MV | Google Summer of Code 2026 Proposal Work
>
> Related Initiative: FINERACT-2442

[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)](https://spring.io/projects/spring-boot)

---

## Overview

Apache Fineract is a mature open-source core banking platform, but it does not currently provide a native Loan Origination System (LOS). Institutions typically implement origination workflows externally or integrate commercial LOS solutions.

This repository contains an early proof of concept created during the proposal phase of Google Summer of Code (GSoC) 2026. The objective was to explore how a standardized Loan Origination System could be designed around Apache Fineract while preserving the platform's existing architecture.

The repository combines:

* Architectural exploration
* Workflow modeling
* State machine design
* Credit scoring concepts
* Fineract integration concepts
* Prototype domain implementation

The work documented here later informed the implementation effort associated with FINERACT-2442.

---

## Proposed Architecture

The following diagram represents the target architecture explored during the proposal phase.

```text
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

### Design Principle

**Zero changes to Apache Fineract core.**

All integration occurs through published REST APIs and extension points.

---

## Proposed Loan Application State Machine

The following workflow was designed during the proof-of-concept phase.

```text
DRAFT ──► SUBMITTED ──► UNDER_REVIEW ──► APPROVED ──► DISBURSED
                                    └──► REJECTED
                                    └──► REFERRED ──► UNDER_REVIEW
```

| Transition               | Trigger                         |
| ------------------------ | ------------------------------- |
| DRAFT → SUBMITTED        | Applicant submits application   |
| SUBMITTED → UNDER_REVIEW | Loan officer begins review      |
| UNDER_REVIEW → APPROVED  | Approval stages completed       |
| UNDER_REVIEW → REJECTED  | Review failure                  |
| UNDER_REVIEW → REFERRED  | Additional information required |
| APPROVED → DISBURSED     | Loan created in Fineract        |

---

## Components Implemented

This repository contains prototype implementations of:

### Domain Model

* LoanApplication
* LoanOriginationStatus

### State Machine

* LoanOriginationStateMachine
* Transition validation logic
* Unit tests

### Credit Scoring Extension Point

* CreditScoringStrategy interface

### Fineract Integration Prototype

* DisbursementBridgeService

### Database Layer

* Flyway migration
* Initial schema definition

---

## Illustrative API Design

The following API examples represent the intended LOS design explored during the proposal phase.

These endpoints are **design references only** and are not implemented in the current repository.

### Submit Loan Application

POST `/api/v1/loan-applications`

```json
{
  "tenantId": "default",
  "applicantId": "CLI-2024-00123",
  "requestedAmount": 50000.00,
  "tenorMonths": 12,
  "loanPurpose": "BUSINESS"
}
```

Response:

```json
{
  "applicationId": "LOS-2026-00101",
  "status": "DRAFT"
}
```

### Retrieve Application

GET `/api/v1/loan-applications/{id}`

```json
{
  "applicationId": "LOS-2026-00101",
  "status": "UNDER_REVIEW",
  "creditScore": {
    "score": 720,
    "riskCategory": "LOW"
  }
}
```

---

## Planned API Surface

| Method | Endpoint                                    | Description          |
| ------ | ------------------------------------------- | -------------------- |
| POST   | /api/v1/loan-applications                   | Submit application   |
| GET    | /api/v1/loan-applications/{id}              | Retrieve application |
| GET    | /api/v1/loan-applications                   | Search applications  |
| PATCH  | /api/v1/loan-applications/{id}/status       | Change status        |
| POST   | /api/v1/loan-applications/{id}/approve      | Approval action      |
| POST   | /api/v1/loan-applications/{id}/reject       | Rejection action     |
| POST   | /api/v1/loan-applications/{id}/refer        | Referral action      |
| GET    | /api/v1/loan-applications/{id}/credit-score | Score lookup         |

---

## Credit Scoring Concept

The proof of concept proposed a rule-based scoring engine suitable for low-resource financial institutions.

| Factor               | Weight |
| -------------------- | ------ |
| Income-to-loan ratio | 30%    |
| Existing obligations | 25%    |
| Employment stability | 20%    |
| Repayment history    | 15%    |
| Loan purpose risk    | 10%    |

### Score Bands

| Score    | Risk   |
| -------- | ------ |
| 750–1000 | LOW    |
| 500–749  | MEDIUM |
| 0–499    | HIGH   |

The repository includes the `CreditScoringStrategy` extension interface intended to support future implementations.

---

## Planned Multi-Tenancy

The proposed LOS architecture aligned with Apache Fineract's tenant model.

```text
X-Fineract-Platform-TenantId
            │
            ▼
     Tenant Isolation
            │
            ▼
     Loan Applications
```

Each entity would carry a tenant identifier and all queries would be filtered according to tenant context.

---

## Planned Security Model

| Concern            | Approach                    |
| ------------------ | --------------------------- |
| Authentication     | JWT Bearer Tokens           |
| Authorization      | Role-Based Access Control   |
| Tenant Isolation   | Tenant-Aware Queries        |
| Data Protection    | Restricted Sensitive Fields |
| Transport Security | HTTPS                       |

---

## Repository Status

This repository represents an early proposal-phase proof of concept.

### Implemented

* Domain model
* State machine
* Credit scoring extension interface
* Flyway migration
* Unit tests
* Integration prototype

### Not Implemented

* Spring Boot application entry point
* REST controllers
* Swagger/OpenAPI endpoints
* Approval workflow engine
* Security configuration
* Multi-tenancy enforcement
* Production-ready Fineract integration

Therefore, the repository is not intended to run as a standalone application.

---

## Current Project Structure

```text
fineract-loan-origination-poc/
├── src/main/java/org/apache/fineract/los/
│   ├── bridge/
│   │   └── DisbursementBridgeService.java
│   ├── domain/
│   │   ├── LoanApplication.java
│   │   ├── LoanOriginationStateMachine.java
│   │   └── LoanOriginationStatus.java
│   └── scoring/
│       └── CreditScoringStrategy.java
│
├── src/main/resources/
│   └── db/migration/
│       └── V1__Create_loan_origination_schema.sql
│
├── src/test/
│   └── LoanOriginationStateMachineTest.java
│
├── docs/
│   └── ARCHITECTURE.md
│
├── build.gradle
└── README.md
```

---

## Roadmap to Apache Fineract

1. Validate architecture concepts
2. Gather community feedback
3. Refine workflow design
4. Align APIs with Apache Fineract
5. Develop production implementation
6. Upstream integration into Apache Fineract

---

## Historical Context

This repository was created during the proposal phase of Google Summer of Code 2026.

Following acceptance into GSoC 2026, implementation work continued under FINERACT-2442 within Apache Fineract-related repositories and project branches.

---

## License

Apache License 2.0

See LICENSE for details.

---

## Author

**Sujan Kumar MV**
