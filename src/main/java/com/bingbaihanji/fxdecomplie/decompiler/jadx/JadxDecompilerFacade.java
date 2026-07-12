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

    private static String sanitize(String message) {
        return (message == null || message.isBlank() ? "unknown error" : message)
                .replace("*/", "* /")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    public String decompile(JadxDecompilerRequest request) {
        JadxDecompilerResult result = decompileResult(request);
        if (result.isSuccess()) {
            return result.source();
        }
        JadxDiagnostic diag = result.diagnostic();
        return "// jadx decompile failed: " + sanitize(diag != null ? diag.message() : "unknown error")
                + "\n// Class: " + (diag != null && diag.className() != null
                ? diag.className() : "(unknown)");
    }

    /**
     * 结构化反编译，返回包含状态和诊断信息的结果对象
     */
    public JadxDecompilerResult decompileResult(JadxDecompilerRequest request) {
        long start = System.currentTimeMillis();
        String typeName = request == null ? "" : request.typeName();
        try {
            JadxInputPlan inputPlan = inputBuilder.build(request);
            JadxArgs args = JadxArgsFactory.create(request.options());
            try (com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler jadx =
                         new com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler(args)) {
                jadx.addCustomCodeLoader(inputPlan.codeLoader());
                jadx.load();

                List<JavaClass> classes = jadx.getClassesWithInners();
                if (classes.isEmpty()) {
                    long elapsed = System.currentTimeMillis() - start;
                    log.warn("jadx decompile: no classes loaded for {}", inputPlan.targetType());
                    return new JadxDecompilerResult(null, JadxResultStatus.NO_CLASSES_LOADED,
                            new JadxDiagnostic(JadxResultStatus.NO_CLASSES_LOADED,
                                    "no classes loaded", inputPlan.targetType(), elapsed));
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
                    return new JadxDecompilerResult(null, JadxResultStatus.EMPTY_OUTPUT,
                            new JadxDiagnostic(JadxResultStatus.EMPTY_OUTPUT,
                                    "empty output", inputPlan.targetType(), elapsed));
                }

                if (args.isDeobfuscationOn()) {
                    JadxProjectRenameSynchronizer.syncDeobfAliases(
                            request.context().workspaceHash(), classes);
                }

                log.debug("jadx decompile OK: {} ({}ms, classes={}, deps={}, chars={})",
                        inputPlan.targetType(), elapsed, inputPlan.totalClasses(),
                        inputPlan.dependencyClasses(), source.length());
                return new JadxDecompilerResult(source, JadxResultStatus.OK,
                        new JadxDiagnostic(JadxResultStatus.OK, "OK", inputPlan.targetType(), elapsed));
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage();
            log.error("jadx decompile exception: {} ({}ms): {}", typeName, elapsed, message, e);
            return new JadxDecompilerResult(null, JadxResultStatus.EXCEPTION,
                    new JadxDiagnostic(JadxResultStatus.EXCEPTION,
                            sanitize(message), typeName.isBlank() ? null : typeName, elapsed));
        }
    }
}
