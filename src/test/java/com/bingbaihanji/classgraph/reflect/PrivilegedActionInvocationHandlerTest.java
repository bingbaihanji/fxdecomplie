package com.bingbaihanji.classgraph.reflect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrivilegedActionInvocationHandler")
class PrivilegedActionInvocationHandlerTest {

    @Test
    @DisplayName("invoke delegates to callable")
    void invokeDelegatesToCallable() throws Throwable {
        Callable<String> callable = () -> "result";
        PrivilegedActionInvocationHandler<String> handler =
            new PrivilegedActionInvocationHandler<>(callable);

        Object result = handler.invoke(
            Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Runnable.class}, handler),
            Object.class.getMethod("toString"),
            null);

        assertEquals("result", result);
    }

    @Test
    @DisplayName("propagates callable exception")
    void propagatesException() {
        Callable<String> callable = () -> {
            throw new IllegalArgumentException("test error");
        };
        PrivilegedActionInvocationHandler<String> handler =
            new PrivilegedActionInvocationHandler<>(callable);

        assertThrows(IllegalArgumentException.class, () ->
            handler.invoke(null, null, null));
    }

    @Test
    @DisplayName("constructs with any callable type")
    void constructsWithAnyType() {
        assertNotNull(new PrivilegedActionInvocationHandler<>(() -> 42));
        assertNotNull(new PrivilegedActionInvocationHandler<>(() -> "hello"));
        assertNotNull(new PrivilegedActionInvocationHandler<>(() -> true));
    }
}
