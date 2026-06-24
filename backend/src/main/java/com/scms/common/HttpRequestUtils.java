package com.scms.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * HttpRequestUtils — extracts client IP / User-Agent from the *current*
 * HTTP request without needing every service method to accept an
 * HttpServletRequest parameter.
 *
 * MENTOR NOTE — fixing the X-Forwarded-For spoofing vulnerability (v1.3 finding):
 *
 * The v1.3 codebase trusted X-Forwarded-For unconditionally:
 *   String ip = xff != null ? xff.split(",")[0] : request.getRemoteAddr();
 *
 * This is exploitable: if the Spring Boot port is *ever* reachable directly
 * (no reverse proxy in front, a misconfigured load balancer, a port left open
 * in a security group), an attacker can send their own X-Forwarded-For header
 * and the application will trust it completely — defeating IP-based rate
 * limiting and poisoning the audit trail with a forged IP.
 *
 * The fix: only trust X-Forwarded-For when request.getRemoteAddr() (the actual
 * TCP peer) is itself in our configured set of trusted reverse-proxy IPs.
 * If the request reaches us directly from an untrusted peer, we use
 * getRemoteAddr() — which cannot be spoofed at the TCP layer — even if the
 * attacker sent an X-Forwarded-For header.
 *
 * Configure trusted proxies via `security.trusted-proxies` (comma-separated).
 * In Docker Compose / Kubernetes, this is typically the Nginx ingress pod's
 * internal IP/CIDR, or 127.0.0.1 if Nginx runs as a sidecar on the same host.
 */
@Slf4j
public final class HttpRequestUtils {

    private HttpRequestUtils() {}

    /** Populated once at startup by SecurityConfig from `security.trusted-proxies`. */
    private static volatile Set<String> trustedProxies = new HashSet<>();

    public static void configureTrustedProxies(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            trustedProxies = new HashSet<>();
            return;
        }
        trustedProxies = new HashSet<>(Arrays.asList(commaSeparated.split(",")));
        log.info("Trusted reverse-proxy IPs configured: {}", trustedProxies);
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        String remoteAddr = request.getRemoteAddr();
        boolean fromTrustedProxy = trustedProxies.contains(remoteAddr) || trustedProxies.contains("*");

        if (fromTrustedProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // First entry is the original client; subsequent entries are
                // intermediate proxies the request passed through.
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    public static String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        // Defensive truncation — AuditLog.userAgent is VARCHAR(500)
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }

    /** Convenience for service-layer code that has no HttpServletRequest parameter. */
    public static String currentIp() {
        HttpServletRequest req = currentRequest();
        return req != null ? extractClientIp(req) : null;
    }

    public static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        return req != null ? extractUserAgent(req) : null;
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (IllegalStateException e) {
            // No request bound to this thread (e.g. called from an async/event listener
            // after the original HTTP request has already completed).
            return null;
        }
    }
}
