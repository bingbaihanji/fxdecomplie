package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.decoders;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.Opcode;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.ConstPoolReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.ConstantType;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.DataReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.stack.StackValueType;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.CodeDecodeState;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.JavaInsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.JavaClassParseException;

/**
 * 常量加载指令解码器。
 * <p>
 * 负责解码 {@code ldc} / {@code ldc_w}（{@code wide=false}）与 {@code ldc2_w}（{@code wide=true}）
 * 类指令：从常量池中取出常量索引，根据常量类型（int/float/long/double/String/Class 等）生成对应的
 * 中间表示指令，并将结果压入模拟操作数栈。
 */
public class LoadConstDecoder implements IJavaInsnDecoder {
    /** 是否为宽索引指令（{@code true} 时使用 2 字节索引，否则使用 1 字节索引） */
    private final boolean wide;

    /**
     * 构造常量加载解码器。
     *
     * @param wide {@code true} 表示使用 2 字节常量池索引（如 {@code ldc_w}/{@code ldc2_w}），
     *             {@code false} 表示使用 1 字节索引（如 {@code ldc}）
     */
    public LoadConstDecoder(boolean wide) {
        this.wide = wide;
    }

    /**
     * 解码常量加载指令：读取常量池索引，按常量类型设置指令的操作码与字面量/索引，并更新操作数栈状态。
     *
     * @param state 当前代码解码状态（包含读取器、当前指令及栈操作）
     * @throws JavaClassParseException 当遇到不支持的常量类型时抛出
     */
    @Override
    public void decode(CodeDecodeState state) {
        DataReader reader = state.reader();
        JavaInsnData insn = state.insn();
        int index;
        if (wide) {
            index = reader.readU2();
        } else {
            index = reader.readU1();
        }
        ConstPoolReader constPoolReader = insn.constPoolReader();
        ConstantType constType = constPoolReader.jumpToConst(index);
        switch (constType) {
            // int / float：作为窄类型字面量常量加载
            case INTEGER:
            case FLOAT:
                insn.setLiteral(constPoolReader.readU4());
                insn.setOpcode(Opcode.CONST);
                state.push(0, StackValueType.NARROW);
                break;

            // long / double：作为宽类型字面量常量加载
            case LONG:
            case DOUBLE:
                insn.setLiteral(constPoolReader.readU8());
                insn.setOpcode(Opcode.CONST_WIDE);
                state.push(0, StackValueType.WIDE);
                break;

            // String 常量：索引指向常量池中的字符串项
            case STRING:
                insn.setIndex(constPoolReader.readU2());
                insn.setOpcode(Opcode.CONST_STRING);
                state.push(0, StackValueType.NARROW);
                break;

            // UTF8 常量：直接以当前索引作为字符串常量
            case UTF8:
                insn.setIndex(index);
                insn.setOpcode(Opcode.CONST_STRING);
                state.push(0, StackValueType.NARROW);
                break;

            // Class 常量：加载类引用
            case CLASS:
                insn.setIndex(index);
                insn.setOpcode(Opcode.CONST_CLASS);
                state.push(0, StackValueType.NARROW);
                break;

            default:
                throw new JavaClassParseException("Unsupported constant type: " + constType);
        }
    }
}
