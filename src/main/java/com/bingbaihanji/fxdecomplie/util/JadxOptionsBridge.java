package com.bingbaihanji.fxdecomplie.util;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import java.util.Map;

/**
 * 将 {@code Map<String, String>} 选项映射到 {@link JadxArgs} setter 的桥接工具。
 * <p>
 * key 名称与 {@link com.bingbaihanji.fxdecomplie.decompiler.JadxParameters} 的定义保持一致。
 */
public final class JadxOptionsBridge {

    private JadxOptionsBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * 将选项 Map 中的值应用到指定的 JadxArgs 实例。
     * 未出现在 map 中的 key 保留 JadxArgs 默认值不变。
     */
    public static void apply(JadxArgs args, Map<String, String> options) {
        if (args == null || options == null || options.isEmpty()) {
            return;
        }
        BooleanValue v;

        v = bool(options, "showInconsistentCode");
        if (v.set) args.setShowInconsistentCode(v.value);
        v = bool(options, "useImports");
        if (v.set) args.setUseImports(v.value);
        v = bool(options, "debugInfo");
        if (v.set) args.setDebugInfo(v.value);
        v = bool(options, "extractFinally");
        if (v.set) args.setExtractFinally(v.value);
        v = bool(options, "inlineAnonymousClasses");
        if (v.set) args.setInlineAnonymousClasses(v.value);
        v = bool(options, "inlineMethods");
        if (v.set) args.setInlineMethods(v.value);
        v = bool(options, "moveInnerClasses");
        if (v.set) args.setMoveInnerClasses(v.value);
        v = bool(options, "deobfuscationOn");
        if (v.set) args.setDeobfuscationOn(v.value);
        v = bool(options, "restoreSwitchOverString");
        if (v.set) args.setRestoreSwitchOverString(v.value);
        v = bool(options, "replaceConsts");
        if (v.set) args.setReplaceConsts(v.value);
        v = bool(options, "skipResources");
        if (v.set) args.setSkipResources(v.value);
        v = bool(options, "skipSources");
        if (v.set) args.setSkipSources(v.value);
        v = bool(options, "insertDebugLines");
        if (v.set) args.setInsertDebugLines(v.value);
        v = bool(options, "allowInlineKotlinLambda");
        if (v.set) args.setAllowInlineKotlinLambda(v.value);
        v = bool(options, "escapeUnicode");
        if (v.set) args.setEscapeUnicode(v.value);
        v = bool(options, "respectBytecodeAccModifiers");
        if (v.set) args.setRespectBytecodeAccModifiers(v.value);
        v = bool(options, "skipXmlPrettyPrint");
        if (v.set) args.setSkipXmlPrettyPrint(v.value);

        intValue(options, "threadsCount").ifSet(args::setThreadsCount);
        intValue(options, "deobfuscationMinLength").ifSet(args::setDeobfuscationMinLength);
        intValue(options, "deobfuscationMaxLength").ifSet(args::setDeobfuscationMaxLength);
        intValue(options, "sourceNameRepeatLimit").ifSet(args::setSourceNameRepeatLimit);
        intValue(options, "typeUpdatesLimitCount").ifSet(args::setTypeUpdatesLimitCount);
    }

    // --- helpers ---

    private record BooleanValue(boolean set, boolean value) {}

    private static BooleanValue bool(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) return new BooleanValue(false, false);
        return new BooleanValue(true, "true".equalsIgnoreCase(v) || "1".equals(v));
    }

    private record IntValue(boolean set, int value) {
        void ifSet(java.util.function.IntConsumer c) { if (set) c.accept(value); }
    }

    private static IntValue intValue(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isEmpty()) return new IntValue(false, 0);
        try {
            return new IntValue(true, Integer.parseInt(v));
        } catch (NumberFormatException e) {
            return new IntValue(false, 0);
        }
    }
}
