package com.hms.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repeatedly-updated gauges, registered once and safe to set from a job.
 *
 * <p>Exists because the obvious call does not do what it looks like it does:
 *
 * <pre>{@code
 *   meterRegistry.gauge("hms_grievances_overdue", overdue.size());   // WRONG
 * }</pre>
 *
 * <p>Two things go wrong there. Micrometer holds only a <b>weak reference</b> to the
 * number it is given, so the boxed {@code Integer} can be collected and the gauge starts
 * reporting {@code NaN}. And meter registration is <b>idempotent by id</b>: calling it
 * again next time the job runs returns the meter already registered and discards the new
 * value. Between them, the gauge reports whatever count it saw first, forever — and
 * because it keeps reporting <em>something</em>, nothing looks broken. An alert written
 * against it sits quietly green.
 *
 * <p>Here each name gets one {@link AtomicLong}, held strongly by the map for the life of
 * the application and registered once. Updating means setting the value the gauge already
 * points at, which is what a gauge is for.
 *
 * <pre>{@code
 *   gauges.set("hms_grievances_overdue", overdue.size());            // RIGHT
 * }</pre>
 *
 * <p>Untagged by design: every current caller reports a single global count. A gauge that
 * needs tags wants its own holder field rather than a shared map keyed on name alone.
 */
@Component
@RequiredArgsConstructor
public class MetricGauges {

    private final MeterRegistry meterRegistry;

    private final Map<String, AtomicLong> holders = new ConcurrentHashMap<>();

    /** Sets {@code name} to {@code value}, registering the gauge on first use. */
    public void set(String name, long value) {
        holders.computeIfAbsent(name, n -> {
            AtomicLong holder = new AtomicLong();
            meterRegistry.gauge(n, holder, AtomicLong::get);
            return holder;
        }).set(value);
    }

    /** Current value, or {@code null} if nothing has ever set this gauge. Test seam. */
    public Long peek(String name) {
        AtomicLong holder = holders.get(name);
        return holder == null ? null : holder.get();
    }
}
