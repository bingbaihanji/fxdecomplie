package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 解析反编译器特定选项和缓存键的共享辅助方法
 *
 * @author bingbaihanji
 * @date 2026-07-07
 */
public final class DecompilerOptions {

    /**
     * Vineflower 选项别名映射(缩写 → 规范名称),同时供 VineflowerDecompiler 和本类使用
     * 允许用户在配置中使用简短别名(如 {@code rbr})代替完整的选项键名(如 {@code deobfuscate-bridge-methods}),
     * 在 normalize 阶段统一转换为规范名称以确保选项识别一致
     */
    public static final Map<String, String> VINEFLOWER_OPTION_ALIASES = Map.ofEntries(
            Map.entry("rbr", IFernflowerPreferences.REMOVE_BRIDGE),
            Map.entry("rsy", IFernflowerPreferences.REMOVE_SYNTHETIC),
            Map.entry("din", IFernflowerPreferences.DECOMPILE_INNER),
            Map.entry("dc4", IFernflowerPreferences.DECOMPILE_CLASS_1_4),
            Map.entry("das", IFernflowerPreferences.DECOMPILE_ASSERTIONS),
            Map.entry("hes", IFernflowerPreferences.HIDE_EMPTY_SUPER),
            Map.entry("hdc", IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR),
            Map.entry("dgs", IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES),
            Map.entry("ner", IFernflowerPreferences.INCORPORATE_RETURNS),
            Map.entry("esm", IFernflowerPreferences.ENSURE_SYNCHRONIZED_MONITOR),
            Map.entry("den", IFernflowerPreferences.DECOMPILE_ENUM),
            Map.entry("dec", IFernflowerPreferences.DECOMPILE_PREVIEW),
            Map.entry("rgn", IFernflowerPreferences.REMOVE_GET_CLASS_NEW),
            Map.entry("lit", IFernflowerPreferences.LITERALS_AS_IS),
            Map.entry("bto", IFernflowerPreferences.BOOLEAN_TRUE_ONE),
            Map.entry("asc", IFernflowerPreferences.ASCII_STRING_CHARACTERS),
            Map.entry("sns", IFernflowerPreferences.SYNTHETIC_NOT_SET),
            Map.entry("uto", IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT),
            Map.entry("udv", IFernflowerPreferences.USE_DEBUG_VAR_NAMES),
            Map.entry("ump", IFernflowerPreferences.USE_METHOD_PARAMETERS),
            Map.entry("rer", IFernflowerPreferences.REMOVE_EMPTY_RANGES),
            Map.entry("fdi", IFernflowerPreferences.FINALLY_DEINLINE),
            Map.entry("lac", IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS),
            Map.entry("bsm", IFernflowerPreferences.BYTECODE_SOURCE_MAPPING),
            Map.entry("dcl", IFernflowerPreferences.DUMP_ORIGINAL_LINES),
            Map.entry(IFernflowerPreferences.DUMP_CODE_LINES, IFernflowerPreferences.DUMP_ORIGINAL_LINES),
            Map.entry("iib", IFernflowerPreferences.IGNORE_INVALID_BYTECODE),
            Map.entry("vac", IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES),
            Map.entry("tcs", IFernflowerPreferences.TERNARY_CONSTANT_SIMPLIFICATION),
            Map.entry("pam", IFernflowerPreferences.PATTERN_MATCHING),
            Map.entry("tlf", IFernflowerPreferences.TRY_LOOP_FIX),
            Map.entry("tco", IFernflowerPreferences.TERNARY_CONDITIONS),
            Map.entry("swi", IFernflowerPreferences.SWITCH_EXPRESSIONS),
            Map.entry("shs", IFernflowerPreferences.SHOW_HIDDEN_STATEMENTS),
            Map.entry("ovr", IFernflowerPreferences.OVERRIDE_ANNOTATION),
            Map.entry("sst", IFernflowerPreferences.SIMPLIFY_STACK_SECOND_PASS),
            Map.entry("vvm", IFernflowerPreferences.VERIFY_VARIABLE_MERGES),
            Map.entry("ega", IFernflowerPreferences.EXPLICIT_GENERIC_ARGUMENTS),
            Map.entry("isl", IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS),
            Map.entry("log", IFernflowerPreferences.LOG_LEVEL),
            Map.entry("mpm", IFernflowerPreferences.MAX_PROCESSING_METHOD),
            Map.entry("pll", IFernflowerPreferences.PREFERRED_LINE_LENGTH),
            Map.entry("ind", IFernflowerPreferences.INDENT_STRING),
            Map.entry("sef", IFernflowerPreferences.SKIP_EXTRA_FILES),
            Map.entry("wia", IFernflowerPreferences.WARN_INCONSISTENT_INNER_CLASSES),
            Map.entry("dbe", IFernflowerPreferences.DUMP_BYTECODE_ON_ERROR),
            Map.entry("dee", IFernflowerPreferences.DUMP_EXCEPTION_ON_ERROR),
            Map.entry("dcc", IFernflowerPreferences.DECOMPILER_COMMENTS),
            Map.entry("sfc", IFernflowerPreferences.SOURCE_FILE_COMMENTS),
            Map.entry("ccd", IFernflowerPreferences.DECOMPILE_COMPLEX_CONDYS),
            Map.entry("fji", IFernflowerPreferences.FORCE_JSR_INLINE)
    );

    private DecompilerOptions() {
        throw new AssertionError("utility class");
    }

    /**
     * 从应用配置中提取指定引擎的选项,并做大小写不敏感查找和 Vineflower 别名归一化
     *
     * @param appConfig 应用配置
     * @param engine    目标反编译引擎
     * @return 归一化后的引擎选项,未找到则返回空 Map
     */
    public static Map<String, String> forEngine(AppConfig appConfig,
                                                DecompilerTypeEnum engine) {
        if (appConfig == null || appConfig.decompiler().engineOptions() == null || engine == null) {
            return Map.of();
        }
        var options = findEngineOptions(appConfig.decompiler().engineOptions(), engine);
        return options == null ? Map.of() : normalize(engine, options);
    }

    /** 大小写不敏感地从所有引擎选项中查找指定引擎的配置 */
    private static Map<String, String> findEngineOptions(Map<String, Map<String, String>> allOptions,
                                                         DecompilerTypeEnum engine) {
        Map<String, String> exact = allOptions.get(engine.name());
        if (exact != null) {
            return exact;
        }
        String expected = engine.name().toLowerCase(Locale.ROOT);
        for (var entry : allOptions.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().toLowerCase(Locale.ROOT).equals(expected)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 对选项键名进行归一化：Vineflower 引擎将缩写别名替换为规范名称,其他引擎直接复制
     * 跳过 null 和空白键名的条目
     */
    private static Map<String, String> normalize(DecompilerTypeEnum engine,
                                                 Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        if (engine != DecompilerTypeEnum.VINEFLOWER) {
            return Map.copyOf(options);
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (var entry : options.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String canonicalKey = VINEFLOWER_OPTION_ALIASES.getOrDefault(key, key);
            normalized.put(canonicalKey, entry.getValue());
        }
        return Map.copyOf(normalized);
    }

    /**
     * 为选项 Map 生成稳定的内容哈希
     * 使用 SHA-256 避免特殊字符(= ,)导致的键值碰撞
     *
     * @param options 引擎选项键值对
     * @return 选项哈希,空选项返回 "default"
     */
    public static String hash(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return "default";
        }
        String canonical = options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return ByteUtils.sha256Hex(canonical).substring(0, 16);
    }
}
