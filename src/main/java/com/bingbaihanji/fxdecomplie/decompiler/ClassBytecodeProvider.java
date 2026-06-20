package com.bingbaihanji.fxdecomplie.decompiler;

/**
 * 通过 JVM 内部名称提供类字节码
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
@FunctionalInterface
public interface ClassBytecodeProvider {
    byte[] getClassBytes(String internalName);
}
