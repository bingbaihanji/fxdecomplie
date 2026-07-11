package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.Map;

/**
 * 单次 jadx 反编译请求 
 */
public record JadxDecompilerRequest(
        String typeName,
        String classFilePath,
        byte[] classBytes,
        DecompilerContext context
) {

    public JadxDecompilerRequest {
        typeName = ClassNameUtil.normalizeInternalName(typeName);
        classFilePath = classFilePath == null ? "" : classFilePath;
        context = context == null ? DecompilerContext.EMPTY : context;
    }

    public Map<String, String> options() {
        return context.options();
    }
}
