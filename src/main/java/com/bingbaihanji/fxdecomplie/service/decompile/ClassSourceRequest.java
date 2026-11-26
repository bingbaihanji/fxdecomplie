package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 一次类源码加载请求.
 *
 * @param workspace       工作区
 * @param fullPath        类文件完整路径
 * @param classBytes      类字节;为 null 时由服务从工作区读取
 * @param engine          请求的引擎
 * @param optionOverrides 单次调用的引擎选项覆盖
 * @param includeComments 是否插入持久化注释
 * @param timeoutSeconds  反编译超时
 * @param active          请求是否仍然有效
 * @author bingbaihanji
 */
public record ClassSourceRequest(Workspace workspace, String fullPath, byte[] classBytes,
                                 DecompilerTypeEnum engine, Map<String, String> optionOverrides,
                                 boolean includeComments, int timeoutSeconds,
                                 BooleanSupplier active) {

    public ClassSourceRequest {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(fullPath, "fullPath");
        optionOverrides = optionOverrides == null ? Map.of() : Map.copyOf(optionOverrides);
        timeoutSeconds = Math.max(1, timeoutSeconds);
        active = active == null ? () -> true : active;
    }

    public static ClassSourceRequest of(Workspace workspace, String fullPath,
                                        DecompilerTypeEnum engine) {
        return new ClassSourceRequest(workspace, fullPath, null, engine, Map.of(),
                false, 30, () -> true);
    }
}
