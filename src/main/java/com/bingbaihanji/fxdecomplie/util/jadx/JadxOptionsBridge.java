package com.bingbaihanji.fxdecomplie.util.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxEngineOptions;

/**
 * 将 {@link JadxEngineOptions} 映射到 {@link JadxArgs} setter 的桥接工具
 *
 * @author bingbaihanji
 */
public final class JadxOptionsBridge {

    private JadxOptionsBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * 将 JadxEngineOptions 中的值应用到指定的 JadxArgs 实例
     */
    public static void apply(JadxArgs args, JadxEngineOptions opts) {
        if (args == null || opts == null) {
            return;
        }
        args.setShowInconsistentCode(opts.showInconsistentCode());
        args.setUseImports(opts.useImports());
        args.setDebugInfo(opts.debugInfo());
        args.setExtractFinally(opts.extractFinally());
        args.setInlineAnonymousClasses(opts.inlineAnonymousClasses());
        args.setInlineMethods(opts.inlineMethods());
        args.setMoveInnerClasses(opts.moveInnerClasses());
        args.setAllowInlineKotlinLambda(opts.allowInlineKotlinLambda());
        args.setDeobfuscationOn(opts.deobfuscationOn());
        args.setDeobfuscationMinLength(opts.deobfuscationMinLength());
        args.setDeobfuscationMaxLength(opts.deobfuscationMaxLength());
        args.setSourceNameRepeatLimit(opts.sourceNameRepeatLimit());
        args.setRestoreSwitchOverString(opts.restoreSwitchOverString());
        args.setReplaceConsts(opts.replaceConsts());
        args.setEscapeUnicode(opts.escapeUnicode());
        args.setInsertDebugLines(opts.insertDebugLines());
        args.setRespectBytecodeAccModifiers(opts.respectBytecodeAccModifiers());
        args.setThreadsCount(opts.threadsCount());
        args.setTypeUpdatesLimitCount(opts.typeUpdatesLimitCount());
        args.setSkipResources(opts.skipResources());
        args.setSkipSources(opts.skipSources());
        args.setSkipXmlPrettyPrint(opts.skipXmlPrettyPrint());
    }
}
