package com.bingbaihanji.fxdecomplie.model.reference;

/**
 * 继承引用节点类型枚举
 *
 * @author bingbaihanji
 * @date 2026-07-20
 */
public enum Kind {
    /** 当前类自身 */
    SELF,
    /** 父类 */
    SUPER_CLASS,
    /** 接口 */
    INTERFACE,
    /** 注解 */
    ANNOTATION,
    /** 实现类(接口的实现) */
    IMPLEMENTATION,
    /** 子类 */
    SUBCLASS,
    /** 重写了父类/接口的方法 */
    OVERRIDES,
    /** 被子类重写的方法 */
    OVERRIDDEN_BY,
    /** 继承但未重写的方法 */
    INHERITED,
    /** 未解析/占位节点 */
    UNRESOLVED
}
