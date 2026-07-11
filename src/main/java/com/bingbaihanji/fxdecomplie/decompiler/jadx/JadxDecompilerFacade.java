package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * fxdecomplie 到移植 jadx 内核的稳定边界 
 */
public final class JadxDecompilerFacade {

    private static final Logger log = LoggerFactory.getLogger(JadxDecompilerFacade.class);
    private static final JadxDecompilerFacade INSTANCE = new JadxDecompilerFacade();

    private final JadxInputBuilder inputBuilder = new JadxInputBuilder();

    private JadxDecompilerFacade() {
    }

    public static JadxDecompilerFacade getInstance() {
        return INSTANCE;
    }

    private static String failed(String typeName, String reason) {
        return "// jadx decompile failed: " + sanitize(reason)
                + "\n// Class: " + (typeName == null || typeName.isBlank() ? "(unknown)" : typeName);
    }

    private static String sanitize(String message) {
        return (message == null || message.isBlank() ? "unknown error" : message)
                .replace("*/", "* /")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    public String decompile(JadxDecompilerRequest request) {
        long start = System.currentTimeMillis();
        try {
            JadxInputPlan inputPlan = inputBuilder.build(request);
            JadxArgs args = JadxArgsFactory.create(request.options());
            try (com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler jadx =
                         new com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler(args)) {
                jadx.addCustomCodeLoader(inputPlan.codeLoader());
                jadx.load();

                List<JavaClass> classes = jadx.getClassesWithInners();
                if (classes.isEmpty()) {
                    log.warn("jadx decompile: no classes loaded for {}", inputPlan.targetType());
                    return failed(inputPlan.targetType(), "no classes loaded");
                }

                JavaClass targetClass = JadxOutputSelector.select(classes, inputPlan.targetType());
                if (targetClass == null) {
                    targetClass = classes.getFirst();
                }

                String source = targetClass.getCode();
                long elapsed = System.currentTimeMillis() - start;
                if (source == null || source.isBlank()) {
                    log.warn("jadx decompile returned empty: {} ({}ms, deps={})",
                            inputPlan.targetType(), elapsed, inputPlan.dependencyClasses());
                    return failed(inputPlan.targetType(), "empty output");
                }

                log.debug("jadx decompile OK: {} ({}ms, classes={}, deps={}, chars={})",
                        inputPlan.targetType(), elapsed, inputPlan.totalClasses(),
                        inputPlan.dependencyClasses(), source.length());
                return source;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String typeName = request == null ? "" : request.typeName();
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage();
            log.error("jadx decompile exception: {} ({}ms): {}", typeName, elapsed, message, e);
            return "// jadx Error: " + sanitize(message)
                    + "\n// Class: " + (typeName == null || typeName.isBlank() ? "(unknown)" : typeName);
        }
    }
}
