package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import org.jetbrains.annotations.Nullable;

/**
 * 方法句柄接口。
 * <p>
 * 表示 DEX 字节码中的方法句柄（method handle），用于支持 {@code invokedynamic} 和
 * {@code invoke-polymorphic} 指令。方法句柄可以是字段引用或方法引用，具体类型由
 * {@link #getType()} 返回的 {@link MethodHandleType} 决定。
 */
public interface IMethodHandle {

    /**
     * 获取方法句柄的类型。
     *
     * @return 方法句柄类型，描述该句柄是字段引用还是方法引用以及访问方式
     */
    MethodHandleType getType();

    /**
     * 获取关联的字段引用。当方法句柄类型为字段访问（如 GET/PUT）时有效。
     *
     * @return 字段引用；若当前句柄类型为方法引用类型，则返回 {@code null}
     */
    @Nullable
    IFieldRef getFieldRef();

    /**
     * 获取关联的方法引用。当方法句柄类型为方法调用（如 INVOKE）时有效。
     *
     * @return 方法引用；若当前句柄类型为字段引用类型，则返回 {@code null}
     */
    @Nullable
    IMethodRef getMethodRef();

    /**
     * 加载当前方法句柄所引用的实际数据。
     * 调用此方法后，字段引用或方法引用中的详细信息才能被填充。
     */
    void load();
}
