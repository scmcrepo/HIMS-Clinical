package com.hms.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes the correlation context for a request and puts it into the SLF4J MDC,
 * so the Logstash JSON encoder emits it on every log line without each call site
 * having to remember.
 *
 * <p>A single patient interaction will eventually span the WhatsApp webhook, the
 * LangGraph orchestrator, this backend, Postgres and an SMS send. Without one id
 * threaded through all of it, "the booking failed" is unanswerable. The id is
 * generated at the edge and preserved if the caller already supplied one — anything
 * that mints its own id downstream has broken the chain.
 *
 * <p>Runs very early so that even authentication failures are correlated.
 * {@code TenantResolutionFilter} adds tenant and branch later, once the principal
 * is known.
 *
 * <p>MDC is a ThreadLocal and does not survive a thread hop. See
 * {@link MdcTaskDecorator} for the async path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String RUN_ID_HEADER = "X-Run-Id";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_RUN_ID = "runId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_BRANCH_ID = "branchId";

    /**
     * Inbound ids are echoed into logs, so constrain them. Without this an attacker
     * could inject newlines or huge strings into the log stream through a header.
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(CORRELATION_ID_HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        String runId = sanitize(request.getHeader(RUN_ID_HEADER));

        MDC.put(MDC_CORRELATION_ID, correlationId);
        if (runId != null) {
            MDC.put(MDC_RUN_ID, runId);
        }
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_RUN_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_BRANCH_ID);
        }
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
