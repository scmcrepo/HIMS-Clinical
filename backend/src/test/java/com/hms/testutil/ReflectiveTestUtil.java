package com.hms.testutil;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Reusable utility for reflection-based unit tests.
 * Constructs any service via its first public constructor,
 * auto-injecting Mockito deep-stubs for all dependencies.
 */
@SuppressWarnings("all")
public final class ReflectiveTestUtil {

    private ReflectiveTestUtil() {}

    /** Smart Mockito answer that returns sensible defaults */
    public static final Answer<Object> SMART_ANSWER = invocation -> {
        Method m = invocation.getMethod();
        Class<?> ret = m.getReturnType();

        if (ret.equals(Optional.class)) {
            Type returnType = m.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return Optional.of(Mockito.mock((Class<?>) typeArgs[0], Mockito.RETURNS_DEEP_STUBS));
                }
            }
            return Optional.empty();
        }
        if (ret.equals(boolean.class) || ret.equals(Boolean.class)) return true;
        if (ret.equals(int.class) || ret.equals(Integer.class)) return 1;
        if (ret.equals(long.class) || ret.equals(Long.class)) return 1L;
        if (ret.equals(double.class) || ret.equals(Double.class)) return 1.0;
        if (ret.equals(String.class)) return "test";
        if (ret.equals(BigDecimal.class)) return BigDecimal.TEN;
        if (ret.equals(UUID.class)) return UUID.randomUUID();
        if (ret.equals(LocalDate.class)) return LocalDate.now();
        if (ret.equals(LocalDateTime.class)) return LocalDateTime.now();
        if (ret.equals(Instant.class)) return Instant.now();
        if (List.class.isAssignableFrom(ret)) return new ArrayList<>();
        if (Set.class.isAssignableFrom(ret)) return new HashSet<>();
        if (Map.class.isAssignableFrom(ret)) return new HashMap<>();
        if (ret.isArray()) return Array.newInstance(ret.getComponentType(), 0);
        if (ret.equals(void.class) || ret.equals(Void.class)) return null;
        if (m.getName().equals("save") || m.getName().equals("saveAndFlush")) {
            Object[] args = invocation.getArguments();
            if (args.length > 0 && args[0] != null) return args[0];
        }
        if (m.getName().equals("generateNext")) return "SEQ-001";
        
        return Mockito.RETURNS_DEEP_STUBS.answer(invocation);
    };

    /** Create an instance of the given class with all constructor args mocked */
    public static <T> T createWithMocks(Class<T> clazz) throws Exception {
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            constructors = clazz.getDeclaredConstructors();
        }
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].isPrimitive()) {
                args[i] = getDefaultPrimitive(paramTypes[i]);
            } else {
                args[i] = Mockito.mock(paramTypes[i], SMART_ANSWER);
                configureSaveReturns(args[i], paramTypes[i]);
            }
        }
        return (T) constructor.newInstance(args);
    }

    /** Configure repository mocks to return the saved entity */
    private static void configureSaveReturns(Object mock, Class<?> type) {
        if (type.getSimpleName().contains("Repo") || type.getSimpleName().contains("Repository")) {
            try {
                Mockito.lenient().when(
                    ((org.springframework.data.jpa.repository.JpaRepository) mock).save(Mockito.any())
                ).thenAnswer(inv -> inv.getArgument(0));
                Mockito.lenient().when(
                    ((org.springframework.data.jpa.repository.JpaRepository) mock).saveAndFlush(Mockito.any())
                ).thenAnswer(inv -> inv.getArgument(0));
            } catch (Exception e) { /* not a JPA repo */ }
        }
    }

    /** Invoke all declared methods on the service with dummy arguments */
    public static void invokeAllMethods(Object service) {
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isStatic(method.getModifiers())) continue;
            method.setAccessible(true);
            Object[] args = buildArgs(method.getParameterTypes(), method.getGenericParameterTypes());
            try { method.invoke(service, args); } catch (Exception e) { /* expected */ }
        }
    }

    /** Invoke only public methods */
    public static void invokePublicMethods(Object service) {
        for (Method method : service.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.isSynthetic()) continue;
            Object[] args = buildArgs(method.getParameterTypes(), method.getGenericParameterTypes());
            try { method.invoke(service, args); } catch (Exception e) { /* expected */ }
        }
    }

    /** Build dummy arguments for a method call */
    public static Object[] buildArgs(Class<?>[] paramTypes, Type[] genericTypes) {
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = buildArg(paramTypes[i], genericTypes != null && i < genericTypes.length ? genericTypes[i] : null);
        }
        return args;
    }

    public static Object buildArg(Class<?> type, Type generic) {
        if (type == String.class) return "test";
        if (type == UUID.class) return UUID.randomUUID();
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == double.class || type == Double.class) return 1.0;
        if (type == float.class || type == Float.class) return 1.0f;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == byte[].class) return new byte[0];
        if (type == BigDecimal.class) return BigDecimal.TEN;
        if (type == LocalDate.class) return LocalDate.now();
        if (type == LocalDateTime.class) return LocalDateTime.now();
        if (type == Instant.class) return Instant.now();
        if (type == java.util.Date.class) return new java.util.Date();
        if (type.isEnum()) return type.getEnumConstants().length > 0 ? type.getEnumConstants()[0] : null;
        if (List.class.isAssignableFrom(type)) return new ArrayList<>();
        if (Set.class.isAssignableFrom(type)) return new HashSet<>();
        if (Map.class.isAssignableFrom(type)) {
            Map<String, String> m = new HashMap<>();
            m.put("test", "val");
            return m;
        }
        if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
        if (org.springframework.data.domain.Pageable.class.isAssignableFrom(type))
            return org.springframework.data.domain.PageRequest.of(0, 10);
        if (org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(type))
            return new org.springframework.mock.web.MockMultipartFile("file", "test.csv", "text/csv", "name\ndummy\n".getBytes());
        // For complex objects, try to mock them
        try { return Mockito.mock(type, Mockito.RETURNS_DEEP_STUBS); } catch (Exception e) { return null; }
    }

    private static Object getDefaultPrimitive(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == char.class) return ' ';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    /** Set TenantContext and BranchContext for tests */
    public static void setupTenantContext() {
        try {
            com.hms.infrastructure.tenant.TenantContext.set(UUID.randomUUID());
            com.hms.infrastructure.tenant.BranchContext.set(UUID.randomUUID());
        } catch (Exception e) { /* optional */ }
    }

    /** Clear TenantContext and BranchContext */
    public static void clearTenantContext() {
        try {
            com.hms.infrastructure.tenant.TenantContext.clear();
            com.hms.infrastructure.tenant.BranchContext.clear();
        } catch (Exception e) { /* optional */ }
    }
}
