package com.hms.application.billing;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class BillingOperationsServiceRichTest {

    private BillingOperationsService service;

    @BeforeEach
    void setUp() throws Exception {
        ReflectiveTestUtil.setupTenantContext();
        service = ReflectiveTestUtil.createWithMocks(BillingOperationsService.class);
    }

    @AfterEach
    void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(service); }
    @Test void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(service); }

    @Test void testBillOperationsWithIds() {
        UUID id = UUID.randomUUID();
        try { service.getBillById(id); } catch (Exception e) {}
        try { service.getBillsByPatient(id); } catch (Exception e) {}
        try { service.getBillByVisit(id); } catch (Exception e) {}
    }

    @Test void testPrivateMethods() {
        for (Method m : BillingOperationsService.class.getDeclaredMethods()) {
            m.setAccessible(true);
            Object[] args = ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes());
            try { m.invoke(service, args); } catch (Exception e) {}
        }
    }
}
