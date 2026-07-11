package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.util.jadx.JadxOptionsBridge;

import java.util.Map;

/**
 * 创建项目默认的 jadx 参数并应用用户配置 
 */
public final class JadxArgsFactory {

    private JadxArgsFactory() {
        throw new AssertionError("utility class");
    }

    public static JadxArgs create(Map<String, String> engineOptions) {
        JadxArgs args = new JadxArgs();
        args.setSkipResources(true);
        args.setDebugInfo(true);
        args.setUseImports(true);
        args.setInlineMethods(true);
        args.setInlineAnonymousClasses(true);
        args.setExtractFinally(true);
        args.setDeobfuscationOn(false);
        args.setShowInconsistentCode(true);
        JadxOptionsBridge.apply(args, engineOptions);
        return args;
    }
}
