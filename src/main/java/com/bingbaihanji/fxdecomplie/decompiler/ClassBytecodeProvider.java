package com.bingbaihanji.fxdecomplie.decompiler;

/**
 * Provides class bytes by JVM internal name.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
@FunctionalInterface
public interface ClassBytecodeProvider {
    byte[] getClassBytes(String internalName);
}
