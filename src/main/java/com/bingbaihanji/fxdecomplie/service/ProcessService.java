package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 新实例启动服务通过 ProcessBuilder 启动第二个 JVM 进程,
 * 实现同一文件的并排对比查看
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessService.class);

    private ProcessService() {
        throw new AssertionError("utility class");
    }

    public static void launchNewInstance(String filePath) {
        try {
            // ---- 定位当前 JVM 的 java 可执行文件 ----
            String javaHome = System.getProperty("java.home");
            String java = javaHome + File.separator + "bin" + File.separator + "java";
            // ---- 从 classloader 解析本应用的 JAR 路径 ----
            var codeSource = ProcessService.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                log.warn("无法确定 JAR 路径,新实例不可用");
                return;
            }
            String jarPath = codeSource.getLocation().toURI().getPath();
            // ---- 修正 Windows 路径前缀(如 "/C:/..." → "C:/...") ----
            if (jarPath.startsWith("/") && jarPath.contains(":")) {
                jarPath = jarPath.substring(1);
            }
            // ---- 启动新进程: java -jar <this.jar> --open <filePath> ----
            new ProcessBuilder(java, "-jar", jarPath, "--open", filePath).start();
        } catch (Exception e) {
            log.error("启动新实例失败", e);
        }
    }
}
