package com.hms.infrastructure.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-001 / T-002.
 *
 * <p>Observability that is never asserted on is observability that quietly
 * disappears in a refactor. These tests pin the two properties the rest of the
 * campaign depends on: every log line carries a correlation id, and that id
 * survives the hop onto an async worker thread.
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private ListAppender<ILoggingEvent> appender;
    private Logger testLogger;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        testLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilterTest.class);
        appender = new ListAppender<>();
        appender.start();
        testLogger.addAppender(appender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        testLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void generatesACorrelationIdWhenTheCallerSuppliesNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));

        filter.doFilter(request, response, chain);

        assertNotNull(seenInsideChain.get(), "a correlation id must exist inside the chain");
        assertEquals(seenInsideChain.get(),
                     response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER),
                     "the id must be echoed to the caller so they can quote it in a bug report");
    }

    @Test
    void preservesAnInboundCorrelationId() throws Exception {
        // The whole point is one id across WhatsApp -> orchestrator -> HMS. A filter
        // that mints a fresh id downstream silently breaks the chain.
        String inbound = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, inbound);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
            seen.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)));

        assertEquals(inbound, seen.get());
        assertEquals(inbound, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void rejectsAMaliciousInboundIdRatherThanEchoingIt() throws Exception {
        // An unvalidated header lands in the log stream. Newlines would let a caller
        // forge log entries.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER,
                          "abc\nlevel=ERROR message=forged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
            seen.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)));

        assertNotNull(seen.get());
        assertTrue(seen.get().matches("[A-Za-z0-9._:-]{1,64}"),
                   "a rejected id must be replaced with a generated one, not echoed");
    }

    @Test
    void theCorrelationIdReachesActualLogEvents() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
            LoggerFactory.getLogger(CorrelationIdFilterTest.class).info("something happened"));

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        Map<String, String> mdc = events.get(0).getMDCPropertyMap();
        assertNotNull(mdc.get(CorrelationIdFilter.MDC_CORRELATION_ID),
                      "log events must carry the correlation id, not just the thread");
    }

    @Test
    void clearsMdcAfterTheRequestEvenOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // the filter must still clean up
        }

        assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID),
                   "a leaked MDC entry misattributes the next request on this pooled thread");
    }

    @Test
    void mdcSurvivesTheHopOntoAnAsyncWorkerThread() throws Exception {
        // Without the decorator, this is where the trace goes dark — and async work
        // (bulk import today, agent tool calls tomorrow) is exactly where debugging
        // is hardest.
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
            new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, correlationId);

        AtomicReference<String> onWorker = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        ((Executor) executor).execute(() -> {
            onWorker.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "async task did not run");
        assertEquals(correlationId, onWorker.get(),
                     "MdcTaskDecorator must carry the correlation id onto the worker");

        executor.shutdown();
    }

    @Test
    void theWorkerThreadDoesNotRetainMdcBetweenTasks() throws Exception {
        // Pooled threads are reused. A stale correlation id is worse than none: it
        // attributes one request's logs to a completely different request.
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
            new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();

        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, "first-request");
        CountDownLatch first = new CountDownLatch(1);
        ((Executor) executor).execute(first::countDown);
        assertTrue(first.await(5, TimeUnit.SECONDS));

        MDC.clear();
        AtomicReference<String> leaked = new AtomicReference<>("sentinel");
        CountDownLatch second = new CountDownLatch(1);
        ((Executor) executor).execute(() -> {
            leaked.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));
            second.countDown();
        });
        assertTrue(second.await(5, TimeUnit.SECONDS));

        assertNull(leaked.get(), "the pooled thread leaked the previous task's correlation id");

        executor.shutdown();
    }
}
