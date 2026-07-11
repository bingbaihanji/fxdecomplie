package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.debuginfo;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ILocalVar;
import org.jetbrains.annotations.Nullable;

/**
 * Java 局部变量信息
 * <p>
 * 实现 {@link ILocalVar} 接口，表示 class 文件中 LocalVariableTable 属性
 * 的单个变量条目包含变量名、类型描述符、泛型签名、寄存器编号以及
 * 在字节码中的有效范围（起止偏移量）
 * </p>
 */
public class JavaLocalVar implements ILocalVar {

    /** 局部变量名 */
    private final String name;
    /** 局部变量的类型描述符 */
    private final String type;
    /** 变量在字节码中的起始偏移量 */
    private final int startOffset;
    /** 变量在字节码中的结束偏移量 */
    private final int endOffset;
    /** 寄存器编号（可能被 shiftRegNum 调整） */
    private int regNum;
    /** 泛型签名信息，可能为 null */
    @Nullable
    private String sign;

    /**
     * 构造一个 Java 局部变量实例
     *
     * @param regNum      寄存器编号
     * @param name        变量名
     * @param type        类型描述符
     * @param sign        泛型签名，可为 null
     * @param startOffset 字节码起始偏移量
     * @param endOffset   字节码结束偏移量
     */
    public JavaLocalVar(int regNum, String name, @Nullable String type, @Nullable String sign, int startOffset, int endOffset) {
        this.regNum = regNum;
        this.name = name;
        this.type = type;
        this.sign = sign;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    /**
     * 将字节码偏移量格式化为十六进制字符串
     *
     * @param offset 字节码偏移量
     * @return 格式为 {@code 0xNNNN} 的十六进制字符串
     */
    private static String formatOffset(int offset) {
        return String.format("0x%04x", offset);
    }

    /**
     * 将局部变量号转换为寄存器号
     * <p>
     * 在 Java 字节码中，方法参数先于局部变量占用寄存器槽位
     * 此方法通过加上 maxStack（即方法参数占用的槽位数）将
     * 局部变量索引偏移为实际的寄存器编号
     * </p>
     *
     * @param maxStack 方法参数占用的寄存器槽位数
     */
    public void shiftRegNum(int maxStack) {
        this.regNum += maxStack; // 将局部变量索引转换为寄存器编号
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getRegNum() {
        return regNum;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public @Nullable String getSignature() {
        return sign;
    }

    /**
     * 设置泛型签名信息
     *
     * @param sign 泛型签名字符串
     */
    public void setSignature(String sign) {
        this.sign = sign;
    }

    @Override
    public int getStartOffset() {
        return startOffset;
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    /**
     * 判断该局部变量是否被标记为方法参数
     * <p>
     * 当前实现始终返回 {@code false}，因为在 Java 类文件格式中
     * 局部变量表不直接提供该标记
     * </p>
     *
     * @return 始终返回 {@code false}
     */
    @Override
    public boolean isMarkedAsParameter() {
        return false;
    }

    @Override
    public int hashCode() {
        int result = regNum;
        result = 31 * result + name.hashCode();
        result = 31 * result + startOffset;
        result = 31 * result + endOffset;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaLocalVar)) {
            return false;
        }
        JavaLocalVar other = (JavaLocalVar) o;
        return regNum == other.regNum
                && startOffset == other.startOffset
                && endOffset == other.endOffset
                && name.equals(other.name);
    }

    @Override
    public String toString() {
        return formatOffset(startOffset) + '-' + formatOffset(endOffset)
                + ": r" + regNum + " '" + name + "' " + type
                + (sign != null ? ", signature: " + sign : "");
    }
}
