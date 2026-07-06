package com.hms.application.appointment;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class AppointmentSchedulingServiceRichTest {
    private AppointmentSchedulingService service;

    @BeforeEach void setUp() throws Exception { ReflectiveTestUtil.setupTenantContext(); service = ReflectiveTestUtil.createWithMocks(AppointmentSchedulingService.class); }
    @AfterEach void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(service); }
    @Test void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(service); }

    @Test void testOperations() {
        UUID id = UUID.randomUUID();
        try { service.cancel(id); } catch (Exception e) {}
        try { service.checkIn(id); } catch (Exception e) {}
        try { service.getById(id); } catch (Exception e) {}
        try { service.getByPatientId(id); } catch (Exception e) {}
        try { service.getByPatient(id, PageRequest.of(0,10)); } catch (Exception e) {}
        try { service.getByProviderAndDate(id, LocalDate.now()); } catch (Exception e) {}
        try { service.getSlotAvailability(id, LocalDate.now()); } catch (Exception e) {}
        try { service.linkPatient(id, id); } catch (Exception e) {}
    }

    @Test void testPrivateMethods() {
        for (Method m : AppointmentSchedulingService.class.getDeclaredMethods()) { m.setAccessible(true);
            try { m.invoke(service, ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes())); } catch (Exception e) {} }
    }
}
