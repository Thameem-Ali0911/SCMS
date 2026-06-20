package com.scms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtUtil — creates and validates JSON Web Tokens.
 *
 * MENTOR NOTE — JWT anatomy:
 *   A JWT is three Base64-encoded parts joined by dots:
 *     Header.Payload.Signature
 *
 *   Header  → {"alg":"HS512","typ":"JWT"}
 *   Payload → {"sub":"ali@college.edu","iat":1714000000,"exp":1714086400}
 *   Signature → HMAC-SHA512(header + "." + payload, secretKey)
 *
 * The server signs the token on login. The client stores it and sends it
 * back in every request. The server just re-verifies the signature —
 * no DB lookup, no session table, fully stateless.
 *
 * Security note: the payload is Base64-encoded, NOT encrypted.
 * Anyone can decode it. Never put passwords or sensitive data in a JWT.
 * The signature only proves the token was not tampered with.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey secretKey;
    private final long      expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}")        String secret,
            @Value("${jwt.expiration-ms}") long   expirationMs) {
        // Keys.hmacShaKeyFor needs at least 512 bits (64 bytes) for HS512.
        this.secretKey    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Issues a signed JWT with the user's email as the subject claim. */
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /** Extracts the email from a verified token. Throws JwtException if invalid. */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /** Returns true only if the token signature is valid AND it has not expired. */
    public boolean isValid(String token, UserDetails user) {
        try {
            return extractEmail(token).equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
