package com.bingbaihanji.fxdecomplie.constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动期路径引导 — 在初始化任何日志/第三方库之前，把运行期资源收拢到应用根目录.
 *
 * <p>应用根目录优先取 JAR 所在目录；开发期 classpath 为 {@code target/classes/} 时
 * 回退到项目根。这里刻意不使用 SLF4J，避免日志系统在目录确定前初始化.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class BootstrapPaths {

    /** logback 使用的应用根目录属性 */
    public static final String APP_DIR_PROPERTY = "fxdecomplie.appDir";

    /** 临时文件目录（位于应用 cache 下） */
    public static final String TEMP_DIR_NAME = "tmp";

    private static volatile Path appDir;

    private static volatile Path tempDir;

    private static volatile boolean configured;

    private BootstrapPaths() {
        throw new AssertionError("utility class");
    }

    /** 解析应用根目录：JAR 同级目录；开发期回退到项目根. */
    public static Path resolveAppDir() {
        Path cached = appDir;
        if (cached != null) {
            return cached;
        }
        synchronized (BootstrapPaths.class) {
            if (appDir == null) {
                appDir = resolveAppDirUncached();
            }
            return appDir;
        }
    }

    /** 应用内临时目录：{@code <appDir>/cache/tmp}. */
    public static Path tempDir() {
        Path cached = tempDir;
        if (cached != null) {
            return cached;
        }
        synchronized (BootstrapPaths.class) {
            if (tempDir == null) {
                tempDir = resolveAppDir().resolve("cache").resolve(TEMP_DIR_NAME);
            }
            return tempDir;
        }
    }

    /** 仅设置 logback 所需的应用目录属性；不改变 JVM 临时目录. */
    public static void configureAppDir() {
        System.setProperty(APP_DIR_PROPERTY, resolveAppDir().toString());
    }

    /**
     * 配置全部运行期目录属性：日志根目录、{@code java.io.tmpdir}、JNA 与 JavaFX 缓存.
     *
     * <p>必须在 ClassGraph/JADX/JNA/JavaFX 首次创建临时文件前调用. 幂等.</p>
     */
    public static void configure() {
        if (configured) {
            return;
        }
        synchronized (BootstrapPaths.class) {
            if (configured) {
                return;
            }
            Path root = resolveAppDir();
            System.setProperty(APP_DIR_PROPERTY, root.toString());
            Path temp = tempDir();
            Path javaFxCache = root.resolve("cache").resolve("javafx");
            try {
                Files.createDirectories(temp);
            } catch (IOException ignored) {
            }
            try {
                Files.createDirectories(javaFxCache);
            } catch (IOException ignored) {
            }
            System.setProperty("java.io.tmpdir", temp.toString());
            System.setProperty("jna.tmpdir", temp.toString());
            System.setProperty("javafx.cachedir", javaFxCache.toString());
            System.setProperty("prism.cachedir", javaFxCache.toString());
            configured = true;
        }
    }

    /** 测试专用：清除缓存的应用目录/配置状态，不修改系统属性. */
    static void resetForTesting() {
        appDir = null;
        tempDir = null;
        configured = false;
    }

    private static Path resolveAppDirUncached() {
        try {
            var codeSource = BootstrapPaths.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path location = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(location) && location.toString().endsWith(".jar")) {
                    Path parent = location.getParent();
                    if (parent != null) {
                        return parent.toAbsolutePath().normalize();
                    }
                } else if (Files.isDirectory(location)) {
                    return appRoot(location);
                }
            }
        } catch (Exception ignored) {
            // 回退到当前工作目录
        }
        return Path.of(System.getProperty("user.dir"));
    }

    private static Path appRoot(Path codeSourceDir) {
        Path normalized = codeSourceDir.toAbsolutePath().normalize();
        Path name = normalized.getFileName();
        if (name != null && "classes".equalsIgnoreCase(name.toString())) {
            Path target = normalized.getParent();
            if (target != null && target.getFileName() != null
                    && "target".equalsIgnoreCase(target.getFileName().toString())
                    && target.getParent() != null) {
                return target.getParent();
            }
        }
        return normalized;
    }
}
