package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;

import java.util.List;

/**
 * JD-Core 反编译引擎参数定义
 * JD-Core 1.1.3 不支持外部配置参数,保留空列表以统一设置界面
 *
 * @author bingbaihanji
 * @date 2026-07-11
 */
public final class JdParameters {

    /** JD-Core 无可配置参数 */
    public static final List<DecompilerParameter> PARAMETERS = List.of();

    /** 私有构造器,防止实例化常量类 */
    private JdParameters() {
        throw new AssertionError("constants");
    }
}
