package com.bingbihanji.fxdecomplie.decompiler;

import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.Languages;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Procyon 反编译引擎适配器。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class ProcyonDecompiler implements Decompiler {

    /** {@inheritDoc} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, DecompilerContext.EMPTY);
    }

    /** {@inheritDoc} */
    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        String internalName = normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        String internalName = normalizeInternalName(typeName);
        DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        DecompilerSettings localSettings = DecompilerSettings.javaDefaults();
        localSettings.setTypeLoader(new CachedTypeLoader(internalName, classBytes, effectiveContext));
        localSettings.setLanguage(Languages.java());
        localSettings.setUnicodeOutputEnabled(true);
        localSettings.setPreviewFeaturesEnabled(true);

        MetadataSystem metadataSystem = new MetadataSystem(localSettings.getTypeLoader());
        TypeReference type = metadataSystem.lookupType(internalName);
        if (type == null) {
            return "// Procyon decompile failed\n// Class: " + internalName;
        }
        TypeDefinition resolvedType = type.resolve();
        if (resolvedType == null) {
            return "// Procyon decompile failed\n// Class: " + internalName;
        }

        DecompilationOptions options = new DecompilationOptions();
        options.setSettings(localSettings);
        options.setFullDecompilation(true);

        try (StringWriter writer = new StringWriter()) {
            PlainTextOutput output = new PlainTextOutput(writer);
            localSettings.getLanguage().decompileType(resolvedType, output, options);
            return writer.toString();
        } catch (IOException e) {
            return "// Procyon decompile failed\n// Class: " + internalName + "\n// Error: " + e.getMessage();
        }
    }

    /** @return 引擎类型 PROCYON */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.PROCYON;
    }

    /**
     * 规范化内部名称（反斜杠转正斜杠，去掉 .class 后缀）。
     * @param name 原始名称
     * @return 规范化后的内部名称
     */
    private String normalizeInternalName(String name) {
        String normalized = name.replace('\\', '/');
        return normalized.endsWith(".class")
                ? normalized.substring(0, normalized.length() - 6)
                : normalized;
    }

    /** Procyon 类型加载器，优先从缓存获取字节码 */
    private static class CachedTypeLoader implements ITypeLoader {
        /** 目标类型名 */
        private final String targetName;
        /** 目标字节码 */
        private final byte[] targetBytes;
        /** 当前反编译上下文 */
        private final DecompilerContext context;

        /** @param targetName 目标类型名 @param targetBytes 目标字节码 */
        private CachedTypeLoader(String targetName, byte[] targetBytes,
                                 DecompilerContext context) {
            this.targetName = targetName;
            this.targetBytes = targetBytes;
            this.context = context;
        }

        @Override
        public boolean tryLoadType(String internalName, Buffer buffer) {
            String normalized = internalName.replace('\\', '/');
            byte[] bytes = null;
            if (normalized.equals(targetName) || normalized.equals(targetName + ".class")) {
                bytes = targetBytes;
            }
            if (bytes == null) {
                bytes = context.resolveClassBytes(normalized.replace(".class", ""));
            }
            if (bytes == null) {
                return false;
            }
            buffer.putByteArray(bytes, 0, bytes.length);
            buffer.position(0);
            return true;
        }
    }
}
