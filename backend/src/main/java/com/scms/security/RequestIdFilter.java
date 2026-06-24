package com.scms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * RequestIdFilter — generates (or propagates) a correlation ID for every
 * request and places it in both the SLF4J MDC (so it appears in every log
 * line written while handling this request) and the response header
 * `X-Request-Id` (so a user can report "X-Request-Id: abc-123" when
 * something goes wrong, and an operator can grep logs for exactly that
 * request across every component that touched it).
 *
 * MENTOR NOTE — fixing a Logging finding from the v1.3 report:
 * "No correlation ID / request ID — cannot trace a single request across
 * multiple log lines." This is the fix. Combined with the JSON structured
 * logging in logback-spring.xml, `requestId` becomes a queryable field in
 * any log aggregator (ELK, Loki, CloudWatch Logs Insights).
 */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
