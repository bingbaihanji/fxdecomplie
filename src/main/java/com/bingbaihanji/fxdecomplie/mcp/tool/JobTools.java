package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.mcp.job.McpJob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步任务查询与取消工具.
 *
 * @author bingbaihanji
 */
public final class JobTools {

    private JobTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(getJob(), getJobResult(), cancelJob());
    }

    private static McpToolDefinition getJob() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "jobId", Map.of("type", "string"),
                "previewLimit", Map.of("type", "integer", "minimum", 0, "maximum", 50)));
        schema.put("required", List.of("jobId"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_job",
                "Return job state, progress, message and a short result preview.",
                schema,
                (context, args) -> {
                    McpJob job = context.jobs().find(McpArgs.requireString(args, "jobId"))
                            .orElseThrow(() -> new McpToolException("JOB_NOT_FOUND",
                                    "Job not found", Map.of()));
                    int previewLimit = McpArgs.intValue(args, "previewLimit", 10, 0, 50);
                    Map<String, Object> data = summary(job);
                    Object result = job.result();
                    List<?> resultItems = resultItems(result);
                    if (resultItems != null) {
                        data.put("resultTotal", resultItems.size());
                        data.put("resultPreview", boundedPreview(resultItems, previewLimit));
                        data.put("resultPaged", true);
                    } else if (result instanceof Map<?, ?> map) {
                        data.put("result", map);
                    }
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition getJobResult() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "jobId", Map.of("type", "string"),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200)));
        schema.put("required", List.of("jobId"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_job_result",
                "Return a page of a list-shaped job result (search matches, suggestions, engine diffs).",
                schema,
                (context, args) -> {
                    McpJob job = context.jobs().find(McpArgs.requireString(args, "jobId"))
                            .orElseThrow(() -> new McpToolException("JOB_NOT_FOUND",
                                    "Job not found", Map.of()));
                    if (job.state() != McpJob.State.SUCCEEDED) {
                        throw new McpToolException("JOB_NOT_FINISHED",
                                "Job state is " + job.state().name(),
                                Map.of("state", job.state().name()));
                    }
                    Object result = job.result();
                    List<?> list = resultItems(result);
                    if (list == null) {
                        return McpToolResult.ok(Map.of("jobId", job.id(), "result", result));
                    }
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 50, 1, 200);
                    int total = list.size();
                    int end = Math.min(total, offset + limit);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("jobId", job.id());
                    if (result instanceof Map<?, ?> map) {
                        for (var entry : map.entrySet()) {
                            if (entry.getKey() != null && !"items".equals(entry.getKey().toString())) {
                                data.put(entry.getKey().toString(), entry.getValue());
                            }
                        }
                    }
                    data.put("items", offset >= total ? List.of() : list.subList(offset, end));
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition cancelJob() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("jobId", Map.of("type", "string")));
        schema.put("required", List.of("jobId"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "cancel_job",
                "Cancel a running job. Files already written by an export are kept.",
                schema,
                (context, args) -> {
                    String jobId = McpArgs.requireString(args, "jobId");
                    McpJob job = context.jobs().find(jobId)
                            .orElseThrow(() -> new McpToolException("JOB_NOT_FOUND",
                                    "Job not found", Map.of("jobId", jobId)));
                    if (job.state() == McpJob.State.SUCCEEDED
                            || job.state() == McpJob.State.FAILED
                            || job.state() == McpJob.State.CANCELLED) {
                        throw new McpToolException("JOB_NOT_CANCELABLE",
                                "Job is already " + job.state().name(),
                                Map.of("state", job.state().name()));
                    }
                    boolean cancelled = context.jobs().cancel(jobId);
                    if (!cancelled) {
                        throw new McpToolException("JOB_NOT_CANCELABLE",
                                "Job cannot be cancelled", Map.of("jobId", jobId));
                    }
                    return McpToolResult.ok(Map.of("jobId", jobId, "canceled", true));
                });
    }

    /** 结果本身是列表,或 Map 中的 items 列表. */
    private static List<?> resultItems(Object result) {
        if (result instanceof List<?> list) {
            return list;
        }
        if (result instanceof Map<?, ?> map && map.get("items") instanceof List<?> items) {
            return items;
        }
        return null;
    }

    /** 预览只保留少量条目并截断长字符串,避免把 diff 全量塞回上下文. */
    private static List<?> boundedPreview(List<?> items, int limit) {
        List<Object> preview = new ArrayList<>();
        int max = Math.min(limit, items.size());
        for (int i = 0; i < max; i++) {
            Object item = items.get(i);
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    Object value = entry.getValue();
                    if (value instanceof String text && text.length() > 4000) {
                        value = text.substring(0, 4000) + "...[truncated]";
                    }
                    copy.put(entry.getKey().toString(), value);
                }
                preview.add(copy);
            } else {
                preview.add(item);
            }
        }
        return preview;
    }

    public static Map<String, Object> summary(McpJob job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.id());
        data.put("type", job.type());
        data.put("workspaceId", job.workspaceId());
        data.put("state", job.state().name());
        data.put("processed", job.processed());
        data.put("total", job.total());
        data.put("progress", job.progress());
        data.put("message", job.message());
        data.put("createdAt", job.createdAt().toString());
        data.put("updatedAt", job.updatedAt().toString());
        return data;
    }

}
