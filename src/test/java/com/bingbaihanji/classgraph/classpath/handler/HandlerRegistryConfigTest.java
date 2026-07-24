package com.bingbaihanji.classgraph.classpath.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HandlerRegistryConfig")
class HandlerRegistryConfigTest {

    @Test
    @DisplayName("defaults creates non-empty handler list")
    void defaultsHasHandlers() {
        HandlerRegistryConfig config = HandlerRegistryConfig.defaults();
        assertFalse(config.effectiveHandlers().isEmpty());
        // 应该包含 URLClassLoaderHandler
        assertTrue(config.effectiveHandlers().stream()
            .anyMatch(h -> h instanceof URLClassLoaderHandler));
    }

    @Test
    @DisplayName("empty creates empty handler list")
    void emptyHasNoHandlers() {
        HandlerRegistryConfig config = HandlerRegistryConfig.empty();
        assertTrue(config.effectiveHandlers().isEmpty());
    }

    @Test
    @DisplayName("withAdditional adds handler to end")
    void withAdditional() {
        HandlerRegistryConfig config = HandlerRegistryConfig.empty()
            .withAdditional(new URLClassLoaderHandler());
        assertEquals(1, config.effectiveHandlers().size());
        assertTrue(config.effectiveHandlers().get(0)
            instanceof URLClassLoaderHandler);
    }

    @Test
    @DisplayName("without excludes specified handler type")
    void withoutExcludesType() {
        HandlerRegistryConfig config = HandlerRegistryConfig.defaults()
            .without(URLClassLoaderHandler.class);

        assertTrue(config.effectiveHandlers().stream()
            .noneMatch(h -> h instanceof URLClassLoaderHandler),
            "URLClassLoaderHandler should be excluded");
    }

    @Test
    @DisplayName("immutability — effectiveHandlers returns new list")
    void immutability() {
        HandlerRegistryConfig config = HandlerRegistryConfig.empty();
        var handlers = config.effectiveHandlers();
        assertThrows(UnsupportedOperationException.class, () ->
            handlers.add(new URLClassLoaderHandler()));
    }

    @Test
    @DisplayName("chained operations are composable")
    void chainedOperations() {
        HandlerRegistryConfig config = HandlerRegistryConfig.empty()
            .withAdditional(new URLClassLoaderHandler())
            .withAdditional(new FallbackClassLoaderHandler());

        assertEquals(2, config.effectiveHandlers().size());
        assertTrue(config.effectiveHandlers().get(0)
            instanceof URLClassLoaderHandler);
        assertTrue(config.effectiveHandlers().get(1)
            instanceof FallbackClassLoaderHandler);
    }

    @Nested
    @DisplayName("Builder immutability")
    class BuilderImmutability {

        @Test
        @DisplayName("original is not affected by modifications")
        void originalUnaffected() {
            HandlerRegistryConfig original = HandlerRegistryConfig.empty()
                .withAdditional(new URLClassLoaderHandler());

            // 修改不会影响 original
            HandlerRegistryConfig modified = original
                .withAdditional(new FallbackClassLoaderHandler());

            assertEquals(1, original.effectiveHandlers().size());
            assertEquals(2, modified.effectiveHandlers().size());
        }
    }
}
