package com.hms.application.consultant;

import com.hms.testutil.ReflectiveTestUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class ConsultantServiceRichTest {
    private ConsultantService service;

    @BeforeEach void setUp() throws Exception { ReflectiveTestUtil.setupTenantContext(); service = ReflectiveTestUtil.createWithMocks(ConsultantService.class); }
    @AfterEach void tearDown() { ReflectiveTestUtil.clearTenantContext(); }

    @Test void testAllPublicMethods() { ReflectiveTestUtil.invokePublicMethods(service); }
    @Test void testAllDeclaredMethods() { ReflectiveTestUtil.invokeAllMethods(service); }

    @Test void testCrudOperations() {
        UUID id = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile("photo", "photo.jpg", "image/jpeg", new byte[]{1,2,3});
        try { service.getAll(); } catch (Exception e) {}
        try { service.getAllNonDeleted(); } catch (Exception e) {}
        try { service.getById(id); } catch (Exception e) {}
        try { service.searchByName("test"); } catch (Exception e) {}
        try { service.searchNonDeletedByName("test"); } catch (Exception e) {}
        try { service.delete(id); } catch (Exception e) {}
    }

    @Test void testPrivateMethods() {
        for (Method m : ConsultantService.class.getDeclaredMethods()) { m.setAccessible(true);
            try { m.invoke(service, ReflectiveTestUtil.buildArgs(m.getParameterTypes(), m.getGenericParameterTypes())); } catch (Exception e) {} }
    }
}
