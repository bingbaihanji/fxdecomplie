package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.CommentData;

import java.util.Comparator;
import java.util.List;

/**
 * 导出源码时插入持久化注释的装饰器
 *
 * <p>匹配策略：sourceHash+optionsHash 精确匹配 → 成员签名定位 → 行号降级</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class CommentExportDecorator {

    private CommentExportDecorator() {
        throw new AssertionError("utility class");
    }

    /**
     * 在源码中插入注释
     *
     * @param sourceCode       原始反编译源码
     * @param comments         该类的注释列表
     * @param currentSourceHash 当前源码 hash
     * @param currentOptionsHash 当前选项 hash
     * @return 插入注释后的源码
     */
    public static String apply(String sourceCode, List<CommentData> comments,
                                String currentSourceHash, String currentOptionsHash) {
        if (sourceCode == null || comments == null || comments.isEmpty()) {
            return sourceCode;
        }

        String[] lines = sourceCode.split("\n", -1);

        // 按行号倒序排列，从后往前插入避免行号偏移
        List<CommentData> sorted = comments.stream()
                .sorted(Comparator.comparingInt(CommentData::line).reversed())
                .toList();

        for (CommentData c : sorted) {
            int targetLine = resolveTargetLine(c, lines, currentSourceHash, currentOptionsHash);
            if (targetLine >= 1 && targetLine <= lines.length) {
                lines[targetLine - 1] = insert(lines[targetLine - 1], c);
            }
        }

        return String.join("\n", lines);
    }

    /**
     * 确定注释插入的目标行号
     */
    public static int resolveTargetLine(CommentData c, String[] lines,
                                         String currentSourceHash, String currentOptionsHash) {
        // 策略1: hash 匹配，直接使用保存时的行号
        if (c.sourceHash() != null && c.sourceHash().equals(currentSourceHash)
                && c.optionsHash() != null && c.optionsHash().equals(currentOptionsHash)) {
            return c.line();
        }

        // 策略2: 成员签名匹配
        if (c.memberSignature() != null && !c.memberSignature().isBlank()) {
            int nameIdx = c.memberSignature().indexOf('(');
            String methodName = nameIdx > 0 ? c.memberSignature().substring(0, nameIdx) : c.memberSignature();
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(methodName)) {
                    return i + 1 + Math.max(0, c.line() - 1 - i);
                }
            }
        }

        // 策略3: 按原始行号降级（可能不准）
        return c.line();
    }

    /** 在源码行中插入注释文本 */
    public static String insert(String sourceLine, CommentData c) {
        if (c == null || c.text().isEmpty()) {
            return sourceLine;
        }
        String safeText = c.text()
                .replace("*/", "*\\/")
                .replace("\n", " ")
                .replace("\r", "");

        return switch (c.style()) {
            case LINE -> sourceLine + " // FXD: " + safeText;
            case BLOCK -> "/* FXD: " + safeText + " */\n" + sourceLine;
        };
    }
}
