package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.events;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.IJadxEvent;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.IJadxEvents;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.JadxEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class JadxEventsImpl implements IJadxEvents {
    private static final Logger LOG = LoggerFactory.getLogger(JadxEventsImpl.class);

    private final JadxEventsManager manager = new JadxEventsManager();

    @Override
    public void send(IJadxEvent event) {
        if (com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx.isDevVersion()) {
            LOG.debug("Sending event: {}", event);
        }
        manager.send(event);
    }

    @Override
    public <E extends IJadxEvent> void addListener(JadxEventType<E> eventType, Consumer<E> listener) {
        manager.addListener(eventType, listener);
        if (com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx.isDevVersion()) {
            LOG.debug("add listener for: {}, stats: {}", eventType, manager.listenersDebugStats());
        }
    }

    @Override
    public <E extends IJadxEvent> void removeListener(JadxEventType<E> eventType, Consumer<E> listener) {
        manager.removeListener(eventType, listener);
        if (com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx.isDevVersion()) {
            LOG.debug("remove listener for: {}, stats: {}", eventType, manager.listenersDebugStats());
        }
    }

    @Override
    public void reset() {
        manager.reset();
    }
}
