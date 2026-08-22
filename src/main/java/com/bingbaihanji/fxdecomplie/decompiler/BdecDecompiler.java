package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.bdec.BdecConfig;
import com.bingbaihanji.bdec.BdecEngine;
import com.bingbaihanji.bdec.BdecResult;

/**
 * bdec 反编译引擎适配器
 *
 * <p>桥接 fxdecomplie {@link Decompiler} 接口与 bdec 的 {@link BdecEngine}.
 * 依赖类字节码通过 fxdecomplie {@link DecompilerContext} 解析.</p>
 *
 * @author bingbaihanji
 * @date 2026-08-17
 */
public final class BdecDecompiler implements Decompiler {

    private static String failureOutput(String typeName, String reason) {
        return "// Bdec Error: " + typeName + "\n// "
                + (reason == null || reason.isBlank() ? "未知错误" : reason);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        if (classBytes == null) {
            return failureOutput(typeName, "类字节码为空");
        }
        try {
            BdecConfig config = BdecConfig.builder().build();
            // 桥接依赖解析: fxdecomplie context → bdec classByteLoader
            var bdecCtx = new com.bingbaihanji.bdec.DecompileContext(
                    config, internalName -> context.getClassBytes(internalName));
            // noop 诊断监听器 (诊断仅记录,不打断反编译)
            BdecResult result = new BdecEngine(config, diag -> {})
                    .decompile(typeName, classBytes, bdecCtx);
            if (!result.success()) {
                Throwable cause = result.cause();
                String reason = cause != null && cause.getMessage() != null
                        ? cause.getMessage() : "反编译失败";
                return failureOutput(typeName, reason);
            }
            return result.decompiledCode();
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName();
            return failureOutput(typeName, reason);
        }
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.BDEC;
    }

    @Override
    public String getName() {
        return "bdec";
    }
}
