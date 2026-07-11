package com.bingbaihanji.fxdecomplie.core.jadx.core.export.gen;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.FileUtils;

import java.io.File;
import java.util.List;

public class GradleGeneratorTools {

    public static String guessProjectName(RootNode root) {
        List<File> inputFiles = root.getArgs().getInputFiles();
        if (inputFiles.size() == 1) {
            return FileUtils.getPathBaseName(inputFiles.get(0).toPath());
        }
        // default
        return "PROJECT_NAME";
    }
}
