package com.bingbihanji.fxdecomplie.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Vineflower 反编译引擎适配器。
 * 从 code-resurrector 项目移植。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class VineflowerDecompiler implements Decompiler {

    /** 日志记录器 */
    private static final System.Logger LOG = System.getLogger(VineflowerDecompiler.class.getName());

    /** Vineflower 默认反编译选项 */
    private static final Map<String, Object> DEFAULT_OPTIONS = createDefaultOptions();

    /** @return Vineflower 默认选项配置 */
    private static Map<String, Object> createDefaultOptions() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("den", "1");           // 反编译内部/匿名类 (decompile inner)
        opts.put("dgs", "1");           // 反编译泛型签名 (decompile generic signatures)
        opts.put("din", "1");           // 反编译内联方法 (decompile inline)
        opts.put("rbr", "1");           // 移除桥接方法 (remove bridge methods)
        opts.put("rsy", "1");           // 移除合成成员 (remove synthetic)
        opts.put("bto", "1");           // 字节码转源码优化 (bytecode to source)
        opts.put("nns", "1");           // 新数字表示法 (new numeric syntax)
        opts.put("uto", "0");           // 未定义类型优化: 关闭
        opts.put("udv", "1");           // 未定义变量处理 (undefined variable)
        opts.put("ump", "1");           // 使用方法参数名 (use method parameters)
        opts.put("fdi", "1");           // 最终去混淆 (final deobfuscation)
        opts.put("asc", "0");           // ASCII 编码限制: 保持 Unicode
        opts.put("rer", "1");           // 移除空 return (remove empty returns)
        opts.put("rgn", "1");           // 移除无用 goto (remove gotos)
        opts.put("lit", "1");           // 字面量简化 (literal simplification)
        opts.put("bsm", "1");           // Bootstrap 方法还原 (bootstrap methods)
        opts.put("mpm", "60");          // 单方法最大处理时间(秒) (max process millis)
        opts.put("lac", "0");           // 本地匿名类: 关闭
        opts.put("nls", "1");           // 换行符样式 (new line separator)
        opts.put("ind", "    ");        // 缩进: 4空格
        opts.put("log", "WARN");        // 日志级别
        opts.put("pll", "130");         // 建议行长度 (preferred line length)
        opts.put("rec", "1");           // 支持 record 类型
        opts.put("sea", "1");           // 支持 sealed class
        opts.put("pam", "1");           // 支持模式匹配 (pattern matching)
        opts.put("swi", "1");           // 支持 switch 表达式
        opts.put("vac", "1");           // 变量访问优化 (variable access)
        return Collections.unmodifiableMap(opts);
    }

    /** {@inheritDoc} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = classFilePath.replace(".class", "");
        return decompileType(internalName, classBytes);
    }

    /** {@inheritDoc} */
    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        final StringBuilder result = new StringBuilder();

        File tempDir = null;
        File tempClassFile = null;

        try {
            tempDir = Files.createTempDirectory("vineflower_").toFile();
            String classFileName = typeName.replace('/', '_').replace('\\', '_') + ".class";
            tempClassFile = new File(tempDir, classFileName);
            Files.write(tempClassFile.toPath(), classBytes);

            final File finalTempClassFile = tempClassFile;

            IBytecodeProvider bytecodeProvider = (externalPath, internalPath) -> {
                if (externalPath != null && externalPath.equals(finalTempClassFile.getAbsolutePath())) {
                    return classBytes;
                }
                String key = internalPath != null ? internalPath : externalPath;
                if (key != null) {
                    key = key.replace(".class", "").replace("\\", "/");
                    if (key.endsWith("/" + getSimpleName(typeName)) || key.equals(typeName)) {
                        return classBytes;
                    }
                    byte[] cached = BytecodeCache.get(key);
                    if (cached != null) {
                        return cached;
                    }
                }
                return null;
            };

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

            IFernflowerLogger logger = new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {
                }

                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {
                }
            };

            BaseDecompiler decompiler = new BaseDecompiler(bytecodeProvider, resultSaver, DEFAULT_OPTIONS, logger);
            decompiler.addSource(tempClassFile);
            decompiler.decompileContext();

        } catch (IOException e) {
            return "// Vineflower Error: " + e.getMessage();
        } finally {
            if (tempClassFile != null) {
                try {
                    Files.deleteIfExists(tempClassFile.toPath());
                } catch (IOException e) {
                    LOG.log(System.Logger.Level.WARNING, "Failed to delete temp class file: " + tempClassFile, e);
                }
            }
            if (tempDir != null) {
                deleteRecursively(tempDir.toPath());
            }
        }

        String decompiled = result.toString();
        if (decompiled.isEmpty()) {
            return "// Vineflower decompile failed\n// Class: " + typeName;
        }
        return decompiled;
    }

    /**
     * 从完整路径中提取简单类名。
     * @param typeName 类型名（如 "com/example/Main"）
     * @return 简单类名（如 "Main"）
     */
    private String getSimpleName(String typeName) {
        int idx = typeName.lastIndexOf('/');
        return idx >= 0 ? typeName.substring(idx + 1) : typeName;
    }

    private static void deleteRecursively(java.nio.file.Path dir) {
        if (dir == null || !java.nio.file.Files.exists(dir)) return;
        try (var files = java.nio.file.Files.walk(dir).sorted(Comparator.reverseOrder())) {
            files.forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (java.io.IOException ignored) {}
            });
        } catch (java.io.IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to clean temp dir: " + dir, e);
        }
    }

    /** @return 引擎类型 VINEFLOWER */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.VINEFLOWER;
    }
}
