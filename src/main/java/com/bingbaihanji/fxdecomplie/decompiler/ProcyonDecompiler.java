package com.bingbaihanji.fxdecomplie.decompiler;

import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.Languages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Procyon 反编译引擎适配器
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class ProcyonDecompiler implements Decompiler {

    private static final Logger logger = LoggerFactory.getLogger(ProcyonDecompiler.class);

    private static void applyOptions(DecompilerSettings settings, DecompilerContext context) {
        if (context == null || !context.hasOptions()) {
            return;
        }
        for (var entry : context.options().entrySet()) {
            String value = entry.getValue();
            switch (entry.getKey()) {
                // 常用
                case "unicodeOutput", "unicodeOutputEnabled" ->
                        settings.setUnicodeOutputEnabled(Boolean.parseBoolean(value));
                case "previewFeatures", "previewFeaturesEnabled" ->
                        settings.setPreviewFeaturesEnabled(Boolean.parseBoolean(value));
                case "showSyntheticMembers" -> settings.setShowSyntheticMembers(Boolean.parseBoolean(value));
                case "forceExplicitImports" -> settings.setForceExplicitImports(Boolean.parseBoolean(value));
                case "forceExplicitTypeArguments" ->
                        settings.setForceExplicitTypeArguments(Boolean.parseBoolean(value));
                case "flattenSwitchBlocks" -> settings.setFlattenSwitchBlocks(Boolean.parseBoolean(value));
                case "retainRedundantCasts" -> settings.setRetainRedundantCasts(Boolean.parseBoolean(value));
                case "includeLineNumbersInBytecode" ->
                        settings.setIncludeLineNumbersInBytecode(Boolean.parseBoolean(value));
                case "showDebugLineNumbers" -> settings.setShowDebugLineNumbers(Boolean.parseBoolean(value));
                case "simplifyMemberReferences" -> settings.setSimplifyMemberReferences(Boolean.parseBoolean(value));
                // 高级
                case "alwaysGenerateExceptionVariableForCatchBlocks" ->
                        settings.setAlwaysGenerateExceptionVariableForCatchBlocks(Boolean.parseBoolean(value));
                case "forceFullyQualifiedReferences" ->
                        settings.setForceFullyQualifiedReferences(Boolean.parseBoolean(value));
                case "excludeNestedTypes" -> settings.setExcludeNestedTypes(Boolean.parseBoolean(value));
                case "retainPointlessSwitches" -> settings.setRetainPointlessSwitches(Boolean.parseBoolean(value));
                case "includeErrorDiagnostics" -> settings.setIncludeErrorDiagnostics(Boolean.parseBoolean(value));
                case "mergeVariables" -> settings.setMergeVariables(Boolean.parseBoolean(value));
                case "disableForEachTransforms" -> settings.setDisableForEachTransforms(Boolean.parseBoolean(value));
                case "textBlockLineMinimum" -> {
                    try {
                        settings.setTextBlockLineMinimum(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // 非数字值安全忽略，使用 Procyon 默认值
                    }
                }
                case "languageTarget" -> {
                    if (value != null && !value.isBlank()) {
                        settings.setLanguage(com.strobel.decompiler.languages.Languages.java());
                    }
                }
                default -> {
                    // Unknown Procyon option — silently ignored
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = DecompilerContext.normalizeInternalName(classFilePath);
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
        String internalName = DecompilerContext.normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        String internalName = DecompilerContext.normalizeInternalName(typeName);
        DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        DecompilerSettings localSettings = DecompilerSettings.javaDefaults();
        localSettings.setTypeLoader(new CachedTypeLoader(internalName, classBytes, effectiveContext));
        localSettings.setLanguage(Languages.java());
        localSettings.setUnicodeOutputEnabled(true);
        localSettings.setPreviewFeaturesEnabled(true);
        applyOptions(localSettings, effectiveContext);

        logger.debug("Procyon decompile: class={}", internalName);
        long start = System.currentTimeMillis();
        MetadataSystem metadataSystem = new MetadataSystem(localSettings.getTypeLoader());
        TypeReference type = metadataSystem.lookupType(internalName);
        if (type == null) {
            logger.warn("Procyon lookupType returned null: {}", internalName);
            return "// Procyon decompile failed\n// Class: " + internalName;
        }
        TypeDefinition resolvedType = type.resolve();
        if (resolvedType == null) {
            logger.warn("Procyon resolve returned null: {}", internalName);
            return "// Procyon decompile failed\n// Class: " + internalName;
        }

        DecompilationOptions options = new DecompilationOptions();
        options.setSettings(localSettings);
        options.setFullDecompilation(true);

        try (StringWriter writer = new StringWriter()) {
            PlainTextOutput output = new PlainTextOutput(writer);
            localSettings.getLanguage().decompileType(resolvedType, output, options);
            String result = writer.toString();
            long elapsed = System.currentTimeMillis() - start;
            logger.debug("Procyon decompile OK: {} ({}ms, {} chars)", internalName, elapsed, result.length());
            return result;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            logger.error("Procyon decompile failed: {} ({}ms): {}", internalName, elapsed, e.getMessage());
            return "// Procyon decompile failed\n// Class: " + internalName + "\n// Error: " + e.getMessage();
        }
    }

    /** @return 引擎类型 PROCYON */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.PROCYON;
    }

    /** Procyon 类型加载器,优先从缓存获取字节码 */
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
                if (targetBytes == null) {
                    return false;
                }
                bytes = targetBytes;
            }
            if (bytes == null) {
                bytes = context.resolveClassBytes(DecompilerContext.normalizeInternalName(normalized));
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
