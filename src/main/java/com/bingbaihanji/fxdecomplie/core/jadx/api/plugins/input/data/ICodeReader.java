package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 代码读取器接口，用于读取单个方法的 Dalvik 字节码指令及相关元数据
 */
public interface ICodeReader {
    /** 创建当前代码读取器的副本 */
    ICodeReader copy();

    /** 遍历并消费所有指令 */
    void visitInstructions(Consumer<InsnData> insnConsumer);

    /** 获取该方法使用的寄存器总数 */
    int getRegistersCount();

    /** 获取参数起始寄存器的编号 */
    int getArgsStartReg();

    /** 获取指令单元（16位字）总数 */
    int getUnitsCount();

    /** 获取调试信息（可能为 {@code null}） */
    @Nullable
    IDebugInfo getDebugInfo();

    /** 获取代码在 DEX 文件中的字节偏移量 */
    int getCodeOffset();

    /** 获取 try-catch 块列表 */
    List<ITry> getTries();
}
