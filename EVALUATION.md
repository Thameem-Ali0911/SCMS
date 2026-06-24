# SCMS — v1.3 Evaluation Report (Historical)

> **This file is preserved for transparency.** It is the brutally-honest
> production-readiness evaluation that v1.3 of this codebase received,
> immediately before the v2.0 "Production Hardening" pass documented in
> [CHANGELOG.md](./CHANGELOG.md). Every "CHANGE in v2.0" comment scattered
> throughout the v2.0 source code references a specific finding in this
> report by name. Reading the two side by side — what was broken, and
> exactly how it was fixed — is the most honest way to evaluate whether the
> v2.0 claims hold up.
>
> Scores below describe v1.3 only. They do not describe the current
> codebase.

---

## PRELIMINARY OBSERVATIONS (before scoring)

Three things need to be said before the detailed scoring begins, because they colour every subsequent number.

1. **This is a college fresher's project.** That context matters. The absolute scores will be low on a professional scale — that is expected and honest.
2. **The codebase has genuine quality signals** that distinguish it from typical beginner work: clean layering, DTOs everywhere, soft-delete, audit logging, optimistic locking, a CSP header, rate-limiting. These are not accidents — someone thought carefully.
3. **There are production-breaking gaps** that are severe: zero tests, an in-memory rate-limiter that breaks in every real deployment, a STAFF role referenced in the frontend that does not exist in the database or backend, build artifacts committed to source control, `ddl-auto=update` in production config, and an N+1 query pattern that will kill performance under any real load.

Both realities are true simultaneously. The report will name both without softening either.

---

## SECTION 1 — DETAILED AREA SCORES

---

### 1. Business Problem Solving — 6/10

**What is good:**
The system correctly models a real-world complaint lifecycle (SUBMITTED → IN_REVIEW → IN_PROGRESS → RESOLVED/CLOSED/REJECTED) with six meaningful statuses. The three-role hierarchy (USER, STAFF, ADMIN) maps to how a real college helpdesk actually functions. Soft-delete preserves complaint history for traceability. The audit log + versioning combination correctly anticipates the regulatory requirement that complaint grievance systems cannot destroy history.

**What is bad:**
The problem statement says "Smart Complaint Management System" but there is nothing "smart" about it — no SLA tracking, no escalation policies, no notification system, no email/SMS alerts when a complaint changes status. A user files a complaint and has no idea when it was reviewed unless they manually refresh the page. The STAFF role exists in the frontend UI (StaffQueue page, `staff@scms.com` in the login demo box) but has zero backend implementation — no seeding in `DataSeeder`, no role check in `SecurityConfig`, no role check in `ComplaintController`. This is a half-implemented feature shipped to "production".

**Why it is bad:**
A complaint management system without notifications is like a ticketing system that never sends confirmation emails. The primary value proposition — "your complaint was received and here is its current status" — requires asynchronous communication. Without it, users must poll manually. The missing STAFF role is worse: any admin who tries to use the self-assign feature as STAFF will find it quietly routes them to `/dashboard` because `isStaffOrAdmin()` is not defined in `AuthContext.jsx` — the function reference exists in `App.jsx` but is never exported from the context.

**Production risk:**
`StaffRoute` calls `isStaffOrAdmin()` which is `undefined` in the context — this throws a JavaScript TypeError at runtime when any user navigates to `/queue`. The page silently crashes for every user role.

**Senior engineer improvement:**
Define the STAFF role completely in `DataSeeder`, add it to `SecurityConfig`, seed a `staff@scms.com` user, add `isStaff()` and `isStaffOrAdmin()` to `AuthContext`. Add async email via Spring Mail (`spring-boot-starter-mail`) with a `ComplaintEventPublisher` that fires `@EventListener` methods to send status-change emails.

**Trade-offs:**
Email adds complexity and infrastructure cost. For a college project it is reasonable to defer, but the demo data should not advertise features that crash.

---

### 2. Product Design — 5/10

**What is good:**
The CSS design system (global.css, 371 lines) is genuinely considered: CSS custom properties, a consistent colour palette, Inter + Plus Jakarta Sans fonts, defined radius and shadow scales. The sidebar navigation, dashboard KPI cards, and Recharts visualisations demonstrate visual product thinking. Toast notifications with the custom DOM event bus are a professional UX pattern.

**What is bad:**
No mobile responsiveness. The layout uses a fixed `shell` + `shell-main` structure that breaks at any viewport under ~900px. No `@media` queries exist in global.css for core layout — only individual component widths. There is no empty-state design for the complaint list when a new user has zero complaints. Accessibility is essentially absent — no ARIA labels, no keyboard navigation for custom components, no focus-visible styles, no screen-reader text on icon-only buttons.

**Why it is bad:**
In 2026, a web application that is not mobile-responsive is not a product, it is a desktop demo. College students access internal systems on phones constantly.

**Production risk:**
Any user on a tablet or phone gets a broken layout. Any disability audit fails immediately.

**Senior improvement:**
Add a CSS Grid or Flexbox responsive breakpoint at 768px for the `shell` layout. Add `aria-label` to all icon buttons. Test with keyboard navigation from end to end.

---

### 3. Requirements Engineering — 4/10

**What is good:**
The complaint lifecycle statuses are well-defined. The role hierarchy is documented in code comments. The README explains setup steps clearly.

**What is bad:**
The README is outdated — it still lists "Next steps" as building the ComplaintController, which was already built. There are no user stories, no acceptance criteria, no API contract (OpenAPI/Swagger), no ERD, no sequence diagrams. The DRAFT status exists in the `Complaint.Status` enum but is never set anywhere in the code — a status that cannot be reached is dead code. The requirement for "STAFF can manage complaint resolution" is specified in the `App.jsx` comment but has no backend implementation.

**Why it is bad:**
Without a requirements document, the next engineer joining the project has no way to distinguish "this is intentional" from "this was forgotten." The DRAFT status / STAFF role situation proves this — someone started implementing them and stopped, leaving both in an indeterminate state.

**Production risk:**
A QA tester finds `DRAFT` in the enum and writes test cases for a state that is unreachable. Junior engineers inherit confusion.

---

### 4. System Design — 5/10

**What is good:**
The overall architecture is correct for the problem: React SPA + Spring Boot REST API + MySQL, JWT stateless auth. The Vite proxy correctly eliminates CORS in development. The separation of concerns (Controller → Service → Repository) is properly implemented. The dual audit trail (structured `ComplaintVersion` + raw-JSON `AuditLog`) is an unusually sophisticated choice for a college project.

**What is bad:**
Single-node, no horizontal scaling considered. No caching layer (Redis). No message queue for async operations (email, notifications). No CDN for frontend static assets. No load balancer. Database is a single point of failure. The in-memory `LoginAttemptService` (ConcurrentHashMap) means rate-limiting state is per-process — restart the server and all lockouts vanish; add a second instance and rate-limiting is completely bypassed. The Vite proxy configuration means the production frontend and backend URL strategy is undefined — `baseURL: '/api'` works only when co-hosted or behind a reverse proxy, which is not documented.

**Why it is bad:**
The in-memory rate limiter is a specific category of architectural mistake: it looks like it works (it does work, on one node), gives false security, and fails silently in every real deployment pattern (Railway, Render, K8s — all multi-instance by default). This is not a performance concern, it is a correctness concern.

**Production risk:**
Two instances behind a load balancer = zero rate limiting. An attacker brute-forces the login endpoint from any IP, hitting different instances alternately, achieving unlimited attempts.

---

### 5. Software Architecture — 5.5/10

**What is good:**
Clean layered architecture strictly respected — controllers never touch repositories directly. DTOs completely shield the entity model from the API surface. `@ControllerAdvice` centralises exception handling (this is the right pattern). The Role entity as a separate table (not a Java enum with `@Enumerated`) correctly supports the Open/Closed Principle — add a new role by inserting a DB row. `@SQLRestriction` for soft-delete is a sophisticated Hibernate 6 choice that eliminates an entire class of "accidentally show deleted records" bugs.

**What is bad:**
`AdminService.getTopComplainants()` performs `userRepository.findAll()` then calls `complaintRepository.countBySubmittedBy(user)` inside a stream — this is an O(N) DB call pattern that fires N+3 SQL queries for N users. With 100 users it fires 100+ queries. With 1000 users it fires 1000+ queries. Similarly, `AdminService.getReportSummary()` calls `countByStatus()` seven separate times — seven queries where one `GROUP BY status` query would do. `AdminService.toUserResponse()` performs three separate count queries per user. The `AdminController` has local `@ExceptionHandler` methods duplicating what `GlobalExceptionHandler` already handles.

**Why it is bad:**
The N+1 problem is the single most common production performance crisis in JPA applications. The comment in `AdminService` acknowledges "for a college project, computing stats in Java is fine" — but the problem is not the Java computation, it is the N separate SQL queries. `GROUP BY` queries are database-side and are fast even for millions of rows.

**Production risk:**
`GET /api/admin/users` with 500 users fires 1500+ SQL queries per request. At 10 concurrent admin users, that is 15,000 queries per second against MySQL. The database dies.

**Senior improvement:**
Replace all per-user count loops with a single `@Query("SELECT u.id, COUNT(c) FROM User u LEFT JOIN Complaint c ON c.submittedBy = u GROUP BY u.id")` JPQL projection. Replace the seven `countByStatus` calls with one native query using `GROUP BY status`.

---

### 6. Backend Engineering — 6/10

**What is good:**
Spring Boot 3.2 / Jakarta EE 10 — current. BCrypt cost 12 — correct. `@Valid` on all request bodies — correct. `@Transactional` with proper atomicity across multi-table writes — correct. `@PrePersist`/`@PreUpdate` for timestamp automation — correct. Lombok usage is appropriate and not abused. `@RequiredArgsConstructor` instead of field injection — correct (constructor injection is testable). The `ObjectMapper` injection into `ComplaintService` for JSON serialisation in the audit log is the right pattern.

**What is bad:**
Zero tests. Not a single `@SpringBootTest`, `@WebMvcTest`, or `@DataJpaTest`. Not one JUnit method. The application cannot be validated for regressions. The `UserRepository.count()` call inside `ComplaintController.stats()` is a direct repository call from the controller — the controller bypasses the service layer for this specific operation. `spring.jpa.hibernate.ddl-auto=update` is present in the main `application.properties` with a comment saying "switch to validate before demo" — but the file deployed is the same one with `update` still in it. `ddl-auto=update` in production is a data corruption risk.

**Why it is bad:**
`ddl-auto=update` will silently alter tables if entity fields change. If a column is renamed, Hibernate adds the new column but leaves the old one — you now have orphan columns accumulating in production. The `count()` call in the controller is a symptom: the abstraction discipline broke at one point, and it will break at more points without tests enforcing it.

**Production risk:**
Any entity field rename + deployment = table alteration without a migration. If the alteration fails mid-process (e.g., timeout on a large table), the table is in an indeterminate state.

---

### 7. Frontend Engineering — 6/10

**What is good:**
React 18 with functional components and hooks throughout — no class components. `useCallback` on data-fetch functions to prevent infinite loops in `useEffect` — correct. The custom `useToast()` hook is clean. `ErrorBoundary` wrapping the entire app is a professional production pattern. The `axios.js` interceptor correctly handles 401 → auto-logout and 429 → rate-limit message without requiring every component to handle it.

**What is bad:**
No TypeScript. All API response shapes are untyped — a single backend field rename silently breaks the UI with no compile-time error. No state management library — complex state is duplicated across pages (each page re-fetches everything on mount, no shared cache). No React Query / SWR — every page has the same boilerplate `useState(loading)` + `useState(error)` + `useEffect(() => fetch().then().catch().finally())` pattern repeated in every single page component. No unit tests. No integration tests. No Storybook. The `StaffRoute` component in `App.jsx` calls `isStaffOrAdmin()` which is not exported from `AuthContext` — this is a runtime crash.

**Why it is bad:**
The missing `isStaffOrAdmin()` function is the most critical bug in the entire codebase — it is a guaranteed runtime TypeError for any user navigating to `/queue`. The `StaffRoute` component destructures a function that doesn't exist from the context object, and JavaScript will throw `TypeError: isStaffOrAdmin is not a function` the moment it is called.

**Production risk:**
`/queue` crashes for all users. Any senior engineer looking at this code during a PR review would block it immediately.

---

### 8. API Design — 6.5/10

**What is good:**
RESTful URL design is correct: `PATCH /api/complaints/{id}/status` for partial update (not PUT), `DELETE` for soft-delete, nested resource naming (`/api/admin/users/{id}/role`). Consistent JSON error envelope (`{timestamp, status, error, message, path, fields}`). Correct HTTP status codes (201 for creation, 204 for delete, 429 for rate limit). The Vite proxy correctly unifies the dev API surface.

**What is bad:**
No API versioning (no `/api/v1/`). No OpenAPI/Swagger documentation — there is no way for a frontend developer to know what the API accepts without reading the Java source. No pagination on any list endpoint — `GET /api/complaints` returns the entire table as an array. No filtering parameters on the list endpoint. `GET /api/complaints/stats` and `GET /api/complaints/{id}` share the same `/api/complaints/*` path pattern — the `stats` endpoint will be shadowed if Spring routes `{id}` before `stats` (Spring handles this correctly, but it is fragile and non-obvious).

**Why it is bad:**
Without pagination, `GET /api/complaints` will return 50,000 rows as a JSON array to a browser that was expecting 20 rows for the first page. The browser will freeze. No API versioning means any breaking change requires a coordinated client+server deploy with zero backwards compatibility.

**Senior improvement:**
Add `Pageable` to all list queries. Add `springdoc-openapi-starter-webmvc-ui` — 5 lines in pom.xml gives you full Swagger UI. Add `/api/v1/` prefix.

---

### 9. Database Design — 6/10

**What is good:**
The schema is normalised: `users`, `roles`, `user_roles` (join table), `complaints`, `complaint_versions`, `audit_logs`. Soft-delete on complaints. `@Version` for optimistic locking. `EnumType.STRING` for status columns — correct. Email uniqueness constraint. Appropriate column lengths. The dual-table audit strategy (structured versions + raw JSON logs) is well-designed.

**What is bad:**
No database indexes defined anywhere — not a single `@Index` annotation on any `@Table`. The `complaints` table will have no index on `submitted_by`, `assigned_to`, `status`, or `submitted_at`. Every query that filters on these columns does a full table scan. `category` is a free-text varchar with no foreign key to a category table — categories are inconsistent strings (case sensitivity, typos). No database migration tool (Flyway/Liquibase) — the only migration strategy is `ddl-auto=update`. `ComplaintVersion` uses `complaintId` as a plain `Long` column instead of a `@ManyToOne` FK with referential integrity — you can insert an orphan version record for a complaint that doesn't exist. All timestamps use Java's `LocalDateTime` without timezone awareness — this is fine for a single-timezone college project but breaks in any multi-region deployment.

**Why it is bad:**
The missing index on `submitted_by` is a direct N+1 multiplier. Every `findBySubmittedBy()` call does a full `complaints` table scan. With 10,000 complaints, every student dashboard load scans 10,000 rows.

**Senior improvement:**
```java
@Table(name = "complaints", indexes = {
    @Index(name = "idx_complaints_submitted_by", columnList = "submitted_by"),
    @Index(name = "idx_complaints_status", columnList = "status"),
    @Index(name = "idx_complaints_submitted_at", columnList = "submitted_at")
})
```
Add Flyway. Create a `categories` reference table with a FK from `complaints.category_id`.

---

### 10. Security — 6/10

**What is good:**
BCrypt cost 12 — correct and well-justified. JWT with HS512 — secure algorithm. Secret from environment variable — correct (no hardcoded secrets in git). CSP header with `frame-ancestors 'none'` — prevents clickjacking and iframe embedding. `X-Content-Type-Options: nosniff` — correct. Referrer-Policy — correct. CORS restricted to configured origin — correct. Rate-limiting on login endpoint with 5-attempt lockout — present.

**What is bad:**
`X-Forwarded-For` is trusted blindly without validating the proxy chain. Any attacker can send `X-Forwarded-For: 1.2.3.4` directly to your Spring Boot port and bypass IP-based rate limiting entirely — the lock will apply to `1.2.3.4` (the forged IP) while the attacker's real IP is never tracked. HSTS is commented out — so the app runs over HTTP with zero transport security in production. JWT tokens are stored in `localStorage` — vulnerable to XSS token theft (the code acknowledges this but ships it anyway). No CSRF protection documentation explaining the threat model to future maintainers. No JWT token blacklisting / revocation — a deactivated user's existing JWT is valid until it expires (24 hours). The `DataSeeder` logs plaintext demo passwords to the application log (`log.info("Seeded admin: admin@scms.com / Admin@1234")`). Default credentials `Admin@1234` / `User@1234` are predictable and hardcoded.

**Why it is bad:**
The X-Forwarded-For spoofing vulnerability completely neutralises the rate-limiting feature. An attacker who knows the API is exposed directly (bypassing Nginx) can forge any IP. The 24-hour JWT window means deactivating a user's account does not immediately block them — they can continue to operate for up to 24 hours. Logging passwords (even demo passwords) to log files is a security anti-pattern that bleeds into log aggregators (ELK, CloudWatch, Grafana Loki).

**Production risk:**
Brute force attack using X-Forwarded-For spoofing bypasses rate limiting completely. Compromised JWT has no revocation mechanism. Deactivated users retain access.

**Senior improvement:**
Use Spring Security's `ForwardedHeaderFilter` with `server.forward-headers-strategy=framework` in application.properties to only trust X-Forwarded-For from configured trusted proxies. Add a JWT blacklist (Redis set of invalidated JTI claims). Remove password logging. Use `@ConditionalOnProperty` to disable DataSeeder in production profiles.

---

### 11. Authentication — 6.5/10

**What is good:**
Email-as-username pattern is correct for a college system. BCrypt 12 rounds. JWT HS512. Token expiry (24h default, configurable). Login attempt tracking with lockout. `UserDetailsService` implementation correctly hooks into Spring Security. `isAccountNonLocked()` and `isEnabled()` correctly wired to block deactivated accounts. The axios interceptor auto-logout on 401 is a complete, correct implementation.

**What is bad:**
`isAccountNonLocked()` always returns `true` in the `User` entity — the in-memory lockout (`LoginAttemptService`) is completely disconnected from `UserDetails.isAccountNonLocked()`. This means Spring Security never sees the locked state — the lockout is implemented as a pre-check in `AuthService.login()` rather than the standard Spring Security mechanism. If the order of checks in `AuthService` is changed, the lockout silently disappears. No password reset flow. No email verification. No token refresh mechanism — when a 24-hour token expires, the user is hard-logged out with no silent refresh option. No "remember me" distinction.

**Why it is bad:**
The `isAccountNonLocked()` always returning `true` means if someone finds a way to call `authManager.authenticate()` directly (bypassing the `AuthService.login()` pre-check), the lockout is completely bypassed. The correct pattern is to have `LoginAttemptService` update the `User.locked` field in the database so Spring Security enforces it.

---

### 12. Authorization — 6/10

**What is good:**
Defense-in-depth: role checks at both the controller level and the service level for ownership verification. `@AuthenticationPrincipal` correctly extracts the authenticated user from the Security Context — no trust of client-sent user IDs. RBAC implemented via `GrantedAuthority`. Users cannot access other users' complaints — the ownership check in `ComplaintService.getComplaint()` is correct.

**What is bad:**
The `AdminController` ignores Spring Security's `.hasRole("ADMIN")` DSL and implements role checking manually in every method with `requireAdmin(actor)`. This is more verbose and error-prone than declarative security. STAFF role is not implemented in any Spring Security rule. The `SecurityConfig` grants all authenticated requests to all endpoints — there is no URL-level role enforcement at all (only method-level). The authorization check in `ComplaintController.updateStatus()` returns an empty `403` body (`ResponseEntity.status(HttpStatus.FORBIDDEN).build()`) without a message — inconsistent with the error envelope used everywhere else. The admin role check uses string comparison (`"ROLE_ADMIN"`) instead of Spring Security's `hasAuthority()` / `hasRole()` utilities.

**Senior improvement:**
Use `@PreAuthorize("hasRole('ADMIN')")` on all admin-only methods. Add `.hasRole("ADMIN")` to `/api/admin/**` in `SecurityConfig`. Define STAFF role and its permissions explicitly at the URL level.

---

### 13. Performance — 4/10

**What is good:**
HikariCP connection pool configured. `FetchType.LAZY` on complaint relationships (correct). The `@SQLRestriction` approach avoids manual soft-delete filtering (one SQL condition vs. filtering in code).

**What is bad:**
The N+1 query catastrophe in `AdminService` is the dominant problem. `getTopComplainants()` fires `3N+1` queries for N users. `listAllUsers()` fires `3N+1` queries. `getReportSummary()` fires 9 separate count queries plus 2 findAll queries plus 1 more findAll inside `activeUsers` streaming — that is 12+ queries for a single API call. No caching at any level (no `@Cacheable`, no Redis). All list endpoints return full entity graphs — no pagination, no projection, no DTO-level query. `complaintRepository.findAllByOrderBySubmittedAtDesc()` in the category breakdown loads every complaint into JVM memory. No database query planning has been done. No `EXPLAIN` queries mentioned. No missing index analysis.

**Why it is bad:**
A single admin pageload of `/api/admin/users` with 200 users issues 600 SQL queries. The `GET /api/admin/reports/summary` endpoint issues 12 queries including a full table scan of all complaints and all users. These are not performance problems — they are correctness problems that masquerade as performance.

**Production risk:**
With 500 users and 5000 complaints, the reports page takes 10-30 seconds to load and causes database connection pool exhaustion under any concurrent load.

---

### 14. Scalability — 3/10

**What is good:**
Stateless JWT means horizontal scaling of the application tier is theoretically possible. HikariCP pool is configured. The database schema does not rely on JVM-level state.

**What is bad:**
The in-memory `LoginAttemptService` means multiple instances break rate-limiting. No Redis. No message queue. No CDN. No horizontal pod autoscaling consideration. No read replica. No database connection pool sizing for horizontal scale. Full-table queries on every admin endpoint. `spring.datasource.hikari.maximum-pool-size=10` — with 3 instances this is 30 connections against MySQL's default of 151. No response caching. No ETags. No conditional GET support.

**Production risk at user levels:**

| Users | Risk |
|-------|------|
| **100** | Works. Slow admin reports (2-3s). No visible crashes. |
| **1,000** | Admin reports time out under concurrent load. N+1 queries saturate DB connections. Login rate-limiting bypassed on any 2-node deploy. |
| **10,000** | DB connection pool exhaustion. `findAllByOrderBySubmittedAtDesc()` loads 50,000+ rows into JVM memory. JVM OutOfMemoryError on report endpoints. |
| **100,000** | System completely non-functional. DB dead. No horizontal scaling possible due to stateful rate-limiter. Frontend serves no cached assets. |

---

### 15. Reliability — 4/10

**What is good:**
`@Transactional` on multi-step writes — atomicity for complaint creation (saves 3 tables atomically). `@Version` optimistic locking prevents concurrent overwrites. Soft-delete means no data is permanently lost by accident. `ErrorBoundary` on the frontend prevents full app crashes from component errors.

**What is bad:**
No circuit breaker. No retry logic. No health check endpoint (`/actuator/health` requires Spring Actuator, not added). No graceful shutdown configuration. `LoginAttemptService.cache.clear()` on capacity — this is not graceful eviction, it is a full cache wipe that resets all lockouts simultaneously (a DoS vector — spam 10,001 failed login attempts to reset all lockouts). No database connection retry configuration. The `complaintRepository.findAllByOrderBySubmittedAtDesc()` with no timeout means a slow query blocks the thread indefinitely. No async operations — a slow email (hypothetical) would block the HTTP thread.

---

### 16. Maintainability — 5.5/10

**What is good:**
The layered architecture makes the codebase navigable. The `MENTOR NOTE` comments throughout are genuinely excellent documentation of design decisions — this is better than most professional codebases. DTOs are grouped logically in nested static classes. Package structure is correct and consistent. Lombok reduces boilerplate without overuse.

**What is bad:**
`AdminService.java` is 15,971 bytes — a single class doing user management AND report generation AND analytics AND timeline — four distinct responsibilities in one class. `AdminReports.jsx` is 21,679 bytes — a monolithic page component with zero component extraction. Duplicated `@ExceptionHandler` blocks in `AdminController` and `ComplaintController` despite `GlobalExceptionHandler` supposedly centralising them. The `toUserResponse()` private method in `AdminService` performs 3 database queries — embedding side effects inside a mapping function is a maintainability trap. There is no interface for `AdminService` or `ComplaintService` — programming to concrete types makes mocking in tests harder. Magic string literals everywhere (`"ROLE_ADMIN"`, `"COMPLAINT"`, `"STATUS_CHANGE"`, `"USER"`) with no constants class.

**Senior improvement:**
Extract `ReportService` from `AdminService`. Extract `UserManagementService`. Create constants: `Roles.ADMIN = "ROLE_ADMIN"`. Extract `UserResponseMapper` from `AdminService`.

---

### 17. Testability — 1/10

**What is good:**
`@RequiredArgsConstructor` (constructor injection) is used everywhere — this is the correct pattern for testability. The layered architecture in principle supports unit testing of each layer independently.

**What is bad:**
There are **zero tests**. Not one. No unit tests, no integration tests, no slice tests, no end-to-end tests. The `spring-boot-starter-test` dependency is in pom.xml but is completely unused. There is no `src/test/java` directory with any content. There is no Mockito usage. There is no `@SpringBootTest`. There is no `@WebMvcTest`. There is no `@DataJpaTest`. There is no frontend testing (`jest`, `vitest`, `@testing-library/react`). There is no Postman collection. There is no test data strategy.

**Why it is bad:**
You cannot refactor, scale, or deploy this system with confidence. Every change is a manual test by a developer. Every bug fix risks introducing three new ones. This is the most expensive single technical debt item in the entire codebase.

**Production risk:**
Any change to `ComplaintService` or `AdminService` is a production gamble.

---

### 18. Code Quality — 6/10

**What is good:**
Consistent code formatting throughout the Java code. Meaningful variable names (`actor`, `performedBy`, `submittedBy`). No dead code paths (except `DRAFT` status). The `toJson()` helper in `ComplaintService` for safe ObjectMapper usage is clean. The `buildKey()` method in `LoginAttemptService` is correctly extracted. Java records for `AttemptRecord` in `LoginAttemptService` — modern Java 16+ usage.

**What is bad:**
`AdminController.java` has CRLF line endings (`\r\n`) while `ComplaintController.java` has LF (`\n`) — mixed line endings in the same project (this is visible in the raw file content). Magic strings scattered everywhere. `getDashboardStats()` in `ComplaintService` takes a `long totalUsers` parameter passed from the controller — the controller is computing a value that belongs in the service. The `recordVersion()` method in `ComplaintService` has 7 parameters — a parameter object (VersionRecord DTO) would be cleaner. `AdminService.getReportSummary()` is 60 lines long with 12 local variables — a builder method of this length should be split.

---

### 19. Design Patterns — 6.5/10

**What is good:**
Repository pattern — correctly implemented via Spring Data JPA interfaces. DTO pattern — consistent and complete. Builder pattern — Lombok `@Builder` used appropriately on all DTO and entity classes. Factory pattern — `AuthService.buildResponse()` acts as a factory for `AuthResponse`. Observer-lite pattern — the custom DOM event bus (`scms:toast`) in the frontend. Strategy-lite — the role-based branching in `getDashboardStats()`.

**What is bad:**
No use of Spring's `@EventListener` — status change notifications should be events, not synchronous method calls inside `@Transactional`. No Command pattern for the status update workflow — the status machine transitions are not enforced (you can go from REJECTED → SUBMITTED which makes no business sense). No specification/query object pattern for the growing number of filtered queries. No use of `Optional` consistently — the code mixes `Optional.orElseThrow()` with null checks.

---

### 20. Clean Code Principles — 5.5/10

**What is good:**
Single-level abstraction in most controller methods — controllers delegate everything. Expressive method names (`findOrThrow`, `recordVersion`, `recordAudit`, `buildKey`). No commented-out production code (only commented-out future features with explanation). File sizes are reasonable for most files.

**What is bad:**
`AdminService.getReportSummary()` violates single responsibility — it computes 6 different categories of metrics in one method. `ComplaintService.getDashboardStats()` takes 3 parameters including a `boolean isAdmin` flag — boolean parameters are a clean code smell (use polymorphism or two methods). `AdminService.listAllUsers()` calls `Comparator.nullsLast(Comparator.reverseOrder())` — `createdAt` is set by `@PrePersist` and can never be null in practice, making `nullsLast` misleading. The `for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1))` loop in `getDailyTimeline()` is imperative in a codebase that is otherwise using streams — inconsistent style.

---

### 21. SOLID Principles — 5/10

**What is good:**
Open/Closed: Role stored in DB table (correct). Single Responsibility: `AuthService` handles auth, `ComplaintService` handles complaints — at the service level. Dependency Inversion: Constructor injection, `UserDetailsService` interface. Liskov Substitution: `User implements UserDetails` correctly.

**What is bad:**
Interface Segregation violation: `User implements UserDetails` combines the domain entity with Spring Security's interface — the User entity is now coupled to the Spring Security framework. Single Responsibility violation: `AdminService` does user management AND reporting. Single Responsibility violation: `ComplaintService.getDashboardStats()` serves two different use-cases with a boolean flag. Open/Closed violation: every new role requires code changes to `isAdmin()` checks scattered throughout the codebase instead of using Spring's declarative `@PreAuthorize`.

---

### 22. Error Handling — 6.5/10

**What is good:**
`GlobalExceptionHandler` is the correct centralised approach. Consistent error envelope. Correct HTTP status codes for each error type (400, 401, 403, 404, 429, 500). Frontend axios interceptor handles 401, 403, 429 globally. `ErrorBoundary` catches React runtime errors. The login page correctly distinguishes lockout errors from credential errors with different UI treatment.

**What is bad:**
The `catch (Exception e)` in `toJson()` silently swallows Jackson serialization errors and returns `"{}"` — this means a corrupted audit log entry is silently written without any alert. `AdminController`'s local `@ExceptionHandler` blocks are redundant with `GlobalExceptionHandler` — these handle some exceptions twice (whichever fires first wins, but it's confusing and inconsistent). Frontend error states on `AdminReports.jsx` show a generic "Failed to load report data" with no retry mechanism. The `Promise.all()` in `AdminReports.jsx` — if any one of 5 parallel requests fails, the entire dashboard shows an error even if 4/5 loaded successfully.

---

### 23. Logging — 5/10

**What is good:**
SLF4J + Logback via Spring Boot. `@Slf4j` Lombok annotation correctly used. `log.info()` for business events (login, complaint creation, status changes). `log.warn()` for security events (JWT failure, rate limiting). `log.error()` for unhandled exceptions.

**What is bad:**
`DataSeeder` logs `"Seeded admin: admin@scms.com / Admin@1234"` — credential values in log output, even for demo accounts. No structured logging (MDC, JSON format) — logs are unstructured plaintext, impossible to query in ELK/CloudWatch. No correlation ID / request ID — cannot trace a single request across multiple log lines. `logging.level.com.scms=DEBUG` in the production config file — DEBUG level floods production logs with internal state. No log rotation configuration. No log aggregation setup. The `logger.warn("JWT filter skipped: " + e.getMessage())` in `JwtAuthFilter` uses string concatenation instead of the `{}` placeholder pattern — this evaluates the string even when WARN is disabled, a performance anti-pattern for logging.

---

### 24. Monitoring — 1/10

**What is good:**
Nothing. There is no monitoring.

**What is bad:**
Spring Boot Actuator is not in pom.xml. There is no `/actuator/health` endpoint. There is no `/actuator/metrics`. There is no Prometheus scrape endpoint. There is no Micrometer. There is no Grafana dashboard. There is no alerting. There is no uptime check. There is no APM (no Datadog, New Relic, Elastic APM agent). The application can be down and no one will know until a user complains.

**Production risk:**
This is an absolute blocker for any production deployment. You deploy blind.

---

### 25. Auditability — 7/10

**What is good:**
This is the strongest area in the codebase. The dual audit trail (structured `complaint_versions` + raw-JSON `audit_logs`) is a genuinely sophisticated design. Every complaint creation, status change, and deletion writes to both tables atomically. The `AuditLog.ipAddress` field exists (though not populated — more on that below). The version number tracking in `ComplaintVersion` allows point-in-time complaint state reconstruction.

**What is bad:**
`AuditLog.ipAddress` is defined in the entity but is never set anywhere — it is always `null`. Same for `userAgent`. The audit log is never queried through any API — there is no admin UI to view audit history. `recordAudit()` in `ComplaintService` hardcodes string literals for `action` and `entityType` — if someone typos `"STAUS_CHANGE"`, there is no compile-time check. Login events are not audited — you can see complaint history but not who logged in from where. User role changes write to the application log but not to `audit_logs`.

---

### 26. User Experience — 5/10

**What is good:**
Toast notifications for all actions. Loading spinners on data fetch. Empty state handling in some pages. The lockout warning with remaining attempts countdown is a professional UX touch. The axios interceptor auto-logout with session-expired message is polished.

**What is bad:**
No mobile layout. No dark mode. No loading skeleton screens (just a spinner). No optimistic UI updates (every action requires a server round-trip before the UI updates). The `StaffQueue` page crashes for all users (the `isStaffOrAdmin` bug). No search functionality on the complaints list page for regular users. No date range filtering on any list. No "bulk actions" (mark multiple complaints as resolved). No keyboard shortcuts. No confirmation dialogs for destructive actions (delete complaint, deactivate user).

---

### 27. Accessibility — 2/10

**What is good:**
Semantic HTML (`<button>`, `<nav>`, `<main>`) is partially used.

**What is bad:**
No ARIA labels on any interactive element. No `aria-live` regions for dynamic content (toasts, loading states). No focus management after route changes. No visible focus indicators (`:focus-visible` styles absent). SVG icons (inline, custom) have no `aria-hidden="true"` and no `title` elements — screen readers will attempt to announce them as unlabelled graphics. Form inputs have labels visually but no `htmlFor`/`id` associations verified (some may be missing). No keyboard trap handling in modals. No skip-to-content link. WCAG AA compliance: near zero.

---

### 28. DevOps — 1/10

**What is good:**
The application.properties uses environment variables with defaults — this is the first step toward 12-factor app compliance.

**What is bad:**
No Dockerfile. No docker-compose.yml. No `.gitignore` (the `target/` build artifacts are committed — `.class` files, `.lst` files all in the zip). No CI/CD configuration (no GitHub Actions, no Jenkins, no GitLab CI). No environment profiles (`application-dev.properties`, `application-prod.properties`). No `application-test.properties`. No secrets manager integration. No database migration strategy. No health check endpoint for container orchestration. The `target/` directory being committed means compiled Java bytecode is in version control — every developer clones a stale build.

---

### 29. Deployment — 2/10

**What is good:**
The README mentions Railway, Render, EC2 as deployment targets. Environment variable usage is deployment-platform friendly.

**What is bad:**
No Dockerfile. No deployment scripts. No production build instructions. The `vite.config.js` proxy is development-only — in production the React build produces static files that need a web server (Nginx, Vercel) and the backend needs a separate domain/port. The production CORS strategy is documented (`CORS_ORIGINS` env var) but the production frontend serving strategy is completely undocumented. `ddl-auto=update` is production-dangerous. HTTPS is not configured. HSTS is commented out. There is no process manager (systemd, PM2, Supervisor) configuration. No rolling deploy strategy. No rollback plan.

---

### 30. CI/CD — 0/10

**What is good:**
Nothing. There is no CI/CD.

**What is bad:**
No GitHub Actions workflow. No automated build. No automated test execution (though there are no tests to execute). No linting. No build artifact versioning. No Docker image build. No deployment pipeline. No environment promotion strategy (dev → staging → prod). The absence of CI/CD means every deployment is a manual SSH + `mvn package` + copy operation, with all the human error that implies.

---

### 31. Observability — 1/10

**What is good:**
Application logs exist.

**What is bad:**
No distributed tracing (no Zipkin, no Jaeger, no OpenTelemetry). No metrics endpoint. No APM agent. No log aggregation. No request ID propagation. No error rate tracking. No latency histograms. No database query time tracking. No JVM heap metrics. When something breaks in production, the only diagnostic tool is grepping unstructured log files.

---

### 32. Documentation — 5/10

**What is good:**
The `MENTOR NOTE` comments throughout the Java codebase are genuinely excellent — they explain the "why" not just the "what." The README covers local setup clearly. The application.properties is heavily commented.

**What is bad:**
The README is outdated (says "next steps" for features already built). No OpenAPI/Swagger. No architecture decision records (ADRs). No ERD. No sequence diagrams for auth flow or complaint lifecycle. No deployment guide. No runbook for common operational tasks (add a user manually, reset a locked account, recover from DB failure). No CHANGELOG. No API contract beyond reading the source code.

---

### 33. Team Readiness — 3/10

**What is good:**
The code is readable enough for a single developer to navigate.

**What is bad:**
No onboarding documentation. No development environment setup script. No `Makefile`. No `.env.example` file. No code style guide. No branch strategy documentation. No PR template. No issue template. Mixed line endings (CRLF vs LF) across files — the first team conflict. No linter configuration (no Checkstyle, no ESLint, no Prettier config). A second developer joining this project would need several hours just to understand the STAFF role situation.

---

### 34. Enterprise Readiness — 2/10

**What is good:**
The dual audit trail is an enterprise-grade feature. DTOs are an enterprise-grade practice.

**What is bad:**
No LDAP/SSO/SAML integration. No multi-tenancy. No tenant isolation. No rate-limiting per API key. No API key management. No SLA tracking. No SLO/SLI definitions. No disaster recovery plan. No data backup strategy. No data retention policy. No GDPR compliance (no right-to-erasure endpoint — soft delete is insufficient for GDPR, which requires actual erasure). No penetration test. No dependency vulnerability scan (no OWASP Dependency Check, no Snyk). No supply chain security. Not production-ready at any enterprise scale.

---

### 35. Production Readiness — 2/10

**What is good:**
The application starts and the core happy paths work.

**What is bad:**
Every item under DevOps, CI/CD, Monitoring, and Observability is missing. `ddl-auto=update` in production. Build artifacts committed. STAFF role feature crashes. No tests. No health check. No HTTPS. No log aggregation. Zero operational tooling.

---

## SECTION 2 — SUMMARY SCORES

| Metric | Score | Commentary |
|---|---|---|
| **Overall Score** | **4.8/10** | Strong junior fundamentals; numerous production gaps |
| **Production Readiness** | **2/10** | Cannot deploy as-is |
| **Resume Value (fresher)** | **8/10** | Impressive for a college project |
| **Interview Value** | **7/10** | Good talking points; expect hard questions on the gaps |
| **Hiring Attractiveness** | **7.5/10** | Shows thinking above average for fresher level |

---

## SECTION 3 — FINAL VERDICT (original)

This is the best college fresher project I have reviewed this year. The architectural awareness — layered architecture, DTOs, soft-delete, dual audit trail, optimistic locking, BCrypt 12, CSP headers — is genuinely above average for a student. Most bootcamp graduates do not understand why these patterns exist. You clearly do.

That said: the delta between "architecturally aware college project" and "production software" is enormous. The zero-test situation alone is an absolute blocker. The N+1 query problem will be your first production incident. The STAFF role crash is embarrassing.

**The honest truth:** If you walked into a senior Java interview tomorrow with this code on your laptop, opened it, and were asked to walk through it — you would be able to defend every architectural decision you made, and that is genuinely impressive. The interviewer would then ask you to write a test for `ComplaintService.createComplaint()`, and you would not be able to. That is where the conversation ends.

Write the tests. Fix the blockers. Ship it as a portfolio project. Then apply.

---

*Report generated: 2026-06-19*
*Reviewer: Principal Software Engineer / Architect perspective*
*Codebase: SCMS v1.3, Spring Boot 3.2, React 18, MySQL 8*

*See [CHANGELOG.md](./CHANGELOG.md) for what v2.0 did about every finding above.*
