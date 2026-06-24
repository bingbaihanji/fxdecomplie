package com.bingbaihanji.fxdecomplie.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
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

    /** Vineflower 默认反编译选项 */
    private static final Map<String, Object> DEFAULT_OPTIONS = createDefaultOptions();

    private static Map<String, Object> createDefaultOptions() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("den", "1");           // 反编译内部/匿名类
        opts.put("dgs", "1");           // 反编译泛型签名
        opts.put("din", "1");           // 反编译内联方法
        opts.put("rbr", "1");           // 移除桥接方法
        opts.put("rsy", "1");           // 移除合成成员
        opts.put("bto", "1");           // 字节码转源码优化
        opts.put("nns", "1");           // 新数字表示法
        opts.put("uto", "0");           // 未定义类型优化: 关闭
        opts.put("udv", "1");           // 未定义变量处理
        opts.put("ump", "1");           // 使用方法参数名
        opts.put("fdi", "1");           // 最终去混淆
        opts.put("asc", "0");           // ASCII 编码限制: 保持 Unicode
        opts.put("rer", "1");           // 移除空 return
        opts.put("rgn", "1");           // 移除无用 goto
        opts.put("lit", "1");           // 字面量简化
        opts.put("bsm", "1");           // Bootstrap 方法还原
        opts.put("mpm", "60");          // 单方法最大处理时间(秒)
        opts.put("lac", "0");           // 本地匿名类: 关闭
        opts.put("nls", "1");           // 换行符样式
        opts.put("ind", "    ");        // 缩进: 4空格
        opts.put("log", "ERROR");       // 日志级别：批量反编译时避免 WARN 噪音
        opts.put("pll", "130");         // 建议行长度
        opts.put("rec", "1");           // 支持 record 类型
        opts.put("sea", "1");           // 支持 sealed class
        opts.put("pam", "1");           // 支持模式匹配
        opts.put("swi", "1");           // 支持 switch 表达式
        opts.put("vac", "1");           // 变量访问优化
        return Collections.unmodifiableMap(opts);
    }

    private static Map<String, Object> mergedOptions(DecompilerContext context) {
        if (context == null || !context.hasOptions()) {
            return DEFAULT_OPTIONS;
        }
        Map<String, Object> merged = new HashMap<>(DEFAULT_OPTIONS);
        merged.putAll(context.options());
        return Collections.unmodifiableMap(merged);
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
        final byte[] bytes = classBytes.clone();

        try {
            // 单 class 的 IContextSource，无需临时文件
            IContextSource source = new SingleClassContextSource(typeName, bytes, effectiveContext);

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
            return "// Vineflower decompile failed\n// Class: " + typeName;
        }
        return decompiled;
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
        private final String entryPath;

        SingleClassContextSource(String typeName, byte[] bytes, DecompilerContext context) {
            this.typeName = typeName;
            this.bytes = bytes.clone();
            this.context = context;
            this.entryPath = typeName + IContextSource.CLASS_SUFFIX;
        }

        @Override
        public String getName() {
            return typeName;
        }

        @Override
        public Entries getEntries() {
            return new Entries(
                    List.of(new Entry(entryPath, Entry.BASE_VERSION)),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public InputStream getInputStream(String resource) throws IOException {
            // 主 class
            if (entryPath.equals(resource)) {
                return new ByteArrayInputStream(bytes);
            }
            // 依赖 class（从 DecompilerContext 解析）
            if (resource.endsWith(IContextSource.CLASS_SUFFIX)) {
                String internalName = resource.substring(0,
                        resource.length() - IContextSource.CLASS_SUFFIX.length());
                byte[] dep = context.resolveClassBytes(internalName);
                if (dep != null) {
                    return new ByteArrayInputStream(dep);
                }
            }
            return null;
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
