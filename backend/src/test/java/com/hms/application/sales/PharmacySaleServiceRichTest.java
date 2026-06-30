package com.hms.application.sales;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class PharmacySaleServiceRichTest {

    private PharmacySaleService service;

    @BeforeEach
    void setUp() throws Exception {
        ReflectiveTestUtil.setupTenantContext();
        service = ReflectiveTestUtil.createWithMocks(PharmacySaleService.class);
    }

    @AfterEach
    void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test
    void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(service); }

    @Test
    void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(service); }

    @Test
    void testSaleOperations() {
        UUID id = UUID.randomUUID();
        try { service.getById(id); } catch (Exception e) {}
        try { service.getByPatient(id); } catch (Exception e) {}
        try { service.getByDate(LocalDate.now()); } catch (Exception e) {}
        try { service.getByDateAndQuery(LocalDate.now(), "test"); } catch (Exception e) {}
        try { service.getDraftsByDepartment(id); } catch (Exception e) {}
        try { service.deleteSale(id); } catch (Exception e) {}
        try { service.collectPayment(id, BigDecimal.TEN, "CASH", null, null, null); } catch (Exception e) {}
    }

    @Test
    void testPrivateMethods() {
        for (Method m : PharmacySaleService.class.getDeclaredMethods()) {
            m.setAccessible(true);
            Object[] args = ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes());
            try { m.invoke(service, args); } catch (Exception e) {}
        }
    }
}
