package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;

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
 * Vineflower 反编译引擎适配器（使用 IContextSource API，无废弃 API）。
 *
 * @author bingbaihanji
 * @date 2026-06-24
 */
public class VineflowerDecompiler implements Decompiler {

    private static final Logger logger = LoggerFactory.getLogger(VineflowerDecompiler.class);
    private static final Map<String, String> OPTION_ALIASES = Map.ofEntries(
            Map.entry("rbr", IFernflowerPreferences.REMOVE_BRIDGE),
            Map.entry("rsy", IFernflowerPreferences.REMOVE_SYNTHETIC),
            Map.entry("din", IFernflowerPreferences.DECOMPILE_INNER),
            Map.entry("dc4", IFernflowerPreferences.DECOMPILE_CLASS_1_4),
            Map.entry("das", IFernflowerPreferences.DECOMPILE_ASSERTIONS),
            Map.entry("hes", IFernflowerPreferences.HIDE_EMPTY_SUPER),
            Map.entry("hdc", IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR),
            Map.entry("dgs", IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES),
            Map.entry("ner", IFernflowerPreferences.INCORPORATE_RETURNS),
            Map.entry("esm", IFernflowerPreferences.ENSURE_SYNCHRONIZED_MONITOR),
            Map.entry("den", IFernflowerPreferences.DECOMPILE_ENUM),
            Map.entry("dec", IFernflowerPreferences.DECOMPILE_PREVIEW),
            Map.entry("rgn", IFernflowerPreferences.REMOVE_GET_CLASS_NEW),
            Map.entry("lit", IFernflowerPreferences.LITERALS_AS_IS),
            Map.entry("bto", IFernflowerPreferences.BOOLEAN_TRUE_ONE),
            Map.entry("asc", IFernflowerPreferences.ASCII_STRING_CHARACTERS),
            Map.entry("sns", IFernflowerPreferences.SYNTHETIC_NOT_SET),
            Map.entry("uto", IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT),
            Map.entry("udv", IFernflowerPreferences.USE_DEBUG_VAR_NAMES),
            Map.entry("ump", IFernflowerPreferences.USE_METHOD_PARAMETERS),
            Map.entry("rer", IFernflowerPreferences.REMOVE_EMPTY_RANGES),
            Map.entry("fdi", IFernflowerPreferences.FINALLY_DEINLINE),
            Map.entry("lac", IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS),
            Map.entry("bsm", IFernflowerPreferences.BYTECODE_SOURCE_MAPPING),
            Map.entry("dcl", IFernflowerPreferences.DUMP_ORIGINAL_LINES),
            Map.entry(IFernflowerPreferences.DUMP_CODE_LINES, IFernflowerPreferences.DUMP_ORIGINAL_LINES),
            Map.entry("iib", IFernflowerPreferences.IGNORE_INVALID_BYTECODE),
            Map.entry("vac", IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES),
            Map.entry("tcs", IFernflowerPreferences.TERNARY_CONSTANT_SIMPLIFICATION),
            Map.entry("pam", IFernflowerPreferences.PATTERN_MATCHING),
            Map.entry("tlf", IFernflowerPreferences.TRY_LOOP_FIX),
            Map.entry("tco", IFernflowerPreferences.TERNARY_CONDITIONS),
            Map.entry("swi", IFernflowerPreferences.SWITCH_EXPRESSIONS),
            Map.entry("shs", IFernflowerPreferences.SHOW_HIDDEN_STATEMENTS),
            Map.entry("ovr", IFernflowerPreferences.OVERRIDE_ANNOTATION),
            Map.entry("sst", IFernflowerPreferences.SIMPLIFY_STACK_SECOND_PASS),
            Map.entry("vvm", IFernflowerPreferences.VERIFY_VARIABLE_MERGES),
            Map.entry("ega", IFernflowerPreferences.EXPLICIT_GENERIC_ARGUMENTS),
            Map.entry("isl", IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS),
            Map.entry("log", IFernflowerPreferences.LOG_LEVEL),
            Map.entry("mpm", IFernflowerPreferences.MAX_PROCESSING_METHOD),
            Map.entry("pll", IFernflowerPreferences.PREFERRED_LINE_LENGTH),
            Map.entry("ind", IFernflowerPreferences.INDENT_STRING),
            Map.entry("sef", IFernflowerPreferences.SKIP_EXTRA_FILES),
            Map.entry("wia", IFernflowerPreferences.WARN_INCONSISTENT_INNER_CLASSES),
            Map.entry("dbe", IFernflowerPreferences.DUMP_BYTECODE_ON_ERROR),
            Map.entry("dee", IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR),
            Map.entry("dcc", IFernflowerPreferences.DECOMPILER_COMMENTS),
            Map.entry("sfc", IFernflowerPreferences.SOURCE_FILE_COMMENTS),
            Map.entry("ccd", IFernflowerPreferences.DECOMPILE_COMPLEX_CONDYS),
            Map.entry("fji", IFernflowerPreferences.FORCE_JSR_INLINE)
    );

    /** Vineflower 默认反编译选项 */
    private static final Map<String, Object> DEFAULT_OPTIONS = createDefaultOptions();

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

    private static Map<String, Object> mergedOptions(DecompilerContext context) {
        if (context == null || !context.hasOptions()) {
            logger.debug("mergedOptions: using DEFAULT_OPTIONS only ({} entries)", DEFAULT_OPTIONS.size());
            return DEFAULT_OPTIONS;
        }
        Map<String, Object> merged = new HashMap<>(DEFAULT_OPTIONS);
        Map<String, Object> userOptions = normalizedOptions(context.options());
        userOptions.forEach(merged::put);
        if (isEnabled(userOptions.get(IFernflowerPreferences.DUMP_ORIGINAL_LINES))) {
            merged.put(IFernflowerPreferences.SOURCE_FILE_COMMENTS, "1");
        }
        logger.debug("mergedOptions: DEFAULT + {} user options = {} total",
                context.options().size(), merged.size());
        return Collections.unmodifiableMap(merged);
    }

    private static Map<String, Object> normalizedOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        options.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            normalized.put(OPTION_ALIASES.getOrDefault(key, key), normalizeOptionValue(value));
        });
        return normalized;
    }

    private static String normalizeOptionValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "1";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "0";
        }
        return value;
    }

    private static boolean isEnabled(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    // ==================== Decompiler 接口 ====================

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
    public String decompile(String classFilePath, byte[] classBytes, DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        final StringBuilder result = new StringBuilder();
        final DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        final String effectiveTypeName = effectiveTypeName(typeName, classBytes);
        final byte[] bytes = classBytes.clone();

        try {
            // 单 class 的 IContextSource，无需临时文件
            IContextSource source = new SingleClassContextSource(effectiveTypeName, bytes, effectiveContext);

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

            IFernflowerLogger fernflowerLogger = new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {
                    switch (severity) {
                        case ERROR -> logger.error("[Vineflower] {}", message);
                        case WARN -> logger.warn("[Vineflower] {}", message);
                        case INFO -> logger.debug("[Vineflower] {}", message);
                        case TRACE -> logger.trace("[Vineflower] {}", message);
                    }
                }

                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {
                    switch (severity) {
                        case ERROR -> logger.error("[Vineflower] {}", message, t);
                        case WARN -> logger.warn("[Vineflower] {}", message, t);
                        case INFO -> logger.debug("[Vineflower] {}", message, t);
                        case TRACE -> logger.trace("[Vineflower] {}", message, t);
                    }
                }
            };

            // 使用非废弃构造器：不需要 IBytecodeProvider
            BaseDecompiler decompiler = new BaseDecompiler(resultSaver,
                    mergedOptions(effectiveContext), fernflowerLogger);
            decompiler.addSource(source);
            decompiler.decompileContext();

        } catch (Exception e) {
            return "// Vineflower Error: " + e.getMessage();
        }

        String decompiled = result.toString();
        if (decompiled.isEmpty()) {
            return "// Vineflower decompile failed\n// Class: " + effectiveTypeName;
        }
        return decompiled;
    }

    private static String effectiveTypeName(String typeName, byte[] classBytes) {
        return ClassFileParser.tryParse(classBytes)
                .map(metadata -> metadata.internalName().replace('\\', '/'))
                .orElseGet(() -> DecompilerContext.normalizeInternalName(
                        typeName == null ? "" : typeName));
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.VINEFLOWER;
    }

    @Override
    public Map<String, String> getDefaultOptions() {
        Map<String, String> stringOpts = new LinkedHashMap<>();
        DEFAULT_OPTIONS.forEach((k, v) -> stringOpts.put(k, String.valueOf(v)));
        return stringOpts;
    }

    // ==================== IContextSource 实现（单 class 服务） ====================

    /**
     * 为单个 class 提供字节码的 IContextSource。
     * 无需写临时文件，直接从内存中的 bytes 创建 InputStream。
     */
    private static final class SingleClassContextSource implements IContextSource {
        private final String typeName;
        private final byte[] bytes;
        private final DecompilerContext context;

        SingleClassContextSource(String typeName, byte[] bytes, DecompilerContext context) {
            this.typeName = typeName;
            this.bytes = bytes.clone();
            this.context = context;
        }

        @Override
        public String getName() {
            return "fxdecomplie";
        }

        @Override
        public Entries getEntries() {
            Entries entries = new Entries(
                    List.of(new Entry(typeName, Entry.BASE_VERSION)),
                    List.of(),
                    List.of()
            );
            logger.debug("Vineflower getEntries: class={}", typeName);
            return entries;
        }

        @Override
        public InputStream getInputStream(String resource) throws IOException {
            byte[] resolved = resolveClassBytes(resource);
            if (resolved != null) {
                logger.debug("Vineflower getInputStream [CLASS] resource={} -> hit", resource);
                return new ByteArrayInputStream(resolved);
            }
            logger.debug("Vineflower getInputStream [MISS] resource={}", resource);
            return null;
        }

        @Override
        public boolean hasClass(String className) throws IOException {
            return resolveClassBytes(className) != null;
        }

        @Override
        public byte[] getClassBytes(String className) throws IOException {
            return resolveClassBytes(className);
        }

        private byte[] resolveClassBytes(String resource) {
            if (resource == null || resource.isBlank()) {
                return null;
            }
            String normalized = resource.replace('\\', '/');
            if (isMainClass(normalized)) {
                return bytes;
            }

            String internalName = normalized;
            while (internalName.endsWith(IContextSource.CLASS_SUFFIX)) {
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
            String simpleName = DecompilerContext.simpleName(typeName);
            String dottedName = typeName.replace('/', '.');
            String entryPath = typeName + IContextSource.CLASS_SUFFIX;
            String dottedPath = dottedName + IContextSource.CLASS_SUFFIX;
            return resource.equals(entryPath)
                    || resource.equals("/" + entryPath)
                    || resource.equals(typeName)
                    || resource.equals("/" + typeName)
                    || resource.equals(simpleName)
                    || resource.equals(simpleName + IContextSource.CLASS_SUFFIX)
                    || resource.equals(dottedName)
                    || resource.equals(dottedPath);
        }

        @Override
        public IOutputSink createOutputSink(IResultSaver saver) {
            // 将 Vineflower 输出委托给全局 IResultSaver
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
