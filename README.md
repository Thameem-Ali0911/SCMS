# SCMS — Smart Complaint Management System
### Login + Register MVP | React + Spring Boot + MySQL + JWT

---

## Project structure

```
scms/
├── backend/    ← Spring Boot REST API  (port 8080)
└── frontend/   ← React + Vite SPA      (port 5173)
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17 or 21 |
| Maven | 3.9+ |
| Node.js | 18+ |
| MySQL | 8.x |

---

## Step 1 — MySQL setup

Open MySQL Workbench (or terminal) and run:

```sql
-- Nothing needed. Spring Boot creates the DB automatically.
-- Just make sure MySQL is running and your credentials are correct.
```

---

## Step 2 — Configure the backend

Open `backend/src/main/resources/application.properties` and change:

```properties
spring.datasource.username=root        ← your MySQL username
spring.datasource.password=yourpassword ← your MySQL password
```

The database `scms_db` is created automatically on first run.

---

## Step 3 — Run the backend

```bash
cd backend
mvn spring-boot:run
```

On first run, Spring Boot will:
1. Connect to MySQL and create `scms_db`
2. Create all tables (`users`, `roles`, `user_roles`) via Hibernate
3. Seed two default accounts (see DataSeeder.java):
   - `admin@scms.com` / `Admin@1234`  (role: ADMIN)
   - `student@scms.com` / `User@1234` (role: USER)

You should see: `Started ScmsApplication in X.XXX seconds`

---

## Step 4 — Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open: **http://localhost:5173**

---

## API endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | Public | Register a new user |
| POST | `/api/auth/login` | Public | Login, get JWT |

### Register request body
```json
{
  "firstName": "Ali",
  "lastName":  "Khan",
  "email":     "ali@gecwyd.ac.in",
  "password":  "MyPass123",
  "phone":     "9876543210"
}
```

### Login request body
```json
{
  "email":    "ali@gecwyd.ac.in",
  "password": "MyPass123"
}
```

### Response (both endpoints)
```json
{
  "token":     "eyJhbGciOiJIUzUxMiJ9...",
  "userId":    3,
  "firstName": "Ali",
  "lastName":  "Khan",
  "email":     "ali@gecwyd.ac.in",
  "roles":     ["USER"]
}
```

---

## Architecture decisions

### Why JWT instead of sessions?
A React SPA and a Spring Boot API are on different ports. Sessions use cookies
which have CORS complications. JWT in the Authorization header is simpler,
stateless, and the industry standard for REST + SPA stacks.

### Why BCrypt cost 12?
BCrypt is deliberately slow. Cost 12 = ~250ms per hash, imperceptible to the
user but catastrophic for attackers trying millions of passwords. MD5/SHA are
far too fast for password storage.

### Why EAGER roles?
The User object lives only for the duration of one HTTP request (stateless).
EAGER fetching means roles come in the same SQL query as the user — safe here,
and avoids LazyInitializationException outside a Hibernate session.

### Why DataSeeder, not SQL scripts?
DataSeeder uses the same PasswordEncoder bean as the app, so BCrypt hashes are
generated at runtime. Hardcoding BCrypt hashes in SQL is fragile.

---

## Enable Lombok in IntelliJ

Settings → Build, Execution, Deployment → Compiler → Annotation Processors  
☑ Enable annotation processing

---

## Next steps (complaint management)
- `Complaint` entity + `ComplaintRepository`
- `ComplaintService` with ownership checks
- `ComplaintController` (CRUD endpoints)
- React pages: Dashboard, NewComplaint, ComplaintDetail
