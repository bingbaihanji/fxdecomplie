package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;

import java.util.List;
import java.util.Map;

/**
 * 按反编译引擎类型查询参数定义的注册表
 * 替代各处对 XxxParameters.PARAMETERS 的硬编码引用,统一收口引擎参数查询
 *
 * @author bingbaihanji
 */
public final class EngineParameters {

    private static final Map<DecompilerTypeEnum, List<DecompilerParameter>> REGISTRY = Map.of(
            DecompilerTypeEnum.CFR, CfrParameters.PARAMETERS,
            DecompilerTypeEnum.PROCYON, ProcyonParameters.PARAMETERS,
            DecompilerTypeEnum.VINEFLOWER, VineflowerParameters.PARAMETERS,
            DecompilerTypeEnum.JADX, JadxParameters.PARAMETERS,
            DecompilerTypeEnum.JD, JdParameters.PARAMETERS);

    private EngineParameters() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 返回指定引擎的参数定义列表
     *
     * @param type 反编译引擎类型
     * @return 参数定义列表,未知类型返回空列表(不会为 null)
     */
    public static List<DecompilerParameter> forType(DecompilerTypeEnum type) {
        return REGISTRY.getOrDefault(type, List.of());
    }
}
