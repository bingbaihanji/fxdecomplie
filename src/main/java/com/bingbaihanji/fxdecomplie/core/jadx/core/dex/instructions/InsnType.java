package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

public enum InsnType {

    /** 常量赋值 */
    CONST,
    /** 字符串常量赋值 */
    CONST_STR,
    /** 类常量赋值 */
    CONST_CLASS,

    /** 算术运算 */
    ARITH,
    /** 取负 */
    NEG,
    /** 逻辑取反 */
    NOT,

    /** 寄存器移动/赋值 */
    MOVE,
    /** 多寄存器移动 */
    MOVE_MULTI,
    /** 类型转换 */
    CAST,

    /** 方法返回 */
    RETURN,
    /** 无条件跳转 */
    GOTO,

    /** 抛出异常 */
    THROW,
    /** 异常捕获并赋值给寄存器 */
    MOVE_EXCEPTION,

    /** 长整型/双精度浮点比较 */
    CMP_L,
    /** 整型/单精度浮点比较 */
    CMP_G,
    /** 条件跳转 */
    IF,
    /** switch 分支跳转 */
    SWITCH,
    /** switch 分支数据表 */
    SWITCH_DATA,

    /** 进入同步块 (获取锁) */
    MONITOR_ENTER,
    /** 退出同步块 (释放锁) */
    MONITOR_EXIT,

    /** 类型检查转换 */
    CHECK_CAST,
    /** instanceof 类型检查 */
    INSTANCE_OF,

    /** 获取数组长度 */
    ARRAY_LENGTH,
    /** 填充数组 */
    FILL_ARRAY,
    /** 填充数组数据 */
    FILL_ARRAY_DATA,
    /** 创建并填充新数组 */
    FILLED_NEW_ARRAY,

    /** 数组元素读取 */
    AGET,
    /** 数组元素写入 */
    APUT,

    /** 创建新数组 */
    NEW_ARRAY,
    /** 创建新实例 (对象) */
    NEW_INSTANCE,

    /** 读取实例字段 */
    IGET,
    /** 写入实例字段 */
    IPUT,

    /** 读取静态字段 */
    SGET,
    /** 写入静态字段 */
    SPUT,

    /** 方法调用 */
    INVOKE,
    /** 获取方法调用结果 */
    MOVE_RESULT,

    // *** 以下为附加指令 ***

    /** 空操作 (替代被移除的指令) */
    NOP,

    /** 三元运算 */
    TERNARY,
    /** 构造函数调用 */
    CONSTRUCTOR,

    /** 跳出循环 */
    BREAK,
    /** 继续循环 */
    CONTINUE,

    /** 字符串拼接 */
    STR_CONCAT,

    /** 仅生成单个参数 */
    ONE_ARG,
    /** SSA Phi 节点 (合并多个控制流路径的值) */
    PHI,

    /** 伪指令，用于区域代码生成时保留待使用的参数 */
    REGION_ARG,

    /** Java 特有的 JSR 跳转指令 */
    JAVA_JSR,
    /** Java 特有的 JSR 返回指令 */
    JAVA_RET,
}
