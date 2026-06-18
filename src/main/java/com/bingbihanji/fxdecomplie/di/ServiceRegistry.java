package com.bingbihanji.fxdecomplie.di;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ServiceRegistry {

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        T instance = (T) singletons.get(type);
        if (instance != null) return instance;
        return (T) singletons.computeIfAbsent(type, k -> {
            Supplier<?> factory = factories.get(k);
            if (factory == null) {
                throw new IllegalStateException("No service registered for: " + k.getName());
            }
            return factory.get();
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> type) {
        Supplier<?> factory = factories.get(type);
        if (factory == null) {
            throw new IllegalStateException("No factory registered for: " + type.getName());
        }
        return (T) factory.get();
    }

    public <T> void registerSingleton(Class<T> type, T instance) {
        singletons.put(type, instance);
    }

    public <T> void registerFactory(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }
}
