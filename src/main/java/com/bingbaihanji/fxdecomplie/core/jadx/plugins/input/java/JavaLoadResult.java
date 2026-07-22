package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Java 代码加载结果
 * <p>
 * 持有一组 {@link JavaClassReader}，在遍历时逐个解析出类数据，
 * 并可选地持有一个在关闭时一并释放的资源 (如打开的归档文件)
 */
public class JavaLoadResult implements ICodeLoader {
    private static final Logger LOG = LoggerFactory.getLogger(JavaLoadResult.class);

    /** 类读取器列表 */
    private final List<JavaClassReader> readers;
    /** 关闭时需一并释放的资源，可为 {@code null} */
    @Nullable
    private final Closeable closeable;

    /**
     * 构造加载结果，不关联额外的可关闭资源
     *
     * @param readers 类读取器列表
     */
    public JavaLoadResult(List<JavaClassReader> readers) {
        this(readers, null);
    }

    /**
     * 构造加载结果，并关联一个在关闭时一并释放的资源
     *
     * @param readers   类读取器列表
     * @param closeable 关闭时需一并释放的资源，可为 {@code null}
     */
    public JavaLoadResult(List<JavaClassReader> readers, @Nullable Closeable closeable) {
        this.readers = readers;
        this.closeable = closeable;
    }

    /** 判断异常是否由线程中断/取消引起 */
    private static boolean isInterruptRelated(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof InterruptedException
                    || cause instanceof CancellationException
                    || "Thread interrupted".equalsIgnoreCase(cause.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    /**
     * 遍历所有类，将解析出的类数据依次交给消费者处理
     * <p>
     * 单个类加载失败时记录错误日志并继续处理后续类
     *
     * @param consumer 类数据消费者
     */
    @Override
    public void visitClasses(Consumer<IClassData> consumer) {
        for (JavaClassReader reader : readers) {
            try {
                consumer.accept(reader.loadClassData());
            } catch (Exception e) {
                if (isInterruptRelated(e)) {
                    LOG.debug("Failed to load class data for file: {} (cancelled)",
                            reader.getFileName());
                    break;
                }
                LOG.error("Failed to load class data for file: {}", reader.getFileName(), e);
            }
        }
    }

    /**
     * 判断是否不含任何可加载的类
     *
     * @return 无类可加载时返回 {@code true}
     */
    @Override
    public boolean isEmpty() {
        return readers.isEmpty();
    }

    /**
     * 关闭加载结果，释放关联的可关闭资源 (若存在)
     *
     * @throws IOException 关闭资源时发生 I/O 异常
     */
    @Override
    public void close() throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }
}
