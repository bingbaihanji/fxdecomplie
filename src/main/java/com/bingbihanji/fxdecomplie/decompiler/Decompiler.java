package com.bingbihanji.fxdecomplie.decompiler;

/**
 * 反编译引擎统一接口。所有引擎实现必须是无状态或线程安全的。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface Decompiler {

    /**
     * 反编译字节码为 Java 源码。
     * @param classFilePath 类文件路径（如 "com/example/Main.class"）
     * @param classBytes    类文件字节码
     * @return 反编译后的 Java 源码
     */
    String decompile(String classFilePath, byte[] classBytes);

    default String decompile(String classFilePath, byte[] classBytes,
                             DecompilerContext context) {
        return decompile(classFilePath, classBytes);
    }

    /**
     * 按内部类型名反编译。
     * @param typeName   内部类型名（如 "com.example.Main"）
     * @param classBytes 类文件字节码
     * @return 反编译后的 Java 源码
     */
    String decompileType(String typeName, byte[] classBytes);

    default String decompileType(String typeName, byte[] classBytes,
                                 DecompilerContext context) {
        return decompileType(typeName, classBytes);
    }

    /** @return 引擎类型标识 */
    DecompilerTypeEnum getType();

    /** @return 引擎名称（默认返回枚举名） */
    default String getName() {
        return getType().name();
    }

    /** 引擎初始化（首次创建时调用） */
    default void initialize() {
    }

    /** 引擎清理（应用退出时调用） */
    default void cleanup() {
    }
}
