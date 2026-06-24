# Security

This document explains the security posture and the reasoning behind it —
not just what's implemented, but why, so a future maintainer can extend it
correctly instead of accidentally weakening it.

## Authentication

- **Passwords**: BCrypt, cost factor 12 (~250ms/hash — high enough to
  resist offline brute force, low enough not to bottleneck login
  throughput).
- **Tokens**: short-lived access token (15 min default) + HttpOnly refresh
  cookie (7 days default), both JWT, HMAC-signed (JJWT auto-selects
  HS256/384/512 based on the strength of the key derived from
  `JWT_SECRET` — see `JwtUtil`). The secret is never hardcoded and never
  committed; the bundled dev-only fallback is exactly that — a fallback for
  local development, always overridden via the `JWT_SECRET` environment
  variable in any real deployment.
- **Revocation**: every token carries a `tv` (tokenVersion) claim. Logging
  out increments `User.tokenVersion` in the database; every previously
  issued token (access AND refresh) fails validation immediately, even
  though it hasn't expired. This is the mechanism behind "deactivating a
  user takes effect on their next request" and "logout actually logs out."
- **The refresh token never appears in a JSON response body or
  localStorage.** It's set directly as an HttpOnly cookie by
  `AuthController`, scoped to `Path=/api/auth`. JavaScript — including an
  XSS payload — cannot read it.

## CSRF threat model

CSRF (Cross-Site Request Forgery) works by exploiting credentials a
browser attaches *automatically* — cookies. This API's primary credential
is a Bearer token in the `Authorization` header, which a cross-site form
or script **cannot** attach automatically; CSRF doesn't apply to any
endpoint authenticated that way (which is everything except two).

The two cookie-authenticated endpoints are `/api/auth/refresh` and
`/api/auth/logout`. Mitigations, layered:

1. The cookie is `SameSite=Strict` — it is not sent at all on cross-site
   navigations or requests in the first place, for any modern browser.
2. Even in the worst case (an attacker tricks a logged-in user's browser
   into calling `/api/auth/refresh`), the endpoint only **issues new
   tokens** — it cannot read or modify any application data. A forced
   refresh is, at worst, a no-op from the attacker's perspective.
3. `/api/auth/logout` being CSRF-forced would just log the user out — an
   annoyance, not a data-confidentiality or integrity issue.

Given that risk profile, a full CSRF token (double-submit cookie pattern)
was judged not worth the added complexity for this specific cookie's
blast radius. If you add any FUTURE cookie-authenticated endpoint that
mutates meaningful data, revisit this.

## X-Forwarded-For / IP spoofing

`HttpRequestUtils.extractClientIp()` only trusts the `X-Forwarded-For`
header when the request's actual TCP peer (`request.getRemoteAddr()`,
which cannot be spoofed at the network layer) is in the configured
`security.trusted-proxies` list. If your Spring Boot container is ever
reachable directly (not exclusively through your reverse proxy), an
untrusted peer's claimed `X-Forwarded-For` is ignored entirely and the
real TCP-layer address is used instead.

**Configure `TRUSTED_PROXIES`** to your actual reverse proxy's IP once you
put one in front of this stack — otherwise IP-based rate limiting will
(safely, not insecurely) attribute every request to the proxy's own IP
instead of the real client.

## Brute-force protection — two independent layers

1. **Account-level lockout** (`User.failedLoginAttempts` /
   `accountLockedUntil`): 5 failed attempts locks the specific account for
   15 minutes. Enforced by Spring Security's own
   `UserDetails.isAccountNonLocked()` contract — there is no code path that
   bypasses it, because the framework itself checks it on every
   authentication attempt.
2. **IP-level throttle** (`login_throttle` table): 20 attempts from one IP
   in 15 minutes (across any combination of email addresses — this is the
   defense against credential stuffing specifically) locks that IP for 15
   minutes. Database-backed, so it's correctly shared across every
   application instance behind a load balancer — a single in-memory map
   (the prior implementation) would let an attacker bypass the limit
   entirely just by hitting a different instance.

A scheduled job (`LoginThrottleCleanupJob`) removes stale, already-expired
throttle rows hourly — it can never clear an *active* lockout early, unlike
a "wipe everything once the cache hits N entries" strategy would.

## Headers

- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none';
  object-src 'none'`
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Strict-Transport-Security` — enabled by default (`HSTS_ENABLED=true`);
  disable **only** for local plain-HTTP development.

## Authorization

- Role check at the URL level (`SecurityConfig` — `/api/admin/**` requires
  `ROLE_ADMIN`) AND at the method level (`@PreAuthorize` on
  STAFF/ADMIN-only methods) AND at the data level (service-layer ownership
  checks — e.g. a STAFF user can only change the status of a complaint
  actually assigned to them, checked in `ComplaintService.assertCanManage`,
  not just "any STAFF user").
- This is deliberate defense in depth: even if one layer has a bug, the
  others still hold.

## Things explicitly out of scope for this pass

- No MFA / 2FA.
- No password-reset-via-email flow (an admin can reset a user's status but
  there's no self-service "forgot password" email flow yet).
- No dependency vulnerability scanning in CI (OWASP Dependency-Check, Snyk,
  Trivy) — straightforward to add to `.github/workflows/ci.yml` as a
  follow-up.
- No penetration test has been performed on this codebase.

Report security issues responsibly — this is a portfolio/educational
project, not a project with a formal security disclosure program, but
please don't publish a 0-day against a default demo-account setup that
others might still have running.
