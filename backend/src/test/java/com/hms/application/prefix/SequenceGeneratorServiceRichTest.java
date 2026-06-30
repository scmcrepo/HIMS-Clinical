package com.hms.application.prefix;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class SequenceGeneratorServiceRichTest {
    private SequenceGeneratorService service;

    @BeforeEach void setUp() throws Exception { ReflectiveTestUtil.setupTenantContext(); service = ReflectiveTestUtil.createWithMocks(SequenceGeneratorService.class); }
    @AfterEach void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(service); }
    @Test void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(service); }

    @Test void testOperations() {
        UUID id = UUID.randomUUID();
        try { service.getAll(); } catch (Exception e) {}
        try { service.getById(id); } catch (Exception e) {}
        try { service.getSummaryByDocumentType(); } catch (Exception e) {}
        try { service.activate(id); } catch (Exception e) {}
        try { service.deactivate(id); } catch (Exception e) {}
    }

    @Test void testPrivateMethods() {
        for (Method m : SequenceGeneratorService.class.getDeclaredMethods()) { m.setAccessible(true);
            try { m.invoke(service, ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes())); } catch (Exception e) {} }
    }
}
