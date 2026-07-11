package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxAdapterOptions;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * jadx 反编译引擎可配置参数定义
 *
 * @author bingbaihanji
 * @date 2026-07-11
 */
public final class JadxParameters {

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // ===== 常用参数 =====
            of("showInconsistentCode", "true"),
            of("useImports", "true"),
            of("debugInfo", "true"),
            of("extractFinally", "true"),
            of("inlineAnonymousClasses", "true"),
            of("inlineMethods", "true"),
            of("moveInnerClasses", "true"),
            of("deobfuscationOn", "false"),
            of("restoreSwitchOverString", "true"),
            of("replaceConsts", "true"),

            // ===== 高级参数 =====
            of("skipResources", "true"),
            of("skipSources", "false"),
            of("insertDebugLines", "false"),
            of("allowInlineKotlinLambda", "true"),
            of("escapeUnicode", "false"),
            of("respectBytecodeAccModifiers", "false"),
            of("skipXmlPrettyPrint", "false"),
            ofInt("threadsCount", "4"),
            ofInt("deobfuscationMinLength", "0"),
            ofInt("deobfuscationMaxLength", "2147483647"),
            ofInt("sourceNameRepeatLimit", "10"),
            ofInt("typeUpdatesLimitCount", "10"),
            adv(JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES, "true"),
            ofInt(JadxAdapterOptions.WORKSPACE_DEPENDENCY_LIMIT, "96"),
            ofInt(JadxAdapterOptions.WORKSPACE_DEPENDENCY_DEPTH, "1")
    );

    /** 私有构造器,防止实例化常量类 */
    private JadxParameters() {
        throw new AssertionError("constants");
    }

    /** 创建一个分类为 {@link Category#COMMON} 的布尔型参数 */
    private static DecompilerParameter of(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.jadx." + key, "engine.jadx." + key + ".help", Category.COMMON, null);
    }

    /** 创建一个分类为 {@link Category#ADVANCED} 的整数型参数 */
    private static DecompilerParameter ofInt(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.INTEGER, defaultValue,
                "engine.jadx." + key, "engine.jadx." + key + ".help", Category.ADVANCED, null);
    }

    /** 创建一个分类为 {@link Category#ADVANCED} 的布尔型参数 */
    private static DecompilerParameter adv(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.jadx." + key, "engine.jadx." + key + ".help", Category.ADVANCED, null);
    }
}
