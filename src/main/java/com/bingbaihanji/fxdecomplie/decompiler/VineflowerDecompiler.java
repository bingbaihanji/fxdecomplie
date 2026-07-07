package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Vineflower 反编译引擎适配器（使用 IContextSource API,无废弃 API）
 *
 * @author bingbaihanji
 * @date 2026-06-24
 */
public class VineflowerDecompiler implements Decompiler {

    private static final Logger log = LoggerFactory.getLogger(VineflowerDecompiler.class);

    /** Vineflower 默认反编译选项 */
    private static final Map<String, Object> DEFAULT_OPTIONS = createDefaultOptions();

    /** @return 基于 Vineflower 默认值构建的增强默认选项（开启枚举、泛型、内部类等反编译特性） */
    private static Map<String, Object> createDefaultOptions() {
        Map<String, Object> opts = new HashMap<>(IFernflowerPreferences.DEFAULTS);
        opts.put(IFernflowerPreferences.DECOMPILE_ENUM, "1");
        opts.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        opts.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
        opts.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
        opts.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        opts.put(IFernflowerPreferences.BOOLEAN_TRUE_ONE, "1");
        opts.put(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT, "0");
        opts.put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1");
        opts.put(IFernflowerPreferences.USE_METHOD_PARAMETERS, "1");
        opts.put(IFernflowerPreferences.FINALLY_DEINLINE, "1");
        opts.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "0");
        opts.put(IFernflowerPreferences.REMOVE_EMPTY_RANGES, "1");
        opts.put(IFernflowerPreferences.REMOVE_GET_CLASS_NEW, "1");
        opts.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
        opts.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
        opts.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "60");
        opts.put(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS, "0");
        opts.put(IFernflowerPreferences.INDENT_STRING, "    ");
        opts.put(IFernflowerPreferences.LOG_LEVEL, "ERROR");
        opts.put(IFernflowerPreferences.PREFERRED_LINE_LENGTH, "130");
        opts.put(IFernflowerPreferences.PATTERN_MATCHING, "1");
        opts.put(IFernflowerPreferences.SWITCH_EXPRESSIONS, "1");
        opts.put(IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1");
        opts.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "0");
        return Collections.unmodifiableMap(opts);
    }

    /**
     * 合并默认选项与用户上下文选项
     * 若启用了 dump original lines,自动连带启用 source file comments
     *
     * @param context 反编译上下文（可为 null）
     * @return 合并后的不可变选项映射
     */
    private static Map<String, Object> mergedOptions(DecompilerContext context) {
        if (context == null || !context.hasOptions()) {
            log.debug("mergedOptions: using DEFAULT_OPTIONS only ({} entries)", DEFAULT_OPTIONS.size());
            return DEFAULT_OPTIONS;
        }
        Map<String, Object> merged = new HashMap<>(DEFAULT_OPTIONS);
        Map<String, Object> userOptions = normalizedOptions(context.options());
        userOptions.forEach(merged::put);
        if (isEnabled(userOptions.get(IFernflowerPreferences.DUMP_ORIGINAL_LINES))) {
            merged.put(IFernflowerPreferences.SOURCE_FILE_COMMENTS, "1");
        }
        log.debug("mergedOptions: DEFAULT + {} user options = {} total",
                context.options().size(), merged.size());
        return Collections.unmodifiableMap(merged);
    }

    /**
     * 将用户选项规格化为 Vineflower 识别的格式
     * 包括：别名映射（通过 {@link DecompilerOptions#VINEFLOWER_OPTION_ALIASES}）、
     * 布尔值转换为 "1"/"0"
     */
    private static Map<String, Object> normalizedOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        options.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            normalized.put(DecompilerOptions.VINEFLOWER_OPTION_ALIASES.getOrDefault(key, key), normalizeOptionValue(value));
        });
        return normalized;
    }

    /** 将布尔字符串转换为 Vineflower 的 "1"/"0" 格式,其他值原样返回 */
    private static String normalizeOptionValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "1";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "0";
        }
        return value;
    }

    /** 判断选项值是否为启用状态（"1" 或 "true"） */
    private static boolean isEnabled(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    // ==================== Decompiler 接口实现 ====================

    /**
     * 通过解析 class 字节码获取准确的内部类名
     * 若解析失败则回退到对传入 typeName 做规格化处理
     */
    private static String effectiveTypeName(String typeName, byte[] classBytes) {
        return ClassFileParser.tryParse(classBytes)
                .map(metadata -> metadata.internalName().replace('\\', '/'))
                .orElseGet(() -> DecompilerContext.normalizeInternalName(
                        typeName == null ? "" : typeName));
    }

    /** 使用空上下文反编译,自动规格化文件路径为内部类名 */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, DecompilerContext.EMPTY);
    }

    /** 使用空上下文反编译给定内部类名 */
    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    /** 带上下文的文件路径反编译,自动规格化后委托给 {@link #decompileType} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes, DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    /**
     * 使用 Vineflower 引擎反编译指定类
     *
     * <p>核心流程：创建 {@link SingleClassContextSource} 作为字节码来源（无需临时文件）,
     * 通过 {@link IResultSaver} 收集反编译输出到 StringBuilder,
     * 调用 {@link BaseDecompiler#decompileContext()} 执行反编译</p>
     *
     * @param typeName   类的内部名称（如 {@code com/example/MyClass}）
     * @param classBytes 类的原始字节码（会被 clone 一份）
     * @param context    反编译上下文（可为 null,用于解析依赖类字节码和传递选项）
     * @return 反编译后的 Java 源码字符串；若结果为空则返回带说明的错误注释；异常时返回错误信息注释
     */
    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        final StringBuilder result = new StringBuilder();
        final DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        final String effectiveTypeName = effectiveTypeName(typeName, classBytes);
        final byte[] bytes = classBytes.clone();

        try {
            // 单 class 的 IContextSource：直接从内存字节数组提供字节码,无需临时文件
            IContextSource source = new SingleClassContextSource(effectiveTypeName, bytes, effectiveContext);

            // 收集反编译输出的 IResultSaver：将 class 源码追加到 StringBuilder
            IResultSaver resultSaver = new IResultSaver() {
                @Override
                public void saveFolder(String path) {
                }

                @Override
                public void copyFile(String source, String path, String entryName) {
                }

                @Override
                public void saveClassFile(String path, String qualifiedName, String entryName,
                                          String content, int[] mapping) {
                    if (content != null && !content.isEmpty()) {
                        result.append(content);
                    }
                }

                @Override
                public void createArchive(String path, String archiveName, Manifest manifest) {
                }

                @Override
                public void saveDirEntry(String path, String archiveName, String entryName) {
                }

                @Override
                public void copyEntry(String source, String path, String archiveName, String entry) {
                }

                @Override
                public void saveClassEntry(String path, String archiveName, String qualifiedName,
                                           String entryName, String content) {
                    if (content != null && !content.isEmpty()) {
                        result.append(content);
                    }
                }

                @Override
                public void closeArchive(String path, String archiveName) {
                }
            };

            // 将 Vineflower 日志桥接到 SLF4J,按严重级别映射
            IFernflowerLogger fernflowerLogger = new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {
                    switch (severity) {
                        case ERROR -> log.error("[Vineflower] {}", message);
                        case WARN -> log.warn("[Vineflower] {}", message);
                        case INFO -> log.debug("[Vineflower] {}", message);
                        case TRACE -> log.trace("[Vineflower] {}", message);
                    }
                }

                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {
                    switch (severity) {
                        case ERROR -> log.error("[Vineflower] {}", message, t);
                        case WARN -> log.warn("[Vineflower] {}", message, t);
                        case INFO -> log.debug("[Vineflower] {}", message, t);
                        case TRACE -> log.trace("[Vineflower] {}", message, t);
                    }
                }
            };

            // 使用非废弃构造器：不需要 IBytecodeProvider
            log.debug("Vineflower decompile: class={}, options={}", effectiveTypeName,
                    effectiveContext.hasOptions() ? effectiveContext.options().size() : 0);
            long start = System.currentTimeMillis();
            BaseDecompiler decompiler = new BaseDecompiler(resultSaver,
                    mergedOptions(effectiveContext), fernflowerLogger);
            decompiler.addSource(source);
            decompiler.decompileContext();

            String decompiled = result.toString();
            long elapsed = System.currentTimeMillis() - start;
            if (decompiled.isEmpty()) {
                log.warn("Vineflower decompile returned empty: {} ({}ms)", effectiveTypeName, elapsed);
                return "// Vineflower decompile failed\n// Class: " + effectiveTypeName;
            }
            log.debug("Vineflower decompile OK: {} ({}ms, {} chars)", effectiveTypeName, elapsed,
                    decompiled.length());
            return decompiled;
        } catch (Exception e) {
            log.error("Vineflower decompile exception: {}: {}", effectiveTypeName, e.getMessage());
            return "// Vineflower Error: " + e.getMessage();
        }
    }

    /** @return 引擎类型 {@link DecompilerTypeEnum#VINEFLOWER} */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.VINEFLOWER;
    }

    /** @return Vineflower 默认反编译选项的字符串映射（值由 Object 转为 String） */
    @Override
    public Map<String, String> getDefaultOptions() {
        Map<String, String> stringOpts = new LinkedHashMap<>();
        DEFAULT_OPTIONS.forEach((k, v) -> stringOpts.put(k, String.valueOf(v)));
        return stringOpts;
    }

    // ==================== IContextSource 实现（单 class 服务） ====================

    /**
     * 为单个 class 提供字节码的 IContextSource
     * 无需写临时文件,直接从内存中的 bytes 创建 InputStream
     */
    private static final class SingleClassContextSource implements IContextSource {
        private final String typeName;
        private final byte[] bytes;
        private final DecompilerContext context;

        /** @param typeName 主类的内部名称 */
        SingleClassContextSource(String typeName, byte[] bytes, DecompilerContext context) {
            this.typeName = typeName;
            this.bytes = bytes;
            this.context = context;
        }

        /** @return 上下文源名称,用于 Vineflower 日志标识 */
        @Override
        public String getName() {
            return "fxdecomplie";
        }

        /** @return 只包含当前主类的 Entries,无目录和其他文件 */
        @Override
        public Entries getEntries() {
            Entries entries = new Entries(
                    List.of(new Entry(typeName, Entry.BASE_VERSION)),
                    List.of(),
                    List.of()
            );
            log.debug("Vineflower getEntries: class={}", typeName);
            return entries;
        }

        /** 解析资源名对应的字节码并返回输入流,解析失败返回 null */
        @Override
        public InputStream getInputStream(String resource) throws IOException {
            byte[] resolved = resolveClassBytes(resource);
            if (resolved != null) {
                log.debug("Vineflower getInputStream [CLASS] resource={} -> hit", resource);
                return new ByteArrayInputStream(resolved);
            }
            log.debug("Vineflower getInputStream [MISS] resource={}", resource);
            return null;
        }

        /** 检查指定类名是否存在（主类或依赖类） */
        @Override
        public boolean hasClass(String className) throws IOException {
            return resolveClassBytes(className) != null;
        }

        /** 获取指定类的字节码（主类或依赖类） */
        @Override
        public byte[] getClassBytes(String className) throws IOException {
            return resolveClassBytes(className);
        }

        /**
         * 解析类字节码的核心方法
         * 首先检查是否为主类（多种路径格式）,
         * 若不是则去除 .class 后缀和开头的 "/" 后再匹配主类,
         * 最后从上下文解析依赖类字节码
         */
        private byte[] resolveClassBytes(String resource) {
            if (resource == null || resource.isBlank()) {
                return null;
            }
            String normalized = resource.replace('\\', '/');
            if (isMainClass(normalized)) {
                return bytes;
            }

            String internalName = normalized;
            if (internalName.endsWith(IContextSource.CLASS_SUFFIX)) {
                internalName = internalName.substring(0,
                        internalName.length() - IContextSource.CLASS_SUFFIX.length());
            }
            if (internalName.startsWith("/")) {
                internalName = internalName.substring(1);
            }
            if (isMainClass(internalName)) {
                return bytes;
            }

            byte[] dep = context.resolveClassBytes(internalName);
            return dep;
        }

        /** 检查请求的路径是否匹配当前反编译的主类 */
        private boolean isMainClass(String resource) {
            String entryPath = typeName + IContextSource.CLASS_SUFFIX;
            return resource.equals(typeName)
                    || resource.equals("/" + typeName)
                    || resource.equals(entryPath)
                    || resource.equals("/" + entryPath);
        }

        /** 创建输出接收器,将 Vineflower 输出委托给外部的 {@link IResultSaver} */
        @Override
        public IOutputSink createOutputSink(IResultSaver saver) {
            // 将 Vineflower 输出委托给全局 IResultSaver（即 decompileType 中的匿名实现）
            return new IOutputSink() {
                @Override
                public void begin() {
                }

                @Override
                public void acceptClass(String qualifiedName, String fileName,
                                        String content, int[] mapping) {
                    saver.saveClassFile("", qualifiedName, fileName, content, mapping);
                }

                @Override
                public void acceptDirectory(String directory) {
                }

                @Override
                public void acceptOther(String path) {
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
