package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.mcp.job.McpJob;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceRequest;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceResult;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步多引擎对比工具。
 *
 * @author bingbaihanji
 */
public final class ComparisonTools {

    private ComparisonTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(startComparison());
    }

    private static McpToolDefinition startComparison() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "class", Map.of("type", "string"),
                "engines", Map.of("type", "array",
                        "items", Map.of("type", "string",
                                "enum", List.of("PROCYON", "CFR", "VINEFLOWER", "JD", "JADX", "BDEC"))),
                "diffContext", Map.of("type", "integer", "minimum", 0, "maximum", 10),
                "maxDiffLines", Map.of("type", "integer", "minimum", 1, "maximum", 1000)));
        schema.put("required", List.of("class"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "start_engine_comparison",
                "Start an asynchronous decompilation comparison across engines. Poll with get_job; results are per-engine stats and unified diffs.",
                schema,
                (context, args) -> {
                    ClassResolver.ResolvedClass resolved = ClassResolver.resolve(context, args);
                    List<String> engineNames = McpArgs.stringList(args, "engines");
                    List<DecompilerTypeEnum> engines = engineNames.isEmpty()
                            ? java.util.Arrays.asList(DecompilerTypeEnum.values())
                            : engineNames.stream().map(ComparisonTools::parseEngine).toList();
                    if (engines.isEmpty()) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "At least one engine is required", Map.of());
                    }
                    int diffContext = McpArgs.intValue(args, "diffContext", 3, 0, 10);
                    int maxDiffLines = McpArgs.intValue(args, "maxDiffLines", 400, 1, 1000);
                    byte[] bytes = readBytes(resolved);
                    McpJob job = context.jobs().submit("ENGINE_COMPARISON", resolved.workspace()
                            .getSourceFile().getAbsolutePath(), running -> {
                        ClassSourceResult baseline = null;
                        String baselineEngine = null;
                        List<Map<String, Object>> results = new ArrayList<>();
                        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                        try {
                            List<CompletableFuture<EngineResult>> futures = new ArrayList<>();
                            for (DecompilerTypeEnum engine : engines) {
                                futures.add(CompletableFuture.supplyAsync(() ->
                                        decompileOne(context, resolved, bytes, engine,
                                                running), executor));
                            }
                            for (int i = 0; i < futures.size(); i++) {
                                EngineResult result = futures.get(i).join();
                                running.updateProgress(i + 1, engines.size(),
                                        "engine " + result.engine().name());
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("engine", result.engine().name());
                                item.put("ok", !result.failure());
                                item.put("elapsedMs", result.elapsedMs());
                                item.put("lineCount", result.lineCount());
                                item.put("charCount", result.charCount());
                                item.put("sha256", result.sha256());
                                if (result.failure()) {
                                    item.put("failureReason", truncate(result.source(), 200));
                                } else {
                                    if (baseline == null) {
                                        baseline = result.sourceResult();
                                        baselineEngine = result.engine().name();
                                        item.put("baseline", true);
                                    } else {
                                        item.put("diffFrom", baselineEngine);
                                        item.put("diff", unifiedDiff(baselineEngine,
                                                result.engine().name(), baseline.sourceCode(),
                                                result.source(), diffContext, maxDiffLines));
                                    }
                                }
                                results.add(item);
                            }
                        } finally {
                            executor.shutdownNow();
                        }
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("class", resolved.fullPath());
                        data.put("items", results);
                        data.put("baselineEngine", baselineEngine);
                        running.complete(data);
                    });
                    return McpToolResult.ok(Map.of("jobId", job.id(), "state", job.state().name()));
                });
    }

    private static EngineResult decompileOne(McpToolContext context,
                                             ClassResolver.ResolvedClass resolved,
                                             byte[] bytes, DecompilerTypeEnum engine,
                                             McpJob job) {
        long start = System.currentTimeMillis();
        try {
            ClassSourceResult result = context.classSource().load(new ClassSourceRequest(
                    resolved.workspace(), resolved.fullPath(), bytes, engine, Map.of(),
                    false, 30, () -> !job.isCancelled()));
            String source = result.sourceCode() == null ? "" : result.sourceCode();
            return new EngineResult(engine, result, result.failure(),
                    System.currentTimeMillis() - start,
                    source.lines().count(), source.length(),
                    ByteUtils.sha256Hex(source));
        } catch (Exception e) {
            return new EngineResult(engine, null, true, System.currentTimeMillis() - start,
                    0, 0, "", e.getMessage());
        }
    }

    private static String unifiedDiff(String from, String to, String original, String revised,
                                      int context, int maxLines) {
        List<String> left = original.lines().toList();
        List<String> right = revised.lines().toList();
        Patch<String> patch = DiffUtils.diff(left, right);
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff(from, to, left, patch, context);
        if (diff.size() <= maxLines) {
            return String.join("\n", diff);
        }
        return String.join("\n", diff.subList(0, maxLines)) + "\n...[diff truncated]";
    }

    private static DecompilerTypeEnum parseEngine(String value) {
        try {
            return DecompilerTypeEnum.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown engine: " + value, Map.of("engine", value));
        }
    }

    private static byte[] readBytes(ClassResolver.ResolvedClass resolved) {
        try {
            byte[] bytes = com.bingbaihanji.fxdecomplie.service.WorkspaceByteReader
                    .readNodeBytes(resolved.workspace(), resolved.node(), true);
            if (bytes == null || bytes.length == 0) {
                throw new McpToolException("CLASS_NOT_FOUND",
                        "Unable to read class bytes", Map.of("class", resolved.fullPath()));
            }
            return bytes;
        } catch (java.io.IOException e) {
            throw new McpToolException("CLASS_NOT_FOUND",
                    "Unable to read class bytes", Map.of("class", resolved.fullPath()));
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }

    private record EngineResult(DecompilerTypeEnum engine, ClassSourceResult sourceResult,
                                boolean failure, long elapsedMs, long lineCount, long charCount,
                                String sha256, String error) {

        EngineResult(DecompilerTypeEnum engine, ClassSourceResult sourceResult, boolean failure,
                     long elapsedMs, long lineCount, long charCount, String sha256) {
            this(engine, sourceResult, failure, elapsedMs, lineCount, charCount, sha256, null);
        }

        String source() {
            return sourceResult == null || sourceResult.sourceCode() == null
                    ? error == null ? "" : error : sourceResult.sourceCode();
        }
    }
}
