package com.bingbaihanji.fxdecomplie.decompiler;

import java.util.Map;

/**
 * 反编译引擎统一接口所有引擎实现必须是无状态或线程安全的
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface Decompiler {

    /**
     * 反编译字节码为 Java 源码(无上下文,不提供依赖解析能力)
     *
     * @deprecated 请使用 {@link #decompile(String, byte[], DecompilerContext)},
     *             携带 DecompilerContext 以支持依赖类字节码解析
     */
    @Deprecated
    String decompile(String classFilePath, byte[] classBytes);

    /**
     * 反编译字节码为 Java 源码(带工作区隔离的依赖解析上下文)
     *
     * @param classFilePath 类文件路径(如 "com/example/Main.class")
     * @param classBytes    类文件字节码调用方必须把该数组视为只读,反编译期间不得修改或复用为写缓冲
     * @param context       反编译上下文,用于按工作区解析依赖类字节码
     * @return 反编译后的 Java 源码
     */
    default String decompile(String classFilePath, byte[] classBytes,
                             DecompilerContext context) {
        return decompile(classFilePath, classBytes);
    }

    /**
     * 按内部类型名反编译(无上下文,不提供依赖解析能力)
     *
     * @deprecated 请使用 {@link #decompileType(String, byte[], DecompilerContext)},
     *             携带 DecompilerContext 以支持依赖类字节码解析
     */
    @Deprecated
    String decompileType(String typeName, byte[] classBytes);

    /**
     * 按内部类型名反编译(带工作区隔离的依赖解析上下文)
     *
     * @param typeName   内部类型名(如 "com.example.Main")
     * @param classBytes 类文件字节码调用方必须把该数组视为只读,反编译期间不得修改或复用为写缓冲
     * @param context    反编译上下文,用于按工作区解析依赖类字节码
     * @return 反编译后的 Java 源码
     */
    default String decompileType(String typeName, byte[] classBytes,
                                 DecompilerContext context) {
        return decompileType(typeName, classBytes);
    }

    /** @return 引擎类型标识 */
    DecompilerTypeEnum getType();

    /** @return 引擎名称(默认返回枚举名) */
    default String getName() {
        return getType().name();
    }

    /** 引擎初始化(首次创建时调用) */
    default void initialize() {
    }

    /** 引擎清理(应用退出时调用) */
    default void cleanup() {
    }

    /** 反编译选项(默认空,子类可覆盖返回引擎特定配置) */
    default Map<String, String> getDefaultOptions() {
        return Map.of();
    }
}
