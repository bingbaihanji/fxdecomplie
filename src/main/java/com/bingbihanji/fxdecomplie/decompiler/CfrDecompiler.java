package com.bingbihanji.fxdecomplie.decompiler;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * CFR 反编译引擎适配器。
 * 从 code-resurrector 项目移植。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CfrDecompiler implements Decompiler {

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

    private static String removeClassSuffix(String path) {
        if (path.endsWith(".class")) {
            return path.substring(0, path.length() - 6);
        }
        return path;
    }

    /** {@inheritDoc} */
    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = removeClassSuffix(classFilePath);
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
        String internalName = removeClassSuffix(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        final StringBuilder result = new StringBuilder();
        DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;

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

                if (normalizedPath.equals(normalizedTypeName)
                        || normalizedPath.equals(normalizedTypeName + ".class")
                        || normalizedPath.endsWith("/" + DecompilerContext.simpleName(normalizedTypeName) + ".class")
                        || normalizedPath.endsWith("/" + DecompilerContext.simpleName(normalizedTypeName))) {
                    return Pair.make(classBytes, normalizedPath);
                }

                String internalName = removeClassSuffix(normalizedPath);
                byte[] otherBytes = effectiveContext.resolveClassBytes(internalName);
                if (otherBytes != null) {
                    return Pair.make(otherBytes, normalizedPath);
                }

                return null;
            }
        };

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

        CfrDriver driver = new CfrDriver.Builder()
                .withClassFileSource(classFileSource)
                .withOutputSink(outputSinkFactory)
                .withOptions(DEFAULT_OPTIONS)
                .build();

        driver.analyse(Collections.singletonList(typeName + ".class"));

        String decompiled = result.toString();
        if (decompiled.isEmpty()) {
            return "// CFR decompile failed\n// Class: " + typeName;
        }
        return decompiled;
    }

    /** @return 引擎类型 CFR */
    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.CFR;
    }
}
