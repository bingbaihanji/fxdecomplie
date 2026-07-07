package com.bingbaihanji.fxdecomplie.decompiler;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * CFR 反编译引擎适配器
 * 从 code-resurrector 项目移植
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CfrDecompiler implements Decompiler {

    private static final Logger log = LoggerFactory.getLogger(CfrDecompiler.class);

    /** CFR 默认反编译选项 */
    private static final Map<String, String> DEFAULT_OPTIONS = createDefaultOptions();

    /** @return CFR 默认选项配置 */
    private static Map<String, String> createDefaultOptions() {
        Map<String, String> opts = new HashMap<>();
        opts.put("showversion", "false");          // 不显示 CFR 版本信息
        opts.put("hideutf", "false");              // 不隐藏 UTF-8 字符
        opts.put("innerclasses", "true");          // 反编译内部类
        opts.put("decodelambdas", "true");         // 解码 lambda 表达式
        opts.put("decodestringswitch", "true");    // 解码字符串 switch
        opts.put("decodeenumswitch", "true");      // 解码枚举 switch
        opts.put("sugarenums", "true");            // 语法糖: 枚举
        opts.put("decodefinally", "true");         // 解码 finally 块
        opts.put("removebadgenerics", "true");     // 移除无效泛型
        opts.put("sugarasserts", "true");          // 语法糖: assert
        opts.put("sugarboxing", "true");           // 语法糖: 自动装箱
        opts.put("showops", "false");              // 不显示原始操作码
        opts.put("silent", "true");                // 静默模式
        opts.put("recover", "true");               // 类型恢复
        opts.put("eclipse", "true");               // Eclipse 兼容模式
        opts.put("override", "true");              // 显示 @Override 注解
        opts.put("showinferrable", "true");        // 显示可推断类型信息
        opts.put("stringbuilder", "true");         // 字符串拼接还原为 StringBuilder
        opts.put("stringconcat", "true");          // 使用 StringConcatFactory
        opts.put("tryresources", "true");          // try-with-resources 还原
        opts.put("recordtypes", "true");           // 支持 record 类型
        opts.put("sealedclasses", "true");         // 支持 sealed class
        opts.put("switchexpression", "true");      // 支持 switch 表达式
        opts.put("instanceofpattern", "true");     // 支持 instanceof 模式匹配
        return Collections.unmodifiableMap(opts);
    }

    /** 移除 .class 后缀,转换为内部类名格式 */
    private static String removeClassSuffix(String path) {
        if (path.endsWith(".class")) {
            return path.substring(0, path.length() - 6);
        }
        return path;
    }

    /**
     * 合并默认选项与用户上下文选项
     * 用户选项覆盖同名的默认选项
     *
     * @param context 反编译上下文（可为 null）
     * @return 合并后的不可变选项映射
     */
    private static Map<String, String> mergedOptions(DecompilerContext context) {
        if (context == null || !context.hasOptions()) {
            return DEFAULT_OPTIONS;
        }
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_OPTIONS);
        merged.putAll(context.options());
        return Collections.unmodifiableMap(merged);
    }

    /** 使用空上下文反编译,保留内部类名中的 .class 后缀 */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = removeClassSuffix(classFilePath);
        return decompileType(internalName, classBytes, DecompilerContext.EMPTY);
    }

    /** 使用空上下文反编译给定内部类名 */
    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    /** 带上下文的文件路径反编译,自动去除 .class 后缀后委托给 {@link #decompileType} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        String internalName = removeClassSuffix(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    /**
     * 使用 CFR 引擎反编译指定类
     *
     * <p>核心流程：通过匿名 {@link ClassFileSource} 为主类和依赖类提供字节码,
     * 匿名 {@link OutputSinkFactory} 收集反编译输出到 StringBuilder,
     * 最终调用 {@link CfrDriver#analyse} 执行反编译</p>
     *
     * @param typeName   类的内部名称（如 {@code com/example/MyClass}）
     * @param classBytes 类的原始字节码
     * @param context    反编译上下文（可为 null,用于解析依赖类字节码和传递选项）
     * @return 反编译后的 Java 源码字符串；若结果为空则返回带说明的错误注释
     */
    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        final StringBuilder result = new StringBuilder();
        DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;

        // 为 CFR 提供字节码来源：主类直接用传入的 bytes,依赖类通过上下文解析
        ClassFileSource classFileSource = new ClassFileSource() {
            @Override
            public void informAnalysisRelativePathDetail(String usePath, String specPath) {
            }

            @Override
            public Collection<String> addJar(String jarPath) {
                return Collections.emptyList();
            }

            @Override
            public String getPossiblyRenamedPath(String path) {
                return path;
            }

            @Override
            public Pair<byte[], String> getClassFileContent(String path) throws IOException {
                String normalizedPath = path.replace("\\", "/");
                String normalizedTypeName = typeName.replace("\\", "/");

                // 检查是否为主类请求（多种路径形式匹配）
                if (normalizedPath.equals(normalizedTypeName)
                        || normalizedPath.equals(normalizedTypeName + ".class")
                        || normalizedPath.endsWith("/" + DecompilerContext.simpleName(normalizedTypeName) + ".class")
                        || normalizedPath.endsWith("/" + DecompilerContext.simpleName(normalizedTypeName))) {
                    return Pair.make(classBytes, normalizedPath);
                }

                // 非主类：从上下文解析依赖类字节码
                String internalName = removeClassSuffix(normalizedPath);
                byte[] otherBytes = effectiveContext.resolveClassBytes(internalName);
                if (otherBytes != null) {
                    return Pair.make(otherBytes, normalizedPath);
                }

                throw new IOException("Class not found: " + path);
            }
        };

        // 收集 CFR 反编译输出到 StringBuilder
        OutputSinkFactory outputSinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                if (sinkType == SinkType.JAVA) {
                    return Collections.singletonList(SinkClass.DECOMPILED);
                }
                return Collections.emptyList();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return (Sink<T>) (OutputSinkFactory.Sink<SinkReturns.Decompiled>) decompiled -> {
                        result.append(decompiled.getJava());
                    };
                }
                return ignore -> {
                };
            }
        };

        Map<String, String> options = mergedOptions(effectiveContext);

        CfrDriver driver = new CfrDriver.Builder()
                .withClassFileSource(classFileSource)
                .withOutputSink(outputSinkFactory)
                .withOptions(options)
                .build();

        log.debug("CFR decompile: class={}, options={}", typeName, options.size());
        long start = System.currentTimeMillis();
        driver.analyse(Collections.singletonList(typeName + ".class"));

        String decompiled = result.toString();
        long elapsed = System.currentTimeMillis() - start;
        if (decompiled.isEmpty()) {
            log.warn("CFR decompile returned empty: {} ({}ms)", typeName, elapsed);
            return "// CFR decompile failed\n// Class: " + typeName;
        }
        log.debug("CFR decompile OK: {} ({}ms, {} chars)", typeName, elapsed, decompiled.length());
        return decompiled;
    }

    /** @return 引擎类型 {@link DecompilerTypeEnum#CFR} */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.CFR;
    }

    /** @return CFR 默认反编译选项的不可变映射 */
    @Override
    public Map<String, String> getDefaultOptions() {
        return DEFAULT_OPTIONS;
    }
}
