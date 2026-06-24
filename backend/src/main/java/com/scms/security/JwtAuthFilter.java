package com.scms.security;

import com.scms.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — runs before every HTTP request and validates the access JWT.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • Only accepts type="access" tokens — a refresh token (even if stolen
 *     from a network capture) cannot be replayed as an API credential.
 *
 *   • Checks userDetails.isEnabled() and isAccountNonLocked() on EVERY
 *     request, not just at login. v1.3 finding: "deactivated user's existing
 *     JWT is valid until it expires (24 hours)". Because this filter already
 *     does a fresh DB lookup per request (loadUserByUsername), checking
 *     these two flags here costs nothing extra and closes that gap
 *     completely — deactivating a user now takes effect on their very next
 *     API call, not after up to 24 hours.
 *
 *   • Checks the token's embedded "tv" (tokenVersion) claim against the
 *     user's current DB value — this is what makes logout (and force-revoke)
 *     actually invalidate the token instead of just deleting the client's
 *     copy of it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil            jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final String email = jwtUtil.extractEmail(token);

            if (email != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                User user = (User) userDetails;

                boolean validToken = jwtUtil.isValid(
                        token, email, user.getTokenVersion(), JwtUtil.TYPE_ACCESS);

                if (validToken && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid/expired/revoked token, or account deactivated/locked —
            // do not set auth; downstream endpoints get 401, frontend silently
            // attempts a token refresh (see /api/auth/refresh).
            log.debug("JWT filter skipped authentication: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
