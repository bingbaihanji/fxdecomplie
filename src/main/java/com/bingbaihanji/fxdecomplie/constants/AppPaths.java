package com.bingbaihanji.fxdecomplie.constants;

import com.bingbaihanji.fxdecomplie.config.AppConfig;

import java.nio.file.Path;

/**
 * 应用级目录约定集中入口 
 * <p>
 * 配置根目录仍由 {@link AppConfig#appDir()} 决定，此处只收口各业务模块使用的子目录 
 */
public final class AppPaths {

    private AppPaths() {
        throw new AssertionError("constants");
    }

    public static Path appDir() {
        return AppConfig.appDir();
    }

    public static Path logsDir() {
        return appDir().resolve("logs");
    }

    public static Path startupErrorLog() {
        return logsDir().resolve("startup-error.log");
    }

    public static Path cacheDir() {
        return appDir().resolve("cache");
    }

    public static Path workspaceDataDir() {
        return appDir().resolve("fxdecomplie");
    }

    public static Path commentsDir() {
        return workspaceDataDir().resolve("comments");
    }

    public static Path renamesDir() {
        return workspaceDataDir().resolve("renames");
    }

    public static Path themesDir() {
        return appDir().resolve("themes");
    }
}
