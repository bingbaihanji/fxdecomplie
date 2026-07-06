package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 打开文件数据模型表示一个在编辑器中打开的类文件,包含类名、完整路径、反编译源码和所使用的反编译引擎
 *
 * @author bingbaihanji
 * @date 2026-06-17
 * @param className  简短类名(如 "Main")
 * @param fullPath  完整内部路径
 * @param sourceCode  反编译后的 Java 源码
 * @param engine  使用的反编译引擎
 */
public record OpenFile(String className, String fullPath, String sourceCode, DecompilerTypeEnum engine) {

    /**
     * 构造打开文件
     *
     * @param className  简短类名
     * @param fullPath   完整内部路径
     * @param sourceCode 反编译后的源码
     * @param engine     使用的引擎
     */
    public OpenFile {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(sourceCode, "sourceCode");
        Objects.requireNonNull(engine, "engine");
    }

    /**
     * 基于 fullPath + engine 比较,不包括 sourceCode
     * 同一类用同一引擎反编译的两个 OpenFile 对象视为相等(用于缓存键语义)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpenFile other)) {
            return false;
        }
        return Objects.equals(fullPath, other.fullPath) && engine == other.engine;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullPath, engine);
    }

    @Override
    public String toString() {
        return className + ".java";
    }
}
