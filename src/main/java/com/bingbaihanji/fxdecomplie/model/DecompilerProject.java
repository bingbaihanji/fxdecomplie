package com.bingbaihanji.fxdecomplie.model;

import java.util.List;

/**
 * 轻量级项目文件模型
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record DecompilerProject(
        int version,
        String engine,
        List<String> inputPaths,
        String selectedInputPath,
        String exportPath
) {
    public DecompilerProject {
        inputPaths = inputPaths == null ? List.of() : List.copyOf(inputPaths);
        engine = engine == null ? "" : engine;
        selectedInputPath = selectedInputPath == null ? "" : selectedInputPath;
        exportPath = exportPath == null ? "" : exportPath;
    }
}
