package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaClassReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaLoadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * jadx 反编译引擎适配器
 * 每次反编译创建新的 jadx JadxDecompiler 实例，保证无状态和线程安全
 *
 * @author bingbaihanji
 * @date 2026-07-11
 */
public class JadxDecompiler implements Decompiler {

    private static final Logger log = LoggerFactory.getLogger(JadxDecompiler.class);

    // ==================== Decompiler 接口实现 ====================

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

    /**
     * 使用 jadx 引擎反编译指定类
     *
     * <p>核心流程：创建 jadx JadxDecompiler 实例，加载目标类字节码，
     * 调用 load() 初始化类层次图，然后通过 getCode() 获取反编译结果</p>
     *
     * @param typeName   类的内部名称(如 {@code com/example/MyClass})
     * @param classBytes 类的原始字节码
     * @param context    反编译上下文(可为 null，用于解析依赖类字节码)
     * @return 反编译后的 Java 源码字符串；异常时返回错误信息注释
     */
    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        final DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        final String effectiveTypeName = DecompilerContext.normalizeInternalName(typeName);

        log.debug("jadx decompile: class={}", effectiveTypeName);
        long start = System.currentTimeMillis();

        try {
            // 构建类数据列表：目标类
            List<JavaClassReader> classReaders = new ArrayList<>();
            classReaders.add(new JavaClassReader(0, effectiveTypeName + ".class", classBytes.clone()));

            // 创建 ICodeLoader
            ICodeLoader codeLoader = new JavaLoadResult(classReaders);

            // 配置 jadx 参数
            JadxArgs args = createJadxArgs();

            // 创建 jadx 实例并加载
            try (com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler jadx = new com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler(args)) {
                // 注册自定义代码加载器
                jadx.addCustomCodeLoader(codeLoader);

                // 加载并初始化
                jadx.load();

                // 获取反编译结果
                List<JavaClass> classes = jadx.getClasses();
                if (classes.isEmpty()) {
                    log.warn("jadx decompile: no classes loaded for {}", effectiveTypeName);
                    return "// jadx decompile failed: no classes loaded\n// Class: " + effectiveTypeName;
                }

                JavaClass targetClass = findTargetClass(classes, effectiveTypeName);
                if (targetClass == null) {
                    targetClass = classes.get(0);
                }

                String decompiled = targetClass.getCode();
                long elapsed = System.currentTimeMillis() - start;

                if (decompiled == null || decompiled.isEmpty()) {
                    log.warn("jadx decompile returned empty: {} ({}ms)", effectiveTypeName, elapsed);
                    return "// jadx decompile failed\n// Class: " + effectiveTypeName;
                }

                log.debug("jadx decompile OK: {} ({}ms, {} chars)", effectiveTypeName, elapsed,
                        decompiled.length());
                return decompiled;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("jadx decompile exception: {} ({}ms): {}", effectiveTypeName, elapsed, e.getMessage());
            return "// jadx Error: " + e.getMessage();
        }
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.JADX;
    }

    @Override
    public String getName() {
        return "jadx";
    }

    // ==================== 内部方法 ====================

    /**
     * 创建 jadx 反编译参数
     * 配置为单类反编译优化模式
     */
    private JadxArgs createJadxArgs() {
        JadxArgs args = new JadxArgs();
        args.setSkipResources(true);           // 跳过资源文件
        args.setDebugInfo(true);               // 保留调试信息
        args.setUseImports(true);              // 使用 import 语句
        args.setInlineMethods(true);           // 内联方法
        args.setInlineAnonymousClasses(true);  // 内联匿名类
        args.setExtractFinally(true);          // 提取 finally 块
        args.setDeobfuscationOn(false);        // 不启用去混淆
        args.setShowInconsistentCode(true);    // 显示不一致的代码
        return args;
    }

    /**
     * 从加载的类列表中查找目标类
     * 按内部名称匹配，支持包名+类名匹配
     */
    private JavaClass findTargetClass(List<JavaClass> classes, String targetName) {
        String normalizedTarget = targetName.replace('.', '/');
        for (JavaClass cls : classes) {
            String clsName = cls.getClassNode().getClassInfo().getFullName();
            if (clsName.equals(normalizedTarget) || clsName.replace('.', '/').equals(normalizedTarget)) {
                return cls;
            }
        }
        // 回退：按简单类名匹配
        String simpleName = normalizedTarget.contains("/")
                ? normalizedTarget.substring(normalizedTarget.lastIndexOf('/') + 1)
                : normalizedTarget;
        for (JavaClass cls : classes) {
            if (cls.getName().equals(simpleName)) {
                return cls;
            }
        }
        return null;
    }
}
