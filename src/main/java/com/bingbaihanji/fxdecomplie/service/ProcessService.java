package com.bingbaihanji.fxdecomplie.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 启动新的应用实例打开指定文件,用于并排对比查看
     *
     * <p>通过 {@link ProcessBuilder} 启动第二个 JVM 进程,子进程的
     * stdin/stdout/stderr 继承父进程,避免管道缓冲区满导致子进程挂起</p>
     *
     * @param filePath 要在新窗口中打开的文件路径(可为 null)
     */
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
            String jarPath = Paths.get(codeSource.getLocation().toURI()).toString();
            // ---- 启动新进程: java -jar <this.jar> [--open <filePath>] ----
            List<String> cmd = new ArrayList<>(List.of(java, "-jar", jarPath));
            if (filePath != null) {
                cmd.addAll(List.of("--open", filePath));
            }
            // inheritIO() 防止子进程 stdout/stderr 管道缓冲区满导致挂起
            new ProcessBuilder(cmd)
                    .inheritIO()
                    .start();
        } catch (Exception e) {
            log.error("启动新实例失败", e);
        }
    }
}
