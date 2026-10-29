package com.bingbaihanji.fxdecomplie.service.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxProjectRenameSynchronizer;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;

import java.io.File;
import java.util.Map;

/**
 * 反编译缓存键工厂 — 全项目唯一的键计算入口.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheKeys {

    /** 反编译输出语义变化时递增,使旧缓存自然失效 */
    public static final int KEY_VERSION = 1;

    private CacheKeys() {
        throw new AssertionError("utility class");
    }

    /** 工作区身份 = 绝对路径 + 内容戳(mtime_size) */
    public static String workspaceId(Workspace workspace) {
        File source = workspace.getSourceFile();
        return source.getAbsolutePath() + "|" + workspace.getContentStamp();
    }

    /**
     * 类内容指纹 = SHA-256 前 16 位十六进制.
     *
     * <p>null 或空字节返回哨兵值 "0"(不是合法的 16 位十六进制指纹),用于表示类字节缺失;
     * {@link ByteUtils#sha256Hex(byte[])} 本身则拒绝 null.</p>
     */
    public static String classFingerprint(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "0";
        }
        return ByteUtils.sha256Hex(bytes).substring(0, 16);
    }

    /** 选项哈希;JADX 开启反混淆时追加重命名状态版本 */
    public static String optionsHash(DecompilerTypeEnum engine, Map<String, String> options) {
        Map<String, String> safeOptions = options == null ? Map.of() : options;
        String hash = DecompilerOptions.hash(safeOptions);
        if (engine == DecompilerTypeEnum.JADX
                && Boolean.parseBoolean(safeOptions.getOrDefault("deobfuscationOn", "false"))) {
            return hash + "_" + JadxProjectRenameSynchronizer.CACHE_STATE_VERSION;
        }
        return hash;
    }

    /** 构造类级缓存键 */
    public static CodeCacheKey forClass(Workspace workspace, byte[] classBytes,
                                        String internalName, DecompilerTypeEnum engine,
                                        Map<String, String> options) {
        return new CodeCacheKey(workspaceId(workspace), classFingerprint(classBytes),
                internalName, engine, optionsHash(engine, options), KEY_VERSION);
    }
}
