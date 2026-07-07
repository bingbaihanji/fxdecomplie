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

    private static final Logger log = LoggerFactory.getLogger(ProcyonDecompiler.class);

    /**
     * 将上下文中携带的参数应用到 Procyon 反编译设置中
     * <p>遍历 context 中的所有选项键值对,根据 key 名称匹配对应的 setter 方法进行设置
     * 未知的 key 会被安全忽略</p>
     *
     * @param settings Procyon 反编译器设置对象
     * @param context  反编译上下文,可能包含额外参数；为 null 或无参数时直接返回
     */
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
                        // 非数字值安全忽略,使用 Procyon 默认值
                    }
                }
                case "languageTarget" -> {
                    if (value != null && !value.isBlank()) {
                        settings.setLanguage(com.strobel.decompiler.languages.Languages.java());
                    } else {
                        log.warn("未知的 languageTarget 值: {}", value);
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

    /**
     * 使用指定上下文反编译类文件
     *
     * @param classFilePath 类文件路径（可以是文件系统路径或内部名）
     * @param classBytes    类字节码
     * @param context       反编译上下文,用于传递依赖解析器和额外参数
     * @return 反编译后的 Java 源码字符串
     */
    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        String internalName = DecompilerContext.normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    /**
     * 使用指定上下文反编译指定类型
     * <p>这是 Procyon 引擎的核心反编译方法,执行以下步骤：
     * <ol>
     *   <li>规范化类型内部名</li>
     *   <li>创建类型加载器 {@link CachedTypeLoader} 以支持依赖类解析</li>
     *   <li>通过 {@link MetadataSystem} 查找并解析目标类型</li>
     *   <li>执行完整反编译并输出为字符串</li>
     * </ol>
     * 查找或解析失败时返回带错误信息的注释文本,而非抛出异常</p>
     *
     * @param typeName   类型全限定名（内部格式,如 com/example/Foo）
     * @param classBytes 目标类字节码
     * @param context    反编译上下文,可为 null（使用空上下文）
     * @return 反编译后的 Java 源码；失败时返回以 {@code //} 开头的注释文本
     */
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

        log.debug("Procyon decompile: class={}", internalName);
        long start = System.currentTimeMillis();
        MetadataSystem metadataSystem = new MetadataSystem(localSettings.getTypeLoader());
        TypeReference type = metadataSystem.lookupType(internalName);
        if (type == null) {
            log.warn("Procyon lookupType returned null: {}", internalName);
            return "// Procyon decompile failed\n// Class: " + internalName;
        }
        TypeDefinition resolvedType = type.resolve();
        if (resolvedType == null) {
            log.warn("Procyon resolve returned null: {}", internalName);
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
            log.debug("Procyon decompile OK: {} ({}ms, {} chars)", internalName, elapsed, result.length());
            return result;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Procyon decompile failed: {} ({}ms): {}", internalName, elapsed, e.getMessage());
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

        /**
         * 尝试加载指定内部名的类型字节码
         * <p>加载优先级：目标类本身 → 上下文依赖解析这样 Procyon 在反编译时
         * 可以获取到依赖类的字节码信息,从而生成更准确的代码（如泛型参数、方法签名等）</p>
         *
         * @param internalName 类型内部名（如 com/example/Foo）
         * @param buffer       输出缓冲区,成功时写入字节码
         * @return 加载成功返回 true,否则返回 false
         */
        @Override
        public boolean tryLoadType(String internalName, Buffer buffer) {
            String normalized = internalName.replace('\\', '/');
            byte[] bytes = null;
            // 优先匹配目标类本身
            if (normalized.equals(targetName) || normalized.equals(targetName + ".class")) {
                if (targetBytes == null) {
                    return false;
                }
                bytes = targetBytes;
            }
            // 非目标类时从上下文依赖解析获取字节码
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
