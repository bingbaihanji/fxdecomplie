package com.bingbaihanji.classgraph.scan.pipeline;

import com.bingbaihanji.classgraph.scan.config.ScanOptions;
import com.bingbaihanji.classgraph.scan.config.ScanOptionsBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanPipeline")
class ScanPipelineTest {

    @Nested
    @DisplayName("Basic pipeline mechanics")
    class BasicMechanics {

        @Test
        @DisplayName("single step pipeline executes")
        void singleStepPipeline() throws Exception {
            ScanStep<String, Integer> countChars = (input, ctx) -> input.length();

            ScanPipeline<String, Integer> pipeline = ScanPipeline
                .<String, Integer>startingWith(countChars)
                .build();

            ScanContext ctx = createTestContext();
            int result = pipeline.execute("hello", ctx);
            assertEquals(5, result);
        }

        @Test
        @DisplayName("multi-step pipeline chains correctly")
        void multiStepPipeline() throws Exception {
            ScanStep<String, Integer> step1 = (input, ctx) -> input.length();
            ScanStep<Integer, String> step2 = (input, ctx) -> "Length: " + input;

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, Integer>startingWith(step1)
                .then(step2)
                .build();

            ScanContext ctx = createTestContext();
            String result = pipeline.execute("hello world", ctx);
            assertEquals("Length: 11", result);
        }

        @Test
        @DisplayName("pipeline step order is preserved")
        void stepOrderPreserved() throws Exception {
            List<String> executionLog = new ArrayList<>();
            ScanStep<String, String> stepA = (input, ctx) -> {
                executionLog.add("A"); return input + "A";
            };
            ScanStep<String, String> stepB = (input, ctx) -> {
                executionLog.add("B"); return input + "B";
            };
            ScanStep<String, String> stepC = (input, ctx) -> {
                executionLog.add("C"); return input + "C";
            };

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, String>startingWith(stepA)
                .then(stepB)
                .then(stepC)
                .build();

            pipeline.execute("", createTestContext());
            assertEquals(List.of("A", "B", "C"), executionLog);
            assertEquals(3, pipeline.stepCount());
            assertEquals(3, pipeline.stepNames().size());
            assertTrue(pipeline.stepNames().get(0).contains("Lambda"),
                "Lambda step name should contain 'Lambda'");
        }

        @Test
        @DisplayName("context carries state between steps")
        void contextCarriesState() throws Exception {
            ScanStep<String, String> storeStep = (input, ctx) -> {
                ctx.put("original", input);
                return input.toUpperCase();
            };
            ScanStep<String, String> retrieveStep = (input, ctx) -> {
                String original = ctx.get("original");
                return input + " (was: " + original + ")";
            };

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, String>startingWith(storeStep)
                .then(retrieveStep)
                .build();

            String result = pipeline.execute("test", createTestContext());
            assertEquals("TEST (was: test)", result);
        }
    }

    @Nested
    @DisplayName("Step lifecycle")
    class StepLifecycle {

        @Test
        @DisplayName("skips step when shouldSkip returns true")
        void skipsStep() throws Exception {
            AtomicInteger executed = new AtomicInteger();

            ScanStep<String, String> skipStep = new ScanStep<>() {
                @Override
                public String execute(String input, ScanContext ctx) {
                    executed.incrementAndGet();
                    return input;
                }

                @Override
                public boolean shouldSkip(ScanContext ctx) {
                    return true;
                }
            };

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, String>startingWith(skipStep)
                .build();

            pipeline.execute("input", createTestContext());
            assertEquals(0, executed.get(), "Step should have been skipped");
        }

        @Test
        @DisplayName("step name is non-null and non-empty")
        void defaultStepName() {
            ScanStep<String, Integer> step = (input, ctx) -> input.length();
            assertNotNull(step.name());
            assertFalse(step.name().isBlank());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws when first step is null")
        void nullFirstStepThrows() {
            assertThrows(NullPointerException.class, () -> {
                ScanPipeline.<String, String>startingWith(null);
            });
        }

        @Test
        @DisplayName("propagates step exceptions")
        void propagatesExceptions() {
            ScanStep<String, String> failingStep = (input, ctx) -> {
                throw new RuntimeException("Step failed");
            };

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, String>startingWith(failingStep)
                .build();

            assertThrows(RuntimeException.class, () ->
                pipeline.execute("input", createTestContext()));
        }

        @Test
        @DisplayName("checks interruption before each step")
        void checksInterruption() {
            AtomicInteger stepsRun = new AtomicInteger();
            ScanStep<String, String> step1 = (input, ctx) -> {
                stepsRun.incrementAndGet();
                ctx.cancel(); // 取消后续步骤
                return input;
            };
            ScanStep<String, String> step2 = (input, ctx) -> {
                stepsRun.incrementAndGet();
                return input;
            };

            ScanPipeline<String, String> pipeline = ScanPipeline
                .<String, String>startingWith(step1)
                .then(step2)
                .build();

            assertThrows(InterruptedException.class, () ->
                pipeline.execute("input", createTestContext()));
            assertEquals(1, stepsRun.get(), "Only first step should run");
        }
    }

    @Nested
    @DisplayName("Context operations")
    class ContextOperations {

        @Test
        @DisplayName("put and get type-safe values")
        void putAndGet() {
            ScanContext ctx = createTestContext();
            ctx.put("string", "value");
            ctx.put("number", 42);

            assertEquals("value", ctx.get("string"));
            assertEquals(42, (Integer) ctx.get("number"));
        }

        @Test
        @DisplayName("getOrThrow throws for missing keys")
        void getOrThrowForMissing() {
            ScanContext ctx = createTestContext();
            assertThrows(IllegalStateException.class, () ->
                ctx.getOrThrow("nonexistent"));
        }

        @Test
        @DisplayName("put returns previous value")
        void putReturnsPrevious() {
            ScanContext ctx = createTestContext();
            assertNull(ctx.put("key", "first"));
            assertEquals("first", ctx.put("key", "second"));
        }
    }

    private static ScanContext createTestContext() {
        return ScanPipeline.createContext(
            ScanOptions.DEFAULT,
            Executors.newSingleThreadExecutor(),
            null,
            1
        );
    }
}
