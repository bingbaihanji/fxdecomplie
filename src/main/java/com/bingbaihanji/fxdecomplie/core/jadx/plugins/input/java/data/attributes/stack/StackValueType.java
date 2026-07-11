package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.stack;

/**
 * 栈值类型，表示 Java 虚拟机操作数栈上值的宽度类别
 * <p>
 * 在字节码的 StackMapTable 属性中，栈帧的 verification_type_info 需要区分
 * 两类值：普通宽度类型 (如 int、float、引用等)和双宽度类型 (long、double)
 * </p>
 */
public enum StackValueType {

    /** 窄类型：int、float、short、引用等占一个槽位的类型 */
    NARROW,

    /** 宽类型：long、double 等占两个槽位的类型 */
    WIDE,
}
