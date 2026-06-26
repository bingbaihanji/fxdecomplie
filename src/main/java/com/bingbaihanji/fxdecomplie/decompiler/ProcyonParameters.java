package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * Procyon 反编译引擎全部可配置参数定义（与 Recaf ProcyonConfig 对齐）。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class ProcyonParameters {

    private ProcyonParameters() { throw new AssertionError("constants"); }

    private static DecompilerParameter of(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.procyon." + key, "engine.procyon." + key + ".help", Category.COMMON, null);
    }

    private static DecompilerParameter adv(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.procyon." + key, "engine.procyon." + key + ".help", Category.ADVANCED, null);
    }

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // -- 常用 10 项 --
            of("unicodeOutputEnabled", "true"),
            of("previewFeaturesEnabled", "true"),
            of("showSyntheticMembers", "false"),
            of("forceExplicitImports", "true"),
            of("forceExplicitTypeArguments", "false"),
            of("flattenSwitchBlocks", "false"),
            of("retainRedundantCasts", "false"),
            of("includeLineNumbersInBytecode", "true"),
            of("showDebugLineNumbers", "false"),
            of("simplifyMemberReferences", "false"),

            // -- 高级 11 项 --
            adv("alwaysGenerateExceptionVariableForCatchBlocks", "true"),
            adv("forceFullyQualifiedReferences", "false"),
            adv("excludeNestedTypes", "false"),
            adv("retainPointlessSwitches", "false"),
            adv("includeErrorDiagnostics", "true"),
            adv("mergeVariables", "false"),
            adv("disableForEachTransforms", "false"),
            new DecompilerParameter("textBlockLineMinimum", ParamType.INTEGER, "3",
                    "engine.procyon.textBlockLineMinimum",
                    "engine.procyon.textBlockLineMinimum.help", Category.ADVANCED, null),
            new DecompilerParameter("forcedCompilerTarget", ParamType.ENUM, "",
                    "engine.procyon.forcedCompilerTarget",
                    "engine.procyon.forcedCompilerTarget.help", Category.ADVANCED,
                    new String[]{"", "JAVA_1", "JAVA_2", "JAVA_3", "JAVA_4", "JAVA_5",
                            "JAVA_6", "JAVA_7", "JAVA_8", "JAVA_9", "JAVA_10", "JAVA_11",
                            "JAVA_12", "JAVA_13", "JAVA_14", "JAVA_15", "JAVA_16", "JAVA_17"}),
            new DecompilerParameter("bytecodeOutputOptions", ParamType.ENUM, "",
                    "engine.procyon.bytecodeOutputOptions",
                    "engine.procyon.bytecodeOutputOptions.help", Category.ADVANCED,
                    new String[]{"", "PREFER_SOURCE", "PREFER_BYTECODE", "BYTECODE_ONLY"}),
            new DecompilerParameter("languageTarget", ParamType.ENUM, "",
                    "engine.procyon.languageTarget",
                    "engine.procyon.languageTarget.help", Category.ADVANCED,
                    new String[]{"", "JAVA", "SCALA", "KOTLIN", "GROOVY"})
    );
}
