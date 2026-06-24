# Architecture

## Overview

```
                    ┌─────────────────────┐
   Browser  ───────▶│  Nginx (frontend)   │── static React build
                    └──────────┬──────────┘
                               │ /api/* reverse proxy
                    ┌──────────▼──────────┐
                    │  Spring Boot API    │── stateless, JWT-authenticated
                    └──────────┬──────────┘
                               │ JDBC
                    ┌──────────▼──────────┐
                    │      MySQL 8        │
                    └─────────────────────┘
```

Layering inside the backend, strictly one-directional:

```
Controller → Service → Repository → Database
```

Controllers never touch repositories directly. DTOs (`dto/`) are the only
shape that crosses the HTTP boundary — entities (`model/`) never leak into
a JSON response or request body.

## Module map

| Package | Responsibility |
|---|---|
| `controller` | HTTP request/response mapping, validation triggers, role gating |
| `service` | Business logic, transactions, access-control rules |
| `repository` | Spring Data JPA interfaces — the only place SQL/JPQL lives |
| `model` | JPA entities |
| `dto` | API request/response shapes |
| `security` | JWT issuance/validation, the auth filter, request-ID correlation |
| `common` | Cross-cutting, dependency-free utilities (constants, the status state machine, IP extraction) |
| `event` / `notification` | Decoupled, asynchronous side effects (email) |
| `config` | Spring `@Configuration` classes (security, async, exception handling, demo data) |

## Why services were split (AdminService → 4 services)

The original `AdminService` did user management, reporting, analytics, and
timeline generation in one ~16KB class — four responsibilities, hard to
test in isolation, hard to navigate. It's now:

- `AdminUserService` — user CRUD, role/status changes
- `AdminReportService` — all reporting/analytics aggregate queries
- `AdminAssignmentService` — staff workload visibility for the assignment UI
- `CategoryService` — category reference-table CRUD

Each is independently testable and has one reason to change.

## The N+1 fix, structurally

`ComplaintStatsAggregator` exists because TWO different call sites
(`AdminUserService.listAllUsers()` and `AdminReportService.getTopComplainants()`)
needed the exact same shape of data: "per-user complaint counts, broken
down by open/resolved/total." Rather than fix the N+1 pattern twice (and
risk re-introducing it in one place while fixing the other), one component
computes it once via a single `GROUP BY` query
(`ComplaintRepository.countPerUserGroupedByStatus()`), and both services
share it.

## Status lifecycle

```
SUBMITTED ──► IN_REVIEW ──► IN_PROGRESS ──► RESOLVED ──► CLOSED
    │              │              │
    └──────────────┴──────────────┴────────► REJECTED ──► CLOSED
```

Enforced by `common/ComplaintStatusPolicy.java`. STAFF must follow the
graph; ADMIN may override any transition except leaving `CLOSED` (which is
permanently terminal, protecting historical integrity even from admin
mistakes).

## Assignment workflow

```
Complaint created (SUBMITTED, unassigned)
        │
        ├─ STAFF self-assigns from /queue/unassigned ──► assignedTo=staff, status→IN_REVIEW
        │
        └─ ADMIN assigns from /admin/assignments ──► assignedTo=chosen staff, status→IN_REVIEW

Once assigned: only that assignee (or any ADMIN) can change status further.
```

## Why audit/version writes are synchronous but email is async

`ComplaintVersion` and `AuditLog` writes happen inside the SAME
`@Transactional` boundary as the complaint mutation itself — a grievance
system's audit trail needs to be exactly as durable as the record it
describes; if the audit write fails, the whole operation should roll back.

Email notifications are published as Spring application events
(`event/`) and handled by `NotificationListener` via
`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` — they only
fire after the transaction has definitely committed, and a slow/unreachable
SMTP server can never add latency to the original HTTP request or roll
back a complaint creation.

## Data model

See `backend/src/main/resources/db/migration/V1__baseline_schema.sql` for
the authoritative schema (Flyway-versioned, not Hibernate-generated).
Key relationships:

- `complaints.category_id → categories.id` (was free-text in the prior
  version)
- `complaints.submitted_by`, `complaints.assigned_to → users.id`
- `complaint_versions.complaint_id → complaints.id` (FK — was an
  unconstrained bare `Long` previously, allowing orphan rows)
- `user_roles` join table — roles are data (a table), not a hardcoded Java
  enum, so adding a new role is a migration, not a code change
