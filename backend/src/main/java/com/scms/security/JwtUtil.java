package com.scms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JwtUtil — creates and validates JSON Web Tokens.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • Access + refresh token pair. v1.3 issued a single 24-hour token with
 *     no refresh mechanism — the report's exact words: "no token refresh
 *     mechanism — when a 24-hour token expires, the user is hard-logged
 *     out". Access tokens are now short-lived (15 min default); a refresh
 *     token (7 days default) is issued alongside it and stored as an
 *     HttpOnly cookie (set by AuthController, never touched by JavaScript —
 *     this is also the fix for "JWT in localStorage is vulnerable to XSS
 *     token theft": the long-lived credential is no longer reachable by
 *     any script at all).
 *
 *   • "type" claim ("access" | "refresh") — prevents a refresh token from
 *     being used directly as an API credential even if it leaked, and vice
 *     versa. JwtAuthFilter only accepts type=access.
 *
 *   • "tv" (tokenVersion) claim — see User.tokenVersion. Logging out bumps
 *     the DB counter; every previously-issued token (access AND refresh)
 *     immediately fails validation because its embedded "tv" no longer
 *     matches. This is the v2.0 answer to "no JWT token blacklisting" —
 *     functionally equivalent revocation without storing every token ever
 *     issued.
 *
 *   • "jti" claim — a random UUID per token, included for traceability in
 *     logs/audit trails (correlating "which specific token did this") even
 *     though v2.0 doesn't maintain a jti blacklist table.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    public static final String TYPE_ACCESS  = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long      accessExpirationMs;
    private final long      refreshExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}")              String secret,
            @Value("${jwt.access-expiration-ms}")  long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secretKey           = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs  = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(String email, int tokenVersion) {
        return buildToken(email, tokenVersion, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(String email, int tokenVersion) {
        return buildToken(email, tokenVersion, TYPE_REFRESH, refreshExpirationMs);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private String buildToken(String email, int tokenVersion, String type, long expirationMs) {
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .claim("tv", tokenVersion)
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public int extractTokenVersion(String token) {
        Object tv = parseClaims(token).get("tv");
        return tv instanceof Number ? ((Number) tv).intValue() : -1;
    }

    public String extractType(String token) {
        Object type = parseClaims(token).get("type");
        return type != null ? type.toString() : null;
    }

    /**
     * Validates signature + expiry + matches the expected type + matches the
     * user's current tokenVersion. Does NOT check isEnabled/isAccountNonLocked —
     * that's JwtAuthFilter's job, since it needs a fresh DB read either way.
     */
    public boolean isValid(String token, String expectedEmail, int currentTokenVersion, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            boolean emailMatches   = expectedEmail.equals(claims.getSubject());
            boolean notExpired     = claims.getExpiration().after(new Date());
            boolean versionMatches = currentTokenVersion == extractTokenVersionFrom(claims);
            boolean typeMatches    = expectedType.equals(claims.get("type"));
            return emailMatches && notExpired && versionMatches && typeMatches;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private int extractTokenVersionFrom(Claims claims) {
        Object tv = claims.get("tv");
        return tv instanceof Number ? ((Number) tv).intValue() : -1;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
