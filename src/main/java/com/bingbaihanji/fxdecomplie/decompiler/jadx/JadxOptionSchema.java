package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * jadx 选项 schema — 单一定义源
 * <p>
 * {@link com.bingbaihanji.fxdecomplie.decompiler.JadxParameters}
 * {@link com.bingbaihanji.fxdecomplie.util.jadx.JadxOptionsBridge}
 * 和 {@link com.bingbaihanji.fxdecomplie.service.DecompilerOptions#hash}
 * 均消费此 schema，确保 key/默认值/类型定义一致
 *
 * @author bingbaihanji
 */
public final class JadxOptionSchema {
    private static final Logger log = LoggerFactory.getLogger(JadxOptionSchema.class);
    private static final List<OptionDef> SCHEMA = List.of(
            def("showInconsistentCode", OptionType.BOOLEAN, true, "engine.jadx.showInconsistentCode"),
            def("useImports", OptionType.BOOLEAN, true, "engine.jadx.useImports"),
            def("debugInfo", OptionType.BOOLEAN, true, "engine.jadx.debugInfo"),
            def("extractFinally", OptionType.BOOLEAN, true, "engine.jadx.extractFinally"),
            def("inlineAnonymousClasses", OptionType.BOOLEAN, true, "engine.jadx.inlineAnonymousClasses"),
            def("inlineMethods", OptionType.BOOLEAN, true, "engine.jadx.inlineMethods"),
            def("moveInnerClasses", OptionType.BOOLEAN, true, "engine.jadx.moveInnerClasses"),
            def("deobfuscationOn", OptionType.BOOLEAN, false, "engine.jadx.deobfuscationOn"),
            def("restoreSwitchOverString", OptionType.BOOLEAN, true, "engine.jadx.restoreSwitchOverString"),
            def("replaceConsts", OptionType.BOOLEAN, true, "engine.jadx.replaceConsts"),
            def("skipResources", OptionType.BOOLEAN, true, "engine.jadx.skipResources"),
            def("skipSources", OptionType.BOOLEAN, false, "engine.jadx.skipSources"),
            def("insertDebugLines", OptionType.BOOLEAN, false, "engine.jadx.insertDebugLines"),
            def("allowInlineKotlinLambda", OptionType.BOOLEAN, true, "engine.jadx.allowInlineKotlinLambda"),
            def("escapeUnicode", OptionType.BOOLEAN, false, "engine.jadx.escapeUnicode"),
            def("respectBytecodeAccModifiers", OptionType.BOOLEAN, false, "engine.jadx.respectBytecodeAccModifiers"),
            def("skipXmlPrettyPrint", OptionType.BOOLEAN, false, "engine.jadx.skipXmlPrettyPrint"),
            defInt("threadsCount", 4),
            defInt("deobfuscationMinLength", 0),
            defInt("deobfuscationMaxLength", Integer.MAX_VALUE),
            defInt("sourceNameRepeatLimit", 10),
            defInt("typeUpdatesLimitCount", 10)
    );

    private JadxOptionSchema() {
        throw new AssertionError("utility class");
    }

    private static OptionDef def(String key, OptionType type, Object defaultValue, String i18nKey) {
        return new OptionDef(key, type, defaultValue, i18nKey);
    }

    private static OptionDef defInt(String key, int defaultValue) {
        return new OptionDef(key, OptionType.INTEGER, defaultValue, "engine.jadx." + key);
    }

    /** 返回完整 schema */
    public static List<OptionDef> schema() {
        return SCHEMA;
    }

    /** 从 Map 解析 JadxEngineOptions，缺失 key 使用 schema 默认值 */
    public static JadxEngineOptions fromOptionsMap(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return JadxEngineOptions.DEFAULTS;
        }
        return new JadxEngineOptions(
                bool(options, "useImports", true),
                bool(options, "debugInfo", true),
                bool(options, "showInconsistentCode", true),
                bool(options, "extractFinally", true),
                bool(options, "inlineMethods", true),
                bool(options, "inlineAnonymousClasses", true),
                bool(options, "moveInnerClasses", true),
                bool(options, "allowInlineKotlinLambda", true),
                bool(options, "deobfuscationOn", false),
                intRange(options, "deobfuscationMinLength", 0, 0, Integer.MAX_VALUE),
                intRange(options, "deobfuscationMaxLength", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                intRange(options, "sourceNameRepeatLimit", 10, 0, Integer.MAX_VALUE),
                bool(options, "restoreSwitchOverString", true),
                bool(options, "replaceConsts", true),
                bool(options, "escapeUnicode", false),
                bool(options, "insertDebugLines", false),
                bool(options, "respectBytecodeAccModifiers", false),
                intRange(options, "threadsCount", JadxEngineOptions.DEFAULTS.threadsCount(), 1, 64),
                intRange(options, "typeUpdatesLimitCount", 10, 1, 100),
                bool(options, "skipResources", true),
                bool(options, "skipSources", false),
                bool(options, "skipXmlPrettyPrint", false)
        );
    }

    /** 生成规范化字符串用于 hash 计算 */
    public static String toCanonicalString(JadxEngineOptions opts) {
        if (opts == null) {
            opts = JadxEngineOptions.DEFAULTS;
        }
        JadxEngineOptions o = opts;
        return "useImports=" + o.useImports()
                + ",debugInfo=" + o.debugInfo()
                + ",showInconsistentCode=" + o.showInconsistentCode()
                + ",extractFinally=" + o.extractFinally()
                + ",inlineMethods=" + o.inlineMethods()
                + ",inlineAnonymousClasses=" + o.inlineAnonymousClasses()
                + ",moveInnerClasses=" + o.moveInnerClasses()
                + ",allowInlineKotlinLambda=" + o.allowInlineKotlinLambda()
                + ",deobfuscationOn=" + o.deobfuscationOn()
                + ",deobfuscationMinLength=" + o.deobfuscationMinLength()
                + ",deobfuscationMaxLength=" + o.deobfuscationMaxLength()
                + ",sourceNameRepeatLimit=" + o.sourceNameRepeatLimit()
                + ",restoreSwitchOverString=" + o.restoreSwitchOverString()
                + ",replaceConsts=" + o.replaceConsts()
                + ",escapeUnicode=" + o.escapeUnicode()
                + ",insertDebugLines=" + o.insertDebugLines()
                + ",respectBytecodeAccModifiers=" + o.respectBytecodeAccModifiers()
                + ",threadsCount=" + o.threadsCount()
                + ",typeUpdatesLimitCount=" + o.typeUpdatesLimitCount()
                + ",skipResources=" + o.skipResources()
                + ",skipSources=" + o.skipSources()
                + ",skipXmlPrettyPrint=" + o.skipXmlPrettyPrint();
    }

    private static boolean bool(Map<String, String> options, String key, boolean defaultValue) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return defaultValue;
    }

    private static int intRange(Map<String, String> options, String key,
                                int defaultValue, int min, int max) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException e) {
            log.debug("jadx option {} value '{}' is not a valid integer, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public enum OptionType {BOOLEAN, INTEGER}

    public record OptionDef(String key, OptionType type, Object defaultValue, String i18nKey) {
    }
}
