package com.bingbihanji.fxdecomplie.model;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 打开文件数据模型。表示一个在编辑器中打开的类文件，包含类名、完整路径、反编译源码和所使用的反编译引擎。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class OpenFile {

    /** 简短类名（如 "Main"） */
    private final String className;
    /** 完整内部路径 */
    private final String fullPath;
    /** 反编译后的 Java 源码 */
    private final String sourceCode;
    /** 使用的反编译引擎 */
    private final DecompilerTypeEnum engine;

    /**
     * 构造打开文件。
     *
     * @param className  简短类名
     * @param fullPath   完整内部路径
     * @param sourceCode 反编译后的源码
     * @param engine     使用的引擎
     */
    public OpenFile(String className, String fullPath, String sourceCode, DecompilerTypeEnum engine) {
        this.className = className;
        this.fullPath = fullPath;
        this.sourceCode = sourceCode;
        this.engine = engine;
    }

    /** @return 简短类名 */
    public String getClassName() {
        return className;
    }

    /** @return 完整内部路径 */
    public String getFullPath() {
        return fullPath;
    }

    /** @return 反编译后的源码 */
    public String getSourceCode() {
        return sourceCode;
    }

    /** @return 使用的引擎 */
    public DecompilerTypeEnum getEngine() {
        return engine;
    }

    /**
     * 基于 fullPath + engine 比较，不包括 sourceCode。
     * 同一类用同一引擎反编译的两个 OpenFile 对象视为相等（用于缓存键语义）。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpenFile other)) return false;
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
