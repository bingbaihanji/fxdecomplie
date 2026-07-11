package com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import org.jetbrains.annotations.Nullable;

/**
 * 指令代码偏移量注解，用于将生成的代码行与原始字节码指令位置关联起来
 * 实现 {@link ICodeAnnotation} 接口，注解类型为 {@link AnnType#OFFSET}
 */
public class InsnCodeOffset implements ICodeAnnotation {

    private final int offset;

    /**
     * 构造指令代码偏移量注解
     *
     * @param offset 字节码偏移量
     */
    public InsnCodeOffset(int offset) {
        this.offset = offset;
    }

    /**
     * 将指令节点的字节码偏移量作为行注解附加到代码写入器中
     * 如果指令为 null 或代码写入器不支持元数据，则不执行任何操作
     *
     * @param code 代码写入器
     * @param insn 指令节点
     */
    public static void attach(ICodeWriter code, InsnNode insn) {
        if (insn == null) {
            return;
        }
        if (code.isMetadataSupported()) {
            InsnCodeOffset ann = from(insn);
            if (ann != null) {
                code.attachLineAnnotation(ann);
            }
        }
    }

    /**
     * 将指定的字节码偏移量作为行注解附加到代码写入器中
     * 仅当偏移量非负且代码写入器支持元数据时才会附加
     *
     * @param code   代码写入器
     * @param offset 字节码偏移量
     */
    public static void attach(ICodeWriter code, int offset) {
        if (offset >= 0 && code.isMetadataSupported()) {
            code.attachLineAnnotation(new InsnCodeOffset(offset));
        }
    }

    /**
     * 根据指令节点创建对应的偏移量注解
     *
     * @param insn 指令节点
     * @return 对应的偏移量注解 若指令偏移量小于 0 则返回 null
     */
    @Nullable
    public static InsnCodeOffset from(InsnNode insn) {
        int offset = insn.getOffset();
        if (offset < 0) {
            return null;
        }
        return new InsnCodeOffset(offset);
    }

    /**
     * 获取字节码偏移量
     *
     * @return 字节码偏移量
     */
    public int getOffset() {
        return offset;
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.OFFSET;
    }

    @Override
    public String toString() {
        return "offset=" + offset;
    }
}
