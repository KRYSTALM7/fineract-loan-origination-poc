# Architecture Decision Record: External LOS Service

**ADR-001** | Status: Accepted | Date: 2026-05-01

## Context

Apache Fineract has no native Loan Origination System. The [FINERACT-2442](https://issues.apache.org/jira/browse/FINERACT-2442) ticket scopes a GSoC 2026 POC as an exploratory external service. This document records the key architectural decisions.

## Decision: Fully External Standalone Service

The LOS is implemented as a **separate Spring Boot microservice** with its own PostgreSQL schema. It communicates with Fineract **exclusively through published REST APIs**.

**Consequences:**
- Zero risk of collision with ongoing Fineract core development
- Fully demonstrable without modifying the Fineract main branch
- Clear upstream migration path once design is validated

## Multi-Tenancy Strategy

Every entity carries a `tenant_id` column. All repository queries are filtered by `tenantId` extracted from the `X-Fineract-Platform-TenantId` request header — matching Fineract's own convention exactly. Institution A and Institution B's data are never mixed.

## Security Model

| Layer | Mechanism |
|-------|-----------|
| Authentication | JWT Bearer token (Spring Security OAuth2 Resource Server) |
| Authorization | Role-based: `LOAN_OFFICER`, `BRANCH_MANAGER`, `CREDIT_COMMITTEE`, `APPLICANT` |
| Tenant isolation | `tenantId` enforced at repository layer on every query |
| Transport | HTTPS in production profile |

## Disbursement Bridge Fallback

`fineract.mock-enabled=true` in local/test profiles allows full flow demonstration without a live Fineract instance. The mock returns a deterministic loan ID derived from the application reference. Switching to real Fineract requires only changing one property — no code changes.

## Upstream Migration Path

1. POC validated → community review on `dev@fineract.apache.org`
2. Entities migrated into `fineract-loan-origination` placeholder module
3. REST contracts aligned with FINERACT-2418 once resolved
4. Submitted as PR to `apache/fineract`
