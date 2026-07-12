package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecompilerRunnerTest {

    @Test
    void jadxFailureOutputDetectedByStringPrefix() {
        assertTrue(DecompilerRunner.isFailureOutput(
                "// jadx Error: something wrong\n// Class: com/example/Foo"));
        assertTrue(DecompilerRunner.isFailureOutput(
                "// jadx decompile failed: no classes loaded\n// Class: com/example/Foo"));
    }

    @Test
    void jadxSuccessOutputNotDetectedAsFailure() {
        assertFalse(DecompilerRunner.isFailureOutput(
                "package com.example;\n\npublic class Foo {\n}\n"));
    }

    @Test
    void jadxEmptyOrNullOutputDetectedAsFailure() {
        assertTrue(DecompilerRunner.isFailureOutput(null));
        assertTrue(DecompilerRunner.isFailureOutput(""));
        assertTrue(DecompilerRunner.isFailureOutput("   "));
    }
}
