package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 打开文件数据模型表示一个在编辑器中打开的类文件,包含类名、完整路径、反编译源码和所使用的反编译引擎
 *
 * <p><b>相等性语义</b>：仅基于 {@code fullPath} 和 {@code engine} 判断相等，
 * {@code sourceCode} 不参与相等性比较这允许同一类用同一引擎的两次反编译结果
 * 在集合中视为同一对象 (满足缓存键语义)</p>
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OpenFile {

    private final String className;
    private final String fullPath;
    private final String sourceCode;
    private final DecompilerTypeEnum engine;

    /**
     * 构造打开文件
     *
     * @param className  简短类名(如 "Main")
     * @param fullPath   完整内部路径
     * @param sourceCode 反编译后的 Java 源码
     * @param engine     使用的引擎
     */
    public OpenFile(String className, String fullPath, String sourceCode, DecompilerTypeEnum engine) {
        this.className = Objects.requireNonNull(className, "className");
        this.fullPath = Objects.requireNonNull(fullPath, "fullPath");
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * 创建用于集合/缓存的逻辑键 —— 仅包含 fullPath + engine，
     * 忽略可能不同的 sourceCode 内容
     */
    public String cacheKey() {
        return fullPath + '\0' + engine.name();
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

    // --- accessors ---

    public String className() {
        return className;
    }

    public String fullPath() {
        return fullPath;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public DecompilerTypeEnum engine() {
        return engine;
    }
}
