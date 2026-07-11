package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * 代码加载器接口。
 * <p>
 * 负责从输入源（如 DEX、CLASS、SMALI 等）中加载类数据，并向消费者提供遍历访问能力。
 * 继承 {@link Closeable}，使用完毕后应关闭以释放底层资源。
 */
public interface ICodeLoader extends Closeable {

    /**
     * 遍历加载器中包含的所有类，并将每个类的数据传递给指定的消费者。
     *
     * @param consumer 用于接收并处理每个 {@link IClassData} 类数据的消费者
     */
    void visitClasses(Consumer<IClassData> consumer);

    /**
     * 判断当前加载器是否为空（不包含任何类数据）。
     *
     * @return 若不包含任何类则返回 {@code true}，否则返回 {@code false}
     */
    boolean isEmpty();
}
