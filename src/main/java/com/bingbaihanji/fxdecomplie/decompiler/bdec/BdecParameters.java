package com.bingbaihanji.fxdecomplie.decompiler.bdec;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * bdec 反编译引擎可配置参数定义
 *
 * <p>从 bdec {@code BdecConfig} 的配置项映射,key 使用 BdecConfig builder 字段名.</p>
 *
 * @author bingbaihanji
 * @date 2026-08-17
 */
public final class BdecParameters {

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            param("indentSize", ParamType.INTEGER, "4", Category.COMMON),
            param("showLineNumbers", ParamType.BOOLEAN, "false", Category.COMMON),
            param("decodeEnums", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeLambdas", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeTernary", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeStringConcat", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeTryResource", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeForEach", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeStringSwitch", ParamType.BOOLEAN, "true", Category.COMMON),
            param("collapseImports", ParamType.BOOLEAN, "true", Category.COMMON),
            param("ssaThreshold", ParamType.INTEGER, "20", Category.ADVANCED)
    );

    private BdecParameters() {
        throw new AssertionError("constants");
    }

    private static DecompilerParameter param(String key, ParamType type,
                                             String def, Category category) {
        return new DecompilerParameter(key, type, def,
                "engine.bdec." + key, "engine.bdec." + key + ".help", category, null);
    }
}
