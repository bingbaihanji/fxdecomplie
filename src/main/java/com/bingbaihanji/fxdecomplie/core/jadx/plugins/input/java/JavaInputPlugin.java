package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginContext;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.EmptyCodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.JavaClassParseException;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Java 输入插件
 * <p>
 * 负责加载 {@code .class} 与 {@code .jar} 文件，将其中的类读取为
 * {@link JavaClassReader} 并封装成可供反编译器消费的 {@link ICodeLoader}
 */
public class JavaInputPlugin implements JadxPlugin {

    /**
     * 加载指定的一组 class/jar 文件
     *
     * @param inputFiles 待加载的文件路径列表
     * @return 代码加载器，无可加载类时返回空加载器
     */
    public static ICodeLoader loadClassFiles(List<Path> inputFiles) {
        return loadClassFiles(inputFiles, null);
    }

    /**
     * 加载指定的一组 class/jar 文件，并可关联一个在关闭时一并释放的资源
     *
     * @param inputFiles 待加载的文件路径列表
     * @param closeable  加载器关闭时需要一并关闭的资源，可为 {@code null}
     * @return 代码加载器，无可加载类时返回空加载器
     */
    public static ICodeLoader loadClassFiles(List<Path> inputFiles, @Nullable Closeable closeable) {
        List<JavaClassReader> readers = new JavaInputLoader().collectFiles(inputFiles);
        if (readers.isEmpty()) {
            return EmptyCodeLoader.INSTANCE;
        }
        return new JavaLoadResult(readers, closeable);
    }

    /**
     * 通过 {@link JavaInputLoader} 的加载方法提供多个输入来源
     *
     * @param loader 使用 {@link JavaInputLoader} 生成类读取器列表的函数
     * @return 包装后的代码加载器
     */
    public static ICodeLoader load(Function<JavaInputLoader, List<JavaClassReader>> loader) {
        return wrapClassReaders(loader.apply(new JavaInputLoader()));
    }

    /**
     * 从输入流加载 class 文件或 jar 的便捷方法
     * <p>
     * 每个 JadxDecompiler 实例只应调用一次 如需多次加载，请使用
     * {@link JavaInputPlugin#load(Function)} 方法
     *
     * @param in       输入流
     * @param fileName 文件名
     * @return 代码加载器
     */
    public static ICodeLoader loadFromInputStream(InputStream in, String fileName) {
        try {
            return wrapClassReaders(new JavaInputLoader().loadInputStream(in, fileName));
        } catch (Exception e) {
            throw new JavaClassParseException("Failed to read input stream", e);
        }
    }

    /**
     * 通过字节内容加载单个 class 文件的便捷方法
     * <p>
     * 每个 JadxDecompiler 实例只应调用一次 如需多次加载，请使用
     * {@link JavaInputPlugin#load(Function)} 方法
     *
     * @param content  class 文件的字节内容
     * @param fileName 文件名
     * @return 代码加载器
     */
    public static ICodeLoader loadSingleClass(byte[] content, String fileName) {
        JavaClassReader reader = new JavaInputLoader().loadClass(content, fileName);
        return new JavaLoadResult(Collections.singletonList(reader));
    }

    /**
     * 将类读取器列表包装为代码加载器
     *
     * @param readers 类读取器列表
     * @return 代码加载器，列表为空时返回空加载器
     */
    public static ICodeLoader wrapClassReaders(List<JavaClassReader> readers) {
        if (readers.isEmpty()) {
            return EmptyCodeLoader.INSTANCE;
        }
        return new JavaLoadResult(readers);
    }

    /**
     * 返回该插件的基本信息 (唯一标识 显示名称 描述)
     *
     * @return 插件信息
     */
    @Override
    public JadxPluginInfo getPluginInfo() {
        return new JadxPluginInfo("java-input", "Java Input", "Load .class and .jar files");
    }

    /**
     * 初始化插件，向上下文注册代码输入处理器
     * <p>
     * 处理器收集输入文件中的类读取器，若无任何类则返回空加载结果
     *
     * @param context 插件上下文
     */
    @Override
    public void init(JadxPluginContext context) {
        context.addCodeInput(inputFiles -> {
            JavaInputLoader loader = new JavaInputLoader(context.getZipReader(), context.files().getPluginTempDir());
            List<JavaClassReader> readers = loader.collectFiles(inputFiles);
            if (readers.isEmpty()) {
                return EmptyCodeLoader.INSTANCE;
            }
            return new JavaLoadResult(readers, null);
        });
    }
}
