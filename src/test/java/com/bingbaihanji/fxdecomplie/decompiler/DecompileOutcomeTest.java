package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DecompileOutcome.classify")
class DecompileOutcomeTest {

    @Nested
    @DisplayName("成功分类")
    class SuccessClassify {

        @Test
        @DisplayName("正常源码应分类为 Success")
        void shouldClassifyNormalSourceAsSuccess() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    "package com.example;\npublic class Test {}",
                    DecompilerTypeEnum.JADX);
            assertTrue(outcome.isSuccess());
            assertInstanceOf(DecompileOutcome.Success.class, outcome);
        }

        @Test
        @DisplayName("空行开头的源码应分类为 Success")
        void shouldClassifySourceWithLeadingWhitespace() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    "\n\npackage com.example;\npublic class Test {}",
                    DecompilerTypeEnum.VINEFLOWER);
            assertTrue(outcome.isSuccess());
        }
    }

    @Nested
    @DisplayName("失败分类")
    class FailureClassify {

        @Test
        @DisplayName("null 输入应分类为 ENGINE_ERROR")
        void shouldClassifyNullAsError() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    null, DecompilerTypeEnum.JADX);
            assertTrue(outcome.isFailure());
            DecompileOutcome.Failure f = (DecompileOutcome.Failure) outcome;
            assertEquals(FailureKind.ENGINE_ERROR, f.kind());
        }

        @Test
        @DisplayName("空字符串应分类为 ENGINE_ERROR")
        void shouldClassifyEmptyAsError() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    "", DecompilerTypeEnum.CFR);
            assertTrue(outcome.isFailure());
            DecompileOutcome.Failure f = (DecompileOutcome.Failure) outcome;
            assertEquals(FailureKind.ENGINE_ERROR, f.kind());
        }

        @Test
        @DisplayName("引擎 Error 前缀应分类为 ENGINE_ERROR")
        void shouldClassifyVineflowerErrorPrefix() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    "// Vineflower Error: something went wrong",
                    DecompilerTypeEnum.VINEFLOWER);
            assertTrue(outcome.isFailure());
            DecompileOutcome.Failure f = (DecompileOutcome.Failure) outcome;
            assertEquals(FailureKind.ENGINE_ERROR, f.kind());
            assertEquals(DecompilerTypeEnum.VINEFLOWER, f.engine());
        }

        @Test
        @DisplayName("反编译器正忙应分类为 BUSY")
        void shouldClassifyBusyAsBusy() {
            DecompileOutcome outcome = DecompileOutcome.classify(
                    "反编译器正忙,请稍后重试",
                    DecompilerTypeEnum.JADX);
            assertTrue(outcome.isFailure());
            DecompileOutcome.Failure f = (DecompileOutcome.Failure) outcome;
            assertEquals(FailureKind.BUSY, f.kind());
        }
    }

    @Nested
    @DisplayName("diagnostic()")
    class Diagnostic {

        @Test
        @DisplayName("Failure.diagnostic() 包含 engine/kind/message")
        void shouldContainEngineKindAndMessage() {
            DecompileOutcome.Failure f = new DecompileOutcome.Failure(
                    DecompilerTypeEnum.CFR, FailureKind.CLASS_NOT_FOUND, "类未找到");
            String diag = f.diagnostic();
            assertTrue(diag.contains("CFR"));
            assertTrue(diag.contains("CLASS_NOT_FOUND"));
            assertTrue(diag.contains("类未找到"));
        }
    }
}
