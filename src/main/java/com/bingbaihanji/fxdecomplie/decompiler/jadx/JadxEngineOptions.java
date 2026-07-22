package com.bingbaihanji.fxdecomplie.decompiler.jadx;

/**
 * jadx 引擎不可变配置
 * <p>
 * 只包含用户可配置的选项，不包含内核内部字段(如 codeCache pluginLoader 等)
 * 通过 {@link JadxOptionSchema#fromOptionsMap} 从 Map 构建实例
 *
 * @author bingbaihanji
 */
public record JadxEngineOptions(
        // 代码生成
        boolean useImports,
        boolean debugInfo,
        boolean showInconsistentCode,
        boolean extractFinally,
        // 内联
        boolean inlineMethods,
        boolean inlineAnonymousClasses,
        boolean moveInnerClasses,
        boolean allowInlineKotlinLambda,
        // 去混淆
        boolean deobfuscationOn,
        int deobfuscationMinLength,
        int deobfuscationMaxLength,
        int sourceNameRepeatLimit,
        // 常量与开关
        boolean restoreSwitchOverString,
        boolean replaceConsts,
        boolean escapeUnicode,
        boolean insertDebugLines,
        boolean respectBytecodeAccModifiers,
        // 线程
        int threadsCount,
        int typeUpdatesLimitCount,
        // 资源
        boolean skipResources,
        boolean skipSources,
        boolean skipXmlPrettyPrint
) {
    public static final JadxEngineOptions DEFAULTS = new JadxEngineOptions(
            true,  // useImports
            true,  // debugInfo
            true,  // showInconsistentCode
            true,  // extractFinally
            true,  // inlineMethods
            true,  // inlineAnonymousClasses
            true,  // moveInnerClasses
            true,  // allowInlineKotlinLambda
            false, // deobfuscationOn
            0,     // deobfuscationMinLength
            Integer.MAX_VALUE, // deobfuscationMaxLength
            10,    // sourceNameRepeatLimit
            true,  // restoreSwitchOverString
            true,  // replaceConsts
            false, // escapeUnicode
            false, // insertDebugLines
            false, // respectBytecodeAccModifiers
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2), // threadsCount
            10,    // typeUpdatesLimitCount
            true,  // skipResources
            false, // skipSources
            false  // skipXmlPrettyPrint
    );
}
