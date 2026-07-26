package com.hms.infrastructure.observability;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Copies the submitting thread's MDC onto the worker thread, so log lines from
 * {@code @Async} work stay correlated with the request that triggered them.
 *
 * <p>MDC is a ThreadLocal, so without this an async log line simply loses its
 * correlation id and the trace goes dark at exactly the point where debugging is
 * hardest. This is the same class of bug as tenant context not surviving a thread
 * hop, and it is worth remembering that the two are independent: copying MDC does
 * <em>not</em> give the worker a tenant. Tenant and branch must still be passed as
 * explicit arguments and set on the worker thread — see
 * {@code BulkImportAsyncService} for that pattern.
 *
 * <p>The worker's MDC is cleared afterwards because pooled threads are reused, and
 * a stale correlation id on a recycled thread is worse than none: it silently
 * attributes one request's logs to another.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (submitterContext != null) {
                    MDC.setContextMap(submitterContext);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
