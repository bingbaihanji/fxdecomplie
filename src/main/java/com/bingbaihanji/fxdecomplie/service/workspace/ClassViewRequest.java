package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.Objects;

/**
 * MCP 请求在 GUI 中打开指定类标签页.
 *
 * @param workspace 工作区
 * @param fullPath  类文件完整路径
 * @param engine    请求的反编译引擎;HEX 模式可为 null
 * @param mode      视图模式
 * @author bingbaihanji
 */
public record ClassViewRequest(Workspace workspace, String fullPath,
                               DecompilerTypeEnum engine, ClassViewMode mode) {

    public ClassViewRequest {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(mode, "mode");
    }
}
