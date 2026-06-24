# Changelog

## v2.0.0 — Production Hardening (2026-06-22)

A ground-up hardening pass over v1.3, driven directly by the findings in
[EVALUATION.md](./EVALUATION.md). Every item below maps to a specific
finding in that report.

### Added

- **STAFF assignment workflow** (the headline feature of this release): a
  STAFF or ADMIN user can self-assign an unassigned complaint
  (`POST /api/complaints/{id}/assign/me`) or be assigned to one by an admin
  (`POST /api/complaints/{id}/assign`, new Admin → Assignments page). Only
  the assignee or an admin can subsequently change that complaint's status.
  The STAFF role is now fully seeded, secured, and wired into the frontend
  (Sidebar links, dashboard stats, queue page) — previously it existed only
  as an unimplemented idea referenced by a frontend page that crashed on
  load.
- Complaint status lifecycle is now an enforced state machine
  (`common/ComplaintStatusPolicy`) — illegal transitions (e.g.
  REJECTED→SUBMITTED) are rejected with 409, not silently accepted.
- `Category` reference table replacing free-text categories; admin CRUD UI.
- Pagination (`PageResponse`) on every list endpoint.
- OpenAPI/Swagger UI (`/swagger-ui.html`).
- Flyway database migrations (`db/migration/`), replacing `ddl-auto=update`.
- Spring Boot Actuator + Micrometer/Prometheus metrics; container
  healthchecks wired to `/actuator/health`.
- Structured JSON logging in the `prod` profile + per-request correlation
  IDs (`X-Request-Id` header, `requestId` in every error response and log
  line).
- Async email notifications (complaint created / status changed /
  assigned) via Spring events — logged by default, real SMTP delivery
  behind a feature flag.
- Access + refresh JWT token pair; refresh token delivered as an HttpOnly
  cookie, never in a JSON body or localStorage.
- Token revocation via `User.tokenVersion` — logout actually invalidates
  outstanding tokens.
- Database-backed brute-force protection (account-level lockout wired into
  Spring Security's `UserDetails.isAccountNonLocked()`, plus an independent
  IP-level throttle) — correct on multi-instance deployments, unlike the
  prior in-memory map.
- `ComplaintVersion`/audit-history exposed via API (`GET
  /api/complaints/{id}/history`) and shown in the UI — previously built but
  never queryable.
- Mobile off-canvas navigation drawer (hamburger menu), replacing a CSS
  rule that hid the sidebar with no replacement on small screens.
- `:focus-visible` styling and `prefers-reduced-motion` support.
- Real test suite: state-machine unit tests, JWT unit tests, service-layer
  Mockito tests (auth, complaint lifecycle, assignment access control),
  a `@DataJpaTest` repository test, and a `@WebMvcTest` controller test.
- Docker (multi-stage builds, non-root users, healthchecks) for both
  services, `docker-compose.yml`, and a GitHub Actions CI workflow.
- `.gitignore`, `.editorconfig`, `.env.example`.
- `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, this changelog, and
  `EVALUATION.md` (the historical report itself, preserved for transparency).

### Fixed

- **`isStaffOrAdmin()` runtime crash** — `AuthContext` now actually defines
  `isStaff()`/`isStaffOrAdmin()`; `/queue` no longer throws a TypeError for
  every user.
- **`complaintsApi.selfAssign` was referenced but never defined** — now
  implemented end-to-end against a real backend endpoint.
- N+1 query patterns in admin reporting/user-listing replaced with single
  `GROUP BY` aggregate queries (`ComplaintStatsAggregator`).
- Missing database indexes added on every column the app actually filters
  or sorts by.
- `ComplaintVersion.complaintId` (an unconstrained `Long`) is now a real
  `@ManyToOne` foreign key — orphan version rows are no longer possible at
  the database level.
- X-Forwarded-For spoofing — only trusted from configured reverse-proxy
  IPs now (`HttpRequestUtils`, `security.trusted-proxies`).
- `AuditLog.ipAddress` / `userAgent` are now actually populated (previously
  defined but always `null`).
- Demo credentials are no longer logged in plaintext; `DataSeeder` is
  disabled entirely in the `prod` profile.
- `target/` build artifacts removed from version control; `.gitignore`
  added.
- Mixed CRLF/LF line endings normalised to LF project-wide;
  `.editorconfig` added to prevent recurrence.
- Duplicate `@ExceptionHandler` blocks removed from individual controllers
  — `GlobalExceptionHandler` is now the single source of truth, extended
  to also handle `OptimisticLockingFailureException` (409, previously fell
  through to a generic 500) and illegal state-transition errors.
- `AdminController`'s manual `requireAdmin()` checks replaced with
  declarative `@PreAuthorize`/URL-level role rules.
- Dead `DRAFT` status removed from the `Complaint.Status` enum (was
  unreachable in v1.3 — no code path ever set it).
- `AdminService` god-class (user management + reporting + analytics +
  timeline in one file) split into four focused services.

### Changed

- `GET /api/complaints` and `GET /api/admin/users` now return paginated
  envelopes (`{content, page, size, totalElements, totalPages, last}`)
  instead of unbounded raw arrays.
- `AdminUsers` role control is a 3-way select (USER/STAFF/ADMIN), not a
  binary promote/demote toggle.
- `ComplaintDetail`'s status-update panel is now shown to the assigned
  STAFF member, not just ADMIN.

### Known limitations (deferred, not hidden — see README.md "Roadmap")

Redis-backed rate limiting at extreme scale, full server-side search,
TypeScript migration, distributed tracing/APM, multi-tenancy, an SLA
engine, and full automated WCAG-AA testing were all evaluated and
deliberately deferred — see README.md for the reasoning on each.

---

## v1.3 — "Production Hardening" (2026-06-19, prior author)

Initial three-role complaint management system. See [EVALUATION.md](./EVALUATION.md)
for the complete state of this version — it is preserved in full rather
than summarised here, since it's the baseline every v2.0 change above is
measured against.
