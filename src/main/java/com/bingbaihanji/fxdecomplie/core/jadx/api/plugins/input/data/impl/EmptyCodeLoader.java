package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 空代码加载器实现，表示没有任何代码可供加载。
 * 所有方法均为空操作，用于占位或表示空结果。
 */
public class EmptyCodeLoader implements ICodeLoader {

    /** 单例实例 */
    public static final EmptyCodeLoader INSTANCE = new EmptyCodeLoader();

    /**
     * @return 始终返回 {@code true}，因为没有任何代码可加载
     */
    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * 无任何类可供访问，方法体为空。
     */
    @Override
    public void visitClasses(Consumer<IClassData> consumer) {
    }

    /**
     * 无需关闭任何资源。
     */
    @Override
    public void close() throws IOException {
    }
}
