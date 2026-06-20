package com.scms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — runs before every HTTP request and validates the JWT.
 *
 * MENTOR NOTE — filter chain position:
 * Spring Security processes requests through an ordered chain of filters.
 * We insert this filter BEFORE UsernamePasswordAuthenticationFilter so that
 * JWT-authenticated requests bypass the default username/password check.
 *
 * Flow per request:
 *   1. Read Authorization header → extract "Bearer <token>"
 *   2. Validate signature + expiry with JwtUtil
 *   3. Load UserDetails from DB by the email claim
 *   4. Set authentication in SecurityContext → controller sees a logged-in user
 *   5. Call filterChain.doFilter() → request continues to the controller
 *
 * If there's no token or it's invalid: skip step 4.
 * Spring Security then rejects protected endpoints with HTTP 401.
 *
 * OncePerRequestFilter guarantees this runs exactly once per request,
 * even in async dispatch / forward chains.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil            jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No token present → let the request through (Spring Security will reject
        // it if the endpoint requires authentication).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7); // strip "Bearer "

        try {
            final String email = jwtUtil.extractEmail(token);

            // Only set auth if email parsed AND no existing auth in the context.
            if (email != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtUtil.isValid(token, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    // Registering this in the SecurityContext is what makes
                    // @AuthenticationPrincipal work in the controller.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid token — do not set auth; downstream endpoints will get 401.
            logger.warn("JWT filter skipped: " + e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
