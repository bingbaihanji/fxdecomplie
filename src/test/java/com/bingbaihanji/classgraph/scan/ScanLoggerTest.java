package com.bingbaihanji.classgraph.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanLogger")
class ScanLoggerTest {

    @Nested
    @DisplayName("Null Object")
    class NullObject {

        @Test
        @DisplayName("noOp logger does not throw")
        void noOpDoesNotThrow() {
            ScanLogger logger = ScanLogger.noOp();
            assertDoesNotThrow(() -> {
                logger.debug("test");
                logger.info("test {}", "arg");
                logger.warn("test");
                logger.error("test");
            });
        }
    }

    @Nested
    @DisplayName("In-memory capture")
    class InMemoryCapture {

        private static class CapturingLogger implements ScanLogger {
            final List<String> messages = new ArrayList<>();

            @Override
            public void log(Level level, String message, Object... args) {
                messages.add("[" + level + "] " + message);
            }
        }

        @Test
        @DisplayName("captures all log levels")
        void capturesAllLevels() {
            CapturingLogger capturer = new CapturingLogger();
            capturer.debug("debug msg");
            capturer.info("info msg");
            capturer.warn("warn msg");
            capturer.error("error msg");

            assertEquals(4, capturer.messages.size());
            assertTrue(capturer.messages.get(0).startsWith("[DEBUG]"));
            assertTrue(capturer.messages.get(1).startsWith("[INFO]"));
            assertTrue(capturer.messages.get(2).startsWith("[WARN]"));
            assertTrue(capturer.messages.get(3).startsWith("[ERROR]"));
        }

        @Test
        @DisplayName("lazy supplier only evaluates when level enabled")
        void lazySupplierConditional() {
            CapturingLogger capturer = new CapturingLogger() {
                @Override
                public boolean isEnabled(Level level) {
                    return level == Level.ERROR;
                }
            };
            AtomicInteger evalCount = new AtomicInteger();

            java.util.function.Supplier<String> msgSupplier =
                () -> { evalCount.incrementAndGet(); return "should not log"; };
            capturer.debugLazy(msgSupplier);
            assertEquals(0, evalCount.get(), "Supplier should not be evaluated for disabled level");

            java.util.function.Supplier<String> errorMsg =
                () -> { evalCount.incrementAndGet(); return "should log"; };
            capturer.debugLazy(errorMsg);
            assertEquals(0, evalCount.get(),
                "Supplier should NOT be evaluated for disabled DEBUG level");
            // ERROR level is enabled, so direct log call should work
            capturer.log(ScanLogger.Level.ERROR, errorMsg.get());
            assertEquals(1, evalCount.get(),
                "Supplier should be evaluated once for enabled level");
        }
    }

    @Nested
    @DisplayName("SLF4J adapter")
    class Slf4jAdapter {

        private static final Logger log = LoggerFactory.getLogger(ScanLoggerTest.class);

        @Test
        @DisplayName("SLF4J adapter does not throw")
        void slf4jAdapter() {
            ScanLogger logger = ScanLogger.slf4j(log);
            assertDoesNotThrow(() -> {
                logger.info("Test from ScanLogger SLF4J adapter");
            });
        }
    }

    @Nested
    @DisplayName("Formatting")
    class Formatting {

        @Test
        @DisplayName("message without args is unchanged")
        void messageWithoutArgs() {
            List<String> captured = new ArrayList<>();
            ScanLogger logger = (level, msg, args) -> captured.add(msg);

            logger.info("Hello World");
            assertEquals("Hello World", captured.get(0));
        }

        @Test
        @DisplayName("message with single arg is formatted")
        void messageWithSingleArg() {
            List<String> captured = new ArrayList<>();
            ScanLogger logger = (level, msg, args) -> captured.add(msg);

            logger.info("Hello {}", "World");
            assertTrue(captured.get(0).contains("Hello"));
        }
    }

    @Nested
    @DisplayName("Level ordering")
    class LevelOrdering {

        @Test
        @DisplayName("DEBUG < INFO < WARN < ERROR")
        void levelOrdering() {
            assertTrue(ScanLogger.Level.DEBUG.ordinal() < ScanLogger.Level.INFO.ordinal());
            assertTrue(ScanLogger.Level.INFO.ordinal() < ScanLogger.Level.WARN.ordinal());
            assertTrue(ScanLogger.Level.WARN.ordinal() < ScanLogger.Level.ERROR.ordinal());
        }
    }
}
