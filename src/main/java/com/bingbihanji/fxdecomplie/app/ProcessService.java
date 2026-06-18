package com.bingbihanji.fxdecomplie.app;

import java.io.File;

/**
 * 新实例启动服务。通过 ProcessBuilder 启动第二个 JVM 进程，
 * 实现同一文件的并排对比查看。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ProcessService {

    private ProcessService() { throw new AssertionError("utility class"); }

    private static final System.Logger LOG = System.getLogger(ProcessService.class.getName());

    public static void launchNewInstance(String filePath) {
        try {
            String javaHome = System.getProperty("java.home");
            String java = javaHome + File.separator + "bin" + File.separator + "java";
            var codeSource = ProcessService.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                LOG.log(System.Logger.Level.WARNING, "Cannot determine JAR path, new instance not available");
                return;
            }
            String jarPath = codeSource.getLocation().toURI().getPath();
            if (jarPath.startsWith("/") && jarPath.contains(":")) {
                jarPath = jarPath.substring(1);
            }
            new ProcessBuilder(java, "-jar", jarPath, "--open", filePath).start();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to launch new instance", e);
        }
    }
}
