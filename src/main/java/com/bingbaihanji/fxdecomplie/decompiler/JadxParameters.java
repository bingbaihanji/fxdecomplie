package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxAdapterOptions;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxOptionSchema;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxOptionSchema.OptionDef;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxOptionSchema.OptionType;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.ArrayList;
import java.util.List;

/**
 * jadx 反编译引擎可配置参数定义
 * <p>
 * 参数列表从 {@link JadxOptionSchema} 生成，确保与 {@code JadxOptionsBridge}
 * 和 {@code DecompilerOptions.hash()} 的 key/默认值一致。
 *
 * @author bingbaihanji
 */
public final class JadxParameters {

    public static final List<DecompilerParameter> PARAMETERS = buildParameters();

    private JadxParameters() {
        throw new AssertionError("constants");
    }

    private static List<DecompilerParameter> buildParameters() {
        List<DecompilerParameter> params = new ArrayList<>();
        // 引擎参数 — 从 schema 生成
        for (OptionDef def : JadxOptionSchema.schema()) {
            Category category = Category.COMMON;
            if (def.type() == OptionType.INTEGER) {
                category = Category.ADVANCED;
            }
            String key = def.key();
            String defaultValue = def.defaultValue().toString();
            params.add(new DecompilerParameter(key,
                    def.type() == OptionType.BOOLEAN ? ParamType.BOOLEAN : ParamType.STRING,
                    defaultValue, def.i18nKey(), def.i18nKey() + ".help", category, null));
        }
        // 适配器参数 — 不属于内核引擎选项，手动添加
        params.add(new DecompilerParameter(JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES,
                ParamType.BOOLEAN, "true",
                "engine.jadx." + JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES,
                "engine.jadx." + JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES + ".help",
                Category.ADVANCED, null));
        params.add(new DecompilerParameter(JadxAdapterOptions.WORKSPACE_DEPENDENCY_LIMIT,
                ParamType.STRING, "96",
                "engine.jadx." + JadxAdapterOptions.WORKSPACE_DEPENDENCY_LIMIT,
                "engine.jadx." + JadxAdapterOptions.WORKSPACE_DEPENDENCY_LIMIT + ".help",
                Category.ADVANCED, null));
        params.add(new DecompilerParameter(JadxAdapterOptions.WORKSPACE_DEPENDENCY_DEPTH,
                ParamType.STRING, "1",
                "engine.jadx." + JadxAdapterOptions.WORKSPACE_DEPENDENCY_DEPTH,
                "engine.jadx." + JadxAdapterOptions.WORKSPACE_DEPENDENCY_DEPTH + ".help",
                Category.ADVANCED, null));
        return List.copyOf(params);
    }
}
