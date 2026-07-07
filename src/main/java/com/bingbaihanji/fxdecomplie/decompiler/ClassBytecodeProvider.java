package com.bingbaihanji.fxdecomplie.decompiler;

/**
 * 通过 JVM 内部名称提供类字节码
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
@FunctionalInterface
public interface ClassBytecodeProvider {

    /**
     * 根据 JVM 内部名称获取类文件的字节码
     *
     * @param internalName JVM 内部类名（如 "java/lang/Object"）
     * @return 类文件字节数组,若找不到则返回 {@code null}
     */
    byte[] getClassBytes(String internalName);
}
