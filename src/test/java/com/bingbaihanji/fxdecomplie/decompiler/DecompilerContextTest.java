package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DecompilerContext 单元测试 —— 验证工作区隔离的依赖类字节码解析
 *
 * @author bingbaihanji
 * @date 2026-06-27
 */
class DecompilerContextTest {

    @Test
    void resolvesClassBytesFromProvider() {
        byte[] workspaceBytes = {1, 2, 3};

        DecompilerContext context = DecompilerContext.of(internalName ->
                "com/example/Demo".equals(internalName) ? workspaceBytes : null);

        assertArrayEquals(workspaceBytes,
                context.resolveClassBytes("com/example/Demo.class"));
    }

    @Test
    void emptyContextReturnsNull() {
        assertNull(DecompilerContext.EMPTY.resolveClassBytes("com/example/NotFound.class"));
    }

    @Test
    void resolvesClassBytesWithAutoNormalization() {
        byte[] workspaceBytes = {7, 8, 9};

        DecompilerContext context = DecompilerContext.of(internalName ->
                "com/example/Foo".equals(internalName) ? workspaceBytes : null);

        // 带 .class 后缀和反斜杠的路径应自动归一化
        assertArrayEquals(workspaceBytes,
                context.resolveClassBytes("com\\example\\Foo.class"));
    }

    @Test
    void carriesImmutableDecompilerOptions() {
        Map<String, String> options = new java.util.HashMap<>();
        options.put("silent", "true");
        DecompilerContext context = DecompilerContext.withOptions(options);
        options.put("silent", "false");

        assertEquals("true", context.option("silent", "false"));
    }
}
