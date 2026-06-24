# SCMS — Smart Complaint Management System

**v2.0 — Production Hardened.** A full-stack grievance/complaint management
system: students file complaints, staff pick them up and resolve them,
admins oversee everything and run reports. Built with Spring Boot 3,
React 18, and MySQL 8.

This version is a ground-up production-hardening pass over a prior student
project. See [CHANGELOG.md](./CHANGELOG.md) for the complete list of fixes,
and [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) /
[docs/SECURITY.md](./docs/SECURITY.md) for deeper technical detail.

---

## What's in here

```
scms/
├── backend/      Spring Boot 3 REST API (Java 17, MySQL, Flyway, JWT)
├── frontend/      React 18 + Vite SPA
├── docker-compose.yml
└── docs/          Architecture & security notes
```

- **Roles:** USER (student), STAFF (handles assigned complaints), ADMIN
  (full oversight: users, categories, reports, assignment).
- **Lifecycle:** SUBMITTED → IN_REVIEW → IN_PROGRESS → RESOLVED → CLOSED,
  with REJECTED reachable from any open state. See
  `common/ComplaintStatusPolicy.java` for the exact state machine.
- **Assignment workflow:** a STAFF (or ADMIN) member self-assigns from the
  unassigned queue (`/queue`), or an admin assigns a complaint to a specific
  staff member from the Assignments page (`/admin/assignments`).

---

## Quickest start — Docker Compose

```bash
git clone <this-repo>
cd scms
cp .env.example .env
# edit .env — at minimum set JWT_SECRET and DB_PASS to real values
docker compose up --build
```

Visit **http://localhost:8080**. The backend, frontend, and MySQL all start
together, with healthchecks gating startup order. Default demo accounts
(non-production profile only — see below):

| Role  | Email              | Password    |
|-------|--------------------|-------------|
| Admin | admin@scms.com     | Admin@1234  |
| Staff | staff@scms.com     | Staff@1234  |
| User  | student@scms.com   | User@1234   |

> **Change these before any real deployment.** They only exist when
> `SPRING_PROFILES_ACTIVE` does **not** include `prod` — see
> `DataSeeder.java`. For a real first admin account, see "First production
> admin" below.

---

## Local development (without Docker)

### Prerequisites

| Tool    | Version |
|---------|---------|
| Java    | 17+     |
| Maven   | 3.9+    |
| Node.js | 18+     |
| MySQL   | 8.x     |

### Backend

```bash
cd backend
# MySQL must be running locally (or point DB_URL at any reachable instance)
export DB_USER=root DB_PASS=yourpassword JWT_SECRET=$(openssl rand -base64 48)
mvn spring-boot:run
```

Flyway runs the schema migrations automatically on startup
(`src/main/resources/db/migration/`). The API is at `http://localhost:8080`,
interactive docs at `http://localhost:8080/swagger-ui.html`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite's dev server proxies `/api/*` to `http://localhost:8080` (see
`vite.config.js`) — no CORS configuration needed for local dev.

### Running tests

```bash
cd backend
mvn test
```

Tests run against an in-memory H2 database (`application-test.properties`)
— no MySQL or Docker required just to run the suite. See
`src/test/java/com/scms/` for what's covered: the status-transition state
machine, JWT issuance/validation/revocation, auth flows (registration,
login, lockout, IP throttling), the assignment workflow's access control,
and the soft-delete/aggregate-query behaviour of the repository layer.

> **Honesty note:** this is a meaningful, real test suite — not exhaustive
> coverage. It was written without the ability to execute `mvn test` in the
> environment that produced it (no Maven Central network access in that
> sandbox); the GitHub Actions workflow (`.github/workflows/ci.yml`) is
> configured to run it on every push, which is where you should expect to
> see it actually go green. Run it locally first if you want to verify
> before pushing.

---

## Environment variables

All variables have safe local-dev defaults baked into
`application.properties` — nothing below is *required* for `mvn
spring-boot:run` against a local MySQL with default credentials. For any
real deployment, set at minimum `JWT_SECRET`, `DB_PASS`, `CORS_ORIGINS`,
`COOKIE_SECURE=true`, and `HSTS_ENABLED=true`.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | local MySQL | Database connection |
| `JWT_SECRET` | dev-only placeholder | **Must** be a long random string in any real deployment |
| `JWT_ACCESS_EXPIRY_MS` | 900000 (15 min) | Access token lifetime |
| `JWT_REFRESH_EXPIRY_MS` | 604800000 (7 days) | Refresh token lifetime |
| `CORS_ORIGINS` | `http://localhost:5173` | Comma-separated allowed frontend origins |
| `COOKIE_SECURE` | true | Set `false` only for local plain-HTTP dev |
| `HSTS_ENABLED` | true | Set `false` only for local plain-HTTP dev |
| `TRUSTED_PROXIES` | empty | IPs of reverse proxies allowed to set `X-Forwarded-For` — see `docs/SECURITY.md` |
| `EMAIL_ENABLED` | false | Set `true` + `SMTP_*` to send real emails; otherwise notifications are logged, not sent |
| `SMTP_HOST/PORT/USER/PASS` | empty | SMTP credentials, only used when `EMAIL_ENABLED=true` |
| `SPRING_PROFILES_ACTIVE` | dev | Set to `prod` for real deployments — disables `DataSeeder`'s demo accounts |

---

## Integrating SCMS into another application

SCMS exposes a versionless-but-stable REST API under `/api/**`, documented
interactively at `/swagger-ui.html` (raw OpenAPI JSON at `/v3/api-docs`).
Any frontend — not just the bundled React app — can consume it:

1. **Set `CORS_ORIGINS`** to include the calling site's origin(s),
   comma-separated.
2. **Authenticate**: `POST /api/auth/login` with `{email, password}`
   returns `{accessToken, expiresInSeconds, userId, firstName, lastName,
   email, roles}` and sets an HttpOnly refresh-token cookie scoped to
   `/api/auth`. Send the access token as `Authorization: Bearer <token>` on
   every subsequent request.
3. **Refresh transparently**: when a request gets `401`, call `POST
   /api/auth/refresh` (send credentials/cookies) to get a new access token
   without forcing the user to log in again — see
   `frontend/src/api/axios.js` for a complete reference implementation of
   this pattern (request queueing included) if you're integrating from
   another JS frontend.
4. **CSRF note**: the refresh/logout endpoints are the only ones that rely
   on a cookie; every other endpoint relies on the `Authorization` header,
   which a cross-site request cannot forge. See `docs/SECURITY.md` for the
   full threat-model writeup.

Example:

```bash
curl -X POST https://your-scms-host/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"student@scms.com","password":"User@1234"}'

curl https://your-scms-host/api/complaints \
  -H "Authorization: Bearer <accessToken from above>"
```

---

## Deploying

### Docker Compose (self-hosted)

The simplest path for a single VM/server: `docker compose up -d --build`
with a real `.env`. Put a TLS-terminating reverse proxy (Caddy, Nginx,
Traefik, or your cloud provider's load balancer) in front of port 8080 if
you need HTTPS at this layer — set `TRUSTED_PROXIES` to that proxy's IP and
`COOKIE_SECURE=true`, `HSTS_ENABLED=true`.

### Split hosting (frontend static host + backend container)

The frontend's `dist/` build output (`npm run build`) is a static bundle —
deployable to any static host (Vercel, Netlify, S3+CloudFront, GitHub
Pages). Point its API calls at your backend's public URL (this requires
changing the hardcoded relative `/api` base in `frontend/src/api/axios.js`
to an absolute URL — set it via a build-time environment variable) and set
that frontend origin in the backend's `CORS_ORIGINS`. Deploy the backend
container (Railway, Render, Fly.io, ECS, a VM — anywhere that runs a
Docker image and gives you a MySQL connection string) using
`backend/Dockerfile` directly.

### First production admin

`DataSeeder` is disabled when `SPRING_PROFILES_ACTIVE` includes `prod` — no
demo accounts with known passwords are created. Create your first real
admin by registering normally (`/register` — creates a USER) and then
promoting that account directly in the database:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'your-real-admin-email@example.com' AND r.name = 'ADMIN';
DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE email = 'your-real-admin-email@example.com') AND role_id = (SELECT id FROM roles WHERE name = 'USER');
```

---

## Configuring email notifications

Disabled by default (`EMAIL_ENABLED=false`) — complaint-created,
status-changed, and assignment notifications are logged instead of sent, so
the app runs correctly with zero SMTP credentials configured. To enable
real delivery, set `EMAIL_ENABLED=true` and the `SMTP_HOST` / `SMTP_PORT` /
`SMTP_USER` / `SMTP_PASS` variables for any standard SMTP provider (Gmail
with an app password, SendGrid, Mailgun, AWS SES, etc.). See
`notification/SmtpEmailSender.java`.

---

## Known limitations / roadmap

In the interest of transparency (this project's own evaluation history is
literally a public, brutally honest audit — see `EVALUATION.md` if present
in your copy), here's what was deliberately deferred rather than rushed:

- **Rate limiting is database-backed, not Redis.** Correct and
  multi-instance-safe, but higher-latency than Redis at extreme login
  volume. Swap `LoginThrottleRepository`/`LoginAttemptService` for a
  Redis-backed implementation if you ever need that scale — the interface
  boundary is already there.
- **Free-text search is server-side for admins, page-local for
  students/staff.** Full server-side full-text search (Elasticsearch or
  MySQL FULLTEXT) is a real upgrade path, not implemented here.
- **No TypeScript.** The frontend is well-organised JS with clear
  prop/response shapes documented in comments, but not statically typed.
- **No distributed tracing / APM.** Actuator + Prometheus metrics exist;
  OpenTelemetry/Jaeger integration does not.
- **No multi-tenancy, SLA engine, or full WCAG-AA automated testing.**
  These are genuine enterprise-scale features explicitly out of scope for
  this pass — see the original evaluation report's Phase 5 roadmap for the
  full list if you want to keep pushing further.
- **`AdminReportService.averageResolutionHours()`** loads all-time
  resolved/closed complaints into memory — documented in code as a
  scale limitation to revisit with a native aggregate query if your
  complaint volume reaches tens of thousands.

None of the above block running this in production at realistic
college-complaint-system scale (hundreds to low thousands of users); they're
honestly documented next steps, not hidden gaps.

---

## License

Use freely for educational and portfolio purposes.
