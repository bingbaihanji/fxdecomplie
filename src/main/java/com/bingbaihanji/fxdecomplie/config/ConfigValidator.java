package com.bingbaihanji.fxdecomplie.config;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置合法性校验器 — 在保存前和执行时验证关键字段.
 *
 * <p>校验规则:
 * <ul>
 *   <li>窗口宽度 ≥ 400px</li>
 *   <li>窗口高度 ≥ 300px</li>
 *   <li>默认引擎名称合法</li>
 *   <li>编辑器字体大小在 8-72 之间</li>
 *   <li>搜索结果上限 ≤ 100,000</li>
 * </ul>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class ConfigValidator {

    /** 最小窗口宽度 */
    public static final int MIN_WINDOW_WIDTH = 400;

    /** 最小窗口高度 */
    public static final int MIN_WINDOW_HEIGHT = 300;

    /** 编辑器最小字体 */
    public static final int MIN_FONT_SIZE = 8;

    /** 编辑器最大字体 */
    public static final int MAX_FONT_SIZE = 72;

    /** 搜索结果上限 */
    public static final int MAX_SEARCH_LIMIT = 100_000;

    private ConfigValidator() {
        throw new AssertionError("utility class");
    }

    /**
     * 校验配置,返回所有发现的错误.
     *
     * @param config 要校验的配置
     * @return 错误列表(空列表表示配置合法)
     */
    public static List<String> validate(AppConfig config) {
        List<String> errors = new ArrayList<>();

        // 窗口尺寸
        if (config.window().width() < MIN_WINDOW_WIDTH) {
            errors.add("窗口宽度不能小于 " + MIN_WINDOW_WIDTH
                    + "px,当前: " + config.window().width());
        }
        if (config.window().height() < MIN_WINDOW_HEIGHT) {
            errors.add("窗口高度不能小于 " + MIN_WINDOW_HEIGHT
                    + "px,当前: " + config.window().height());
        }

        // 引擎合法性
        DecompilerTypeEnum engine = config.decompiler().defaultEngine();
        if (engine == null) {
            errors.add("默认反编译引擎不能为空");
        }

        // 字体大小
        int fontSize = config.theme().fontSize();
        if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
            errors.add("编辑器字体大小必须在 " + MIN_FONT_SIZE + "-"
                    + MAX_FONT_SIZE + " 之间,当前: " + fontSize);
        }

        // 搜索上限
        if (config.search().resultLimit() > MAX_SEARCH_LIMIT) {
            errors.add("搜索结果上限不能超过 " + MAX_SEARCH_LIMIT
                    + ",当前: " + config.search().resultLimit());
        }

        return errors;
    }

    /**
     * 校验并返回第一个错误(或 null 表示合法).
     */
    public static String firstError(AppConfig config) {
        List<String> errors = validate(config);
        return errors.isEmpty() ? null : errors.get(0);
    }

    /**
     * 配置是否合法.
     */
    public static boolean isValid(AppConfig config) {
        return validate(config).isEmpty();
    }
}
