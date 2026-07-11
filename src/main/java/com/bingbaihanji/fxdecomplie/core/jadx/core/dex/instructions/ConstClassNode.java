package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;

/**
 * 表示 Dex 字节码中的 const-class 指令节点。
 * <p>
 * 该指令将一个类引用（{@code Class<T>} 类型）加载到寄存器中，
 * 对应 Java 中的 {@code Foo.class} 表达式。
 * </p>
 */
public final class ConstClassNode extends InsnNode {

    /** 该指令引用的类类型 */
    private final ArgType clsType;

    /**
     * 构造一个 const-class 指令节点。
     *
     * @param clsType 引用的类类型
     */
    public ConstClassNode(ArgType clsType) {
        super(InsnType.CONST_CLASS, 0);
        this.clsType = clsType;
    }

    /**
     * 获取该指令引用的类类型。
     *
     * @return 类 ArgType
     */
    public ArgType getClsType() {
        return clsType;
    }

    @Override
    public InsnNode copy() {
        return copyCommonParams(new ConstClassNode(clsType));
    }

    @Override
    public boolean isSame(InsnNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConstClassNode) || !super.isSame(obj)) {
            return false;
        }
        ConstClassNode other = (ConstClassNode) obj;
        return clsType.equals(other.clsType);
    }

    @Override
    public String toString() {
        return super.toString() + ' ' + clsType + ".class";
    }
}
