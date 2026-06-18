package com.bingbihanji.fxdecomplie.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> void publish(T event) {
        List<Consumer<?>> subs = listeners.get(event.getClass());
        if (subs != null) {
            for (Consumer<?> sub : subs) {
                ((Consumer<T>) sub).accept(event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> void unsubscribe(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> subs = listeners.get(type);
        if (subs != null) {
            subs.remove(listener);
        }
    }
}
