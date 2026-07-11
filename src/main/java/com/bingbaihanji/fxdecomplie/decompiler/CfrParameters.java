package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * CFR 反编译引擎全部可配置参数定义(与 Recaf CfrConfig 对齐)
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class CfrParameters {

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // ===== 常用参数 20 项 =====
            of("decodeenumswitch", "true"),
            of("sugarenums", "true"),
            of("decodestringswitch", "true"),
            of("decodelambdas", "true"),
            of("innerclasses", "true"),
            of("tryresources", "true"),
            of("decodefinally", "true"),
            of("removebadgenerics", "true"),
            of("sugarasserts", "true"),
            of("sugarboxing", "true"),
            of("stringbuilder", "true"),
            of("stringconcat", "true"),
            of("recordtypes", "true"),
            of("sealedclasses", "true"),
            of("switchexpression", "true"),
            of("instanceofpattern", "true"),
            of("previewfeatures", "true"),
            of("showinferrable", "true"),
            of("override", "true"),
            of("eclipse", "true"),

            // ===== 高级参数 62 项 =====
            adv("showversion", "false"),
            adv("hideutf", "false"),
            adv("hidelongstrings", "false"),
            adv("removeboilerplate", "false"),
            adv("removeinnerclasssynthetics", "false"),
            adv("relinkconst", "false"),
            adv("relinkconststring", "false"),
            adv("liftconstructorinit", "false"),
            adv("removedeadmethods", "false"),
            adv("sugarretrolambda", "false"),
            adv("tidymonitors", "false"),
            adv("commentmonitors", "false"),
            adv("lenient", "true"),
            adv("comments", "false"),
            adv("antiobf", "false"),
            adv("obfcontrol", "false"),
            adv("obfattr", "false"),
            adv("constobf", "false"),
            adv("hidebridgemethods", "false"),
            adv("ignoreexceptions", "false"),
            adv("version", "false"),
            adv("labelledblocks", "false"),
            adv("j14classobj", "false"),
            adv("hidelangimports", "false"),
            adv("renamedupmembers", "false"),
            adv("renameillegalidents", "false"),
            adv("renameenumidents", "false"),
            adv("staticinitreturn", "false"),
            adv("usenametable", "false"),
            adv("pullcodecase", "false"),
            adv("elidescala", "false"),
            adv("usesignatures", "false"),
            adv("arrayiter", "false"),
            adv("collectioniter", "false"),
            adv("forbidmethodscopedclasses", "false"),
            adv("forbidanonymousclasses", "false"),
            adv("skipbatchinnerclasses", "false"),
            adv("recover", "true"),
            adv("forcetopsort", ""),
            adv("forloopaggcapture", ""),
            adv("forcetopsortaggress", ""),
            adv("forcetopsortnopull", ""),
            adv("forcecondpropagate", ""),
            adv("reducecondscope", ""),
            adv("forcereturningifs", ""),
            adv("forceexceptionprune", ""),
            adv("aexagg", ""),
            adv("aexagg2", ""),
            adv("recovertypeclash", ""),
            adv("recovertypehints", ""),
            adv("removedeadconditionals", ""),
            adv("aggressivedoextension", ""),
            adv("aggressiveduff", ""),
            adv("allowmalformedswitch", ""),
            adv("ignoreexceptionsalways", ""),
            advInt("renamesmallmembers", "0"),
            advInt("aggressivedocopy", "0"),
            advInt("aggressivesizethreshold", "13000"),
            advStr("forceclassfilever", "")
    );

    /** 私有构造器,防止实例化常量类 */
    private CfrParameters() {
        throw new AssertionError("constants");
    }

    /** 创建一个分类为 {@link Category#COMMON} 的布尔型参数 */
    private static DecompilerParameter of(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.COMMON, null);
    }

    /** 创建一个分类为 {@link Category#ADVANCED} 的布尔型参数 */
    private static DecompilerParameter adv(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }

    /** 创建一个分类为 {@link Category#ADVANCED} 的整数型参数 */
    private static DecompilerParameter advInt(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.INTEGER, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }

    /** 创建一个分类为 {@link Category#ADVANCED} 的字符串型参数 */
    private static DecompilerParameter advStr(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.STRING, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }
}
