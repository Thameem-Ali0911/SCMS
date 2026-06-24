package com.scms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtilTest — round-trips access/refresh tokens and verifies the v2.0
 * security fixes: type separation (access vs refresh), tokenVersion
 * revocation, and rejection of tampered tokens.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET =
            "test-only-secret-key-not-for-production-use-minimum-64-characters-needed";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 900_000L, 604_800_000L);
    }

    @Test
    void accessTokenRoundTripsCorrectly() {
        String token = jwtUtil.generateAccessToken("ali@scms.com", 0);

        assertEquals("ali@scms.com", jwtUtil.extractEmail(token));
        assertEquals(0, jwtUtil.extractTokenVersion(token));
        assertEquals(JwtUtil.TYPE_ACCESS, jwtUtil.extractType(token));
        assertTrue(jwtUtil.isValid(token, "ali@scms.com", 0, JwtUtil.TYPE_ACCESS));
    }

    @Test
    void refreshTokenIsRejectedWhenUsedAsAccessToken() {
        String refreshToken = jwtUtil.generateRefreshToken("ali@scms.com", 0);

        assertEquals(JwtUtil.TYPE_REFRESH, jwtUtil.extractType(refreshToken));
        assertFalse(jwtUtil.isValid(refreshToken, "ali@scms.com", 0, JwtUtil.TYPE_ACCESS),
                "A refresh token must never validate as an access credential");
    }

    @Test
    void tokenIsRejectedAfterLogoutBumpsTokenVersion() {
        String token = jwtUtil.generateAccessToken("ali@scms.com", 0);

        assertTrue(jwtUtil.isValid(token, "ali@scms.com", 0, JwtUtil.TYPE_ACCESS));

        // Simulate logout: User.tokenVersion is now 1 in the database.
        assertFalse(jwtUtil.isValid(token, "ali@scms.com", 1, JwtUtil.TYPE_ACCESS),
                "A token issued before logout must be rejected once tokenVersion has been bumped");
    }

    @Test
    void emailMismatchFailsValidation() {
        String token = jwtUtil.generateAccessToken("ali@scms.com", 0);
        assertFalse(jwtUtil.isValid(token, "someone-else@scms.com", 0, JwtUtil.TYPE_ACCESS));
    }

    @Test
    void tamperedTokenFailsValidation() {
        String token = jwtUtil.generateAccessToken("ali@scms.com", 0);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        // JwtUtil.isValid() catches JwtException internally and returns
        // false rather than propagating — a tampered signature must never
        // be reported as valid.
        assertFalse(jwtUtil.isValid(tampered, "ali@scms.com", 0, JwtUtil.TYPE_ACCESS));
    }
}
