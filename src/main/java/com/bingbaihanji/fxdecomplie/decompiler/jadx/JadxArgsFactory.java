package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.GeneratedRenamesMappingFileMode;
import com.bingbaihanji.fxdecomplie.util.jadx.JadxOptionsBridge;

import java.util.Map;

/**
 * 创建项目默认的 jadx 参数并应用用户配置
 */
public final class JadxArgsFactory {

    private JadxArgsFactory() {
        throw new AssertionError("utility class");
    }

    /** 从 JadxEngineOptions 创建 JadxArgs */
    public static JadxArgs create(JadxEngineOptions options) {
        JadxArgs args = new JadxArgs();
        args.setSkipResources(true);
        args.setRootDir(AppPaths.jadxRuntimeDir().toFile());
        args.setGeneratedRenamesMappingFileMode(GeneratedRenamesMappingFileMode.IGNORE);
        JadxOptionsBridge.apply(args, options);
        return args;
    }

    /** 从 Map 创建 JadxArgs（兼容旧调用方） */
    public static JadxArgs create(Map<String, String> engineOptions) {
        return create(JadxOptionSchema.fromOptionsMap(engineOptions));
    }
}
