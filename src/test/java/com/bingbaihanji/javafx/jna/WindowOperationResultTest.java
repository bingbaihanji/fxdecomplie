package com.bingbaihanji.javafx.jna;

import com.bingbaihanji.windows.platform.WindowOperationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowOperationResultTest {

    @Test
    void combineReportsSuccessWhenAnyOperationSucceeds() {
        WindowOperationResult result = WindowOperationResult.combine("theme", List.of(
                WindowOperationResult.skipped("a", "unsupported"),
                WindowOperationResult.success("b")));

        assertTrue(result.isSuccess());
    }

    @Test
    void combinePreservesFailureWhenAnyOperationFails() {
        WindowOperationResult result = WindowOperationResult.combine("theme", List.of(
                WindowOperationResult.success("a"),
                WindowOperationResult.failed("b", -1, "bad")));

        assertTrue(result.isFailure());
    }
}
