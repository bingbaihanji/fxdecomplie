package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;
import java.util.Map;

/**
 * Vineflower 反编译引擎全部可配置参数定义（与 Recaf VineflowerConfig 对齐）。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class VineflowerParameters {

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            of("din", "1"),
            of("dgs", "1"),
            of("das", "1"),
            of("rbr", "1"),
            of("rsy", "1"),
            of("bto", "1"),
            of("nns", "1"),
            of("ump", "1"),
            of("udv", "1"),
            of("pam", "1"),
            of("swi", "1"),
            of("dec", "1"),
            of("fdi", "1"),
            of("rer", "1"),
            of("ovr", "1"),
            of("isl", "1"),
            of("tco", "1"),
            of("hes", "1"),
            of("hdc", "1"),
            of("den", "1"),

            adv("asc", "0"),
            adv("sns", "0"),
            adv("uto", "0"),
            adv("ner", "1"),
            adv("esm", "1"),
            adv("rgn", "1"),
            adv("lit", "0"),
            adv("dc4", "1"),
            adv("lac", "0"),
            adv("bsm", "0"),
            adv("dcl", "0"),
            adv("iib", "0"),
            adv("vac", "0"),
            adv("tcs", "0"),
            adv("tlf", "1"),
            adv("shs", "0"),
            adv("sst", "1"),
            adv("vvm", "0"),
            adv("ega", "0"),
            adv("sef", "0"),
            adv("wia", "1"),
            adv("dbe", "1"),
            adv("dee", "1"),
            adv("dcc", "0"),
            adv("sfc", "0"),
            adv("ccd", "0"),
            adv("fji", "0"),

            new DecompilerParameter("log", ParamType.ENUM, "ERROR",
                    "engine.vineflower.log", "engine.vineflower.log.help",
                    Category.ADVANCED, new String[]{"ERROR", "WARN", "INFO", "DEBUG"}),
            new DecompilerParameter("mpm", ParamType.INTEGER, "60",
                    "engine.vineflower.mpm", "engine.vineflower.mpm.help",
                    Category.ADVANCED, null),
            new DecompilerParameter("pll", ParamType.INTEGER, "130",
                    "engine.vineflower.pll", "engine.vineflower.pll.help",
                    Category.ADVANCED, null),
            new DecompilerParameter("ind", ParamType.STRING, "    ",
                    "engine.vineflower.ind", "engine.vineflower.ind.help",
                    Category.ADVANCED, null)
    );
    public static final Map<String, String> KEY_LABELS = Map.ofEntries(
            Map.entry("din", "Decompile Inner"),
            Map.entry("dgs", "Decompile Generic Signatures"),
            Map.entry("das", "Decompile Assertions"),
            Map.entry("rbr", "Remove Bridge"),
            Map.entry("rsy", "Remove Synthetic"),
            Map.entry("bto", "Bytecode-to-Source Optimization"),
            Map.entry("nns", "New Number Representation"),
            Map.entry("ump", "Use Method Parameters"),
            Map.entry("udv", "Use Debug Variable Names"),
            Map.entry("pam", "Pattern Matching"),
            Map.entry("swi", "Switch Expressions"),
            Map.entry("dec", "Decompile Preview"),
            Map.entry("fdi", "Decompile Finally"),
            Map.entry("rer", "Remove Empty Ranges"),
            Map.entry("ovr", "Override Annotation"),
            Map.entry("isl", "Inline Simple Lambdas"),
            Map.entry("tco", "Ternary Conditions"),
            Map.entry("hes", "Hide Empty Super"),
            Map.entry("hdc", "Hide Default Constructor"),
            Map.entry("den", "Decompile Enums"),
            Map.entry("asc", "ASCII String Characters"),
            Map.entry("sns", "Synthetic Not Set"),
            Map.entry("uto", "Undefined Param Type Object"),
            Map.entry("ner", "Incorporate Returns"),
            Map.entry("esm", "Ensure Synchronized Monitors"),
            Map.entry("rgn", "Remove GetClass/New"),
            Map.entry("lit", "Keep Literals"),
            Map.entry("dc4", "Decompile Java 1.4"),
            Map.entry("lac", "Lambda to Anonymous Class"),
            Map.entry("bsm", "Bytecode Source Mapping"),
            Map.entry("dcl", "Dump Code Lines"),
            Map.entry("iib", "Ignore Invalid Bytecode"),
            Map.entry("vac", "Verify Anonymous Classes"),
            Map.entry("tcs", "Ternary Constant Simplification"),
            Map.entry("tlf", "Try Loop Fix"),
            Map.entry("shs", "Show Hidden Statements"),
            Map.entry("sst", "Simplify Stack Second Pass"),
            Map.entry("vvm", "Verify Variable Merges"),
            Map.entry("ega", "Explicit Generic Arguments"),
            Map.entry("sef", "Skip Extra Files"),
            Map.entry("wia", "Warn Inconsistent Inner Attributes"),
            Map.entry("dbe", "Dump Bytecode On Error"),
            Map.entry("dee", "Dump Exception On Error"),
            Map.entry("sfc", "Source File Comments"),
            Map.entry("dcc", "Decompiler Comments"),
            Map.entry("ccd", "Decompile Complex Constant Dynamics"),
            Map.entry("fji", "Force JSR Inline"),
            Map.entry("log", "Log Level"),
            Map.entry("mpm", "Max Processing Time (s)"),
            Map.entry("pll", "Preferred Line Length"),
            Map.entry("ind", "Indent String")
    );

    private VineflowerParameters() {
        throw new AssertionError("constants");
    }

    private static DecompilerParameter of(String vfKey, String defaultValue) {
        return new DecompilerParameter(vfKey, ParamType.BOOLEAN, defaultValue,
                "engine.vineflower." + vfKey, "engine.vineflower." + vfKey + ".help",
                Category.COMMON, null);
    }

    private static DecompilerParameter adv(String vfKey, String defaultValue) {
        return new DecompilerParameter(vfKey, ParamType.BOOLEAN, defaultValue,
                "engine.vineflower." + vfKey, "engine.vineflower." + vfKey + ".help",
                Category.ADVANCED, null);
    }
}
