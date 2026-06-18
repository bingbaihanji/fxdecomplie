package com.bingbihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 新实例启动服务。通过 ProcessBuilder 启动第二个 JVM 进程，
 * 实现同一文件的并排对比查看。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ProcessService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);

    private ProcessService() {
        throw new AssertionError("utility class");
    }

    public static void launchNewInstance(String filePath) {
        try {
            // ---- Locate the current JVM's java executable ----
            String javaHome = System.getProperty("java.home");
            String java = javaHome + File.separator + "bin" + File.separator + "java";
            // ---- Resolve this application's own JAR path from the classloader ----
            var codeSource = ProcessService.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                logger.warn("Cannot determine JAR path, new instance not available");
                return;
            }
            String jarPath = codeSource.getLocation().toURI().getPath();
            // ---- Fix Windows path prefix (e.g. "/C:/..." -> "C:/...") ----
            if (jarPath.startsWith("/") && jarPath.contains(":")) {
                jarPath = jarPath.substring(1);
            }
            // ---- Launch new process: java -jar <this.jar> --open <filePath> ----
            new ProcessBuilder(java, "-jar", jarPath, "--open", filePath).start();
        } catch (Exception e) {
            logger.error("Failed to launch new instance", e);
        }
    }
}
