package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.CommentExportDecorator;
import com.bingbaihanji.fxdecomplie.service.DecompilerOptions;
import com.bingbaihanji.fxdecomplie.service.DecompilerRunner;
import com.bingbaihanji.fxdecomplie.service.WorkspaceByteReader;
import com.bingbaihanji.fxdecomplie.service.cache.CacheKeys;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 类源码加载服务：读取字节、选择引擎、查询 L2/L3、反编译、应用重命名与注释。
 *
 * <p>UI 的 {@code ClassTabOpener} 与 MCP 的源码工具共用此服务，
 * 保证缓存键、引擎回退和展示语义一致。</p>
 *
 * @author bingbaihanji
 */
public final class ClassSourceService {

    private static final Logger log = LoggerFactory.getLogger(ClassSourceService.class);

    /** JD-Core 不支持的最低 class 主版本（Java 21） */
    private static final int MIN_UNSUPPORTED_JD_CLASS_VERSION = 65;

    private final AppConfig config;

    private final DecompileCacheService cache;

    public ClassSourceService(AppConfig config, DecompileCacheService cache) {
        this.config = Objects.requireNonNull(config, "config");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /** 命中缓存时直接返回；未命中时反编译并回填缓存。 */
    public ClassSourceResult load(ClassSourceRequest request) {
        return loadInternal(request, false);
    }

    /** 跳过 L2/L3 读取，反编译成功后回填缓存。 */
    public ClassSourceResult reload(ClassSourceRequest request) {
        return loadInternal(request, true);
    }

    private ClassSourceResult loadInternal(ClassSourceRequest request, boolean bypassCache) {
        Workspace workspace = request.workspace();
        if (workspace.isClosed()) {
            throw new IllegalStateException("工作区已关闭: " + workspace.getName());
        }
        byte[] classBytes = request.classBytes() != null
                ? request.classBytes() : readBytes(workspace, request.fullPath());
        if (classBytes == null || classBytes.length == 0) {
            throw new IllegalArgumentException("无法读取类字节: " + request.fullPath());
        }
        String internalName = ClassNameUtil.normalizeInternalName(request.fullPath());
        DecompilerTypeEnum engine = effectiveEngine(classBytes, request.engine());
        Map<String, String> options = mergedOptions(engine, request.optionOverrides());
        CodeCacheKey cacheKey = CacheKeys.forClass(workspace, classBytes, internalName,
                engine, options);
        if (bypassCache) {
            cache.invalidate(cacheKey, CodeCacheScope.CLASS);
        }
        String rawSource = bypassCache ? null : cache.load(cacheKey).orElse(null);
        boolean cacheHit = rawSource != null;
        if (cacheHit && !request.active().getAsBoolean()) {
            throw new java.util.concurrent.CancellationException("反编译请求已被替换");
        }
        if (rawSource == null) {
            if (!request.active().getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("反编译请求已被替换");
            }
            Thread.interrupted();
            DecompilerContext context = DecompilerRunner.contextForWorkspace(workspace, options);
            rawSource = DecompilerRunner.decompileWithTimeout(request.fullPath(), classBytes,
                    engine, context, request.active(), request.timeoutSeconds());
            if (!request.active().getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("反编译请求已被替换");
            }
            if (!DecompilerRunner.isFailureOutput(rawSource)) {
                cache.save(cacheKey, rawSource);
            }
        }
        boolean failure = DecompilerRunner.isFailureOutput(rawSource);
        String workspaceHash = CommentScope.workspaceHash(workspace);
        String source = RenameService.applyRenames(rawSource, workspaceHash, internalName);
        if (request.includeComments() && !failure) {
            CommentScope scope = new CommentScope(workspaceHash,
                    DecompilerOptions.hash(options));
            source = CommentExportDecorator.applyForClass(source, request.fullPath(), scope);
        }
        String displayName = RenameService.displayClassName(internalName, workspaceHash);
        log.debug("类源码加载完成: {} engine={}, cacheHit={}, failure={}",
                internalName, engine, cacheHit, failure);
        return new ClassSourceResult(source, engine, cacheHit, failure,
                displayName, workspaceHash, options);
    }

    private byte[] readBytes(Workspace workspace, String fullPath) {
        try {
            FileTreeNode node = workspace.findNodeByPath(fullPath);
            if (node != null) {
                return WorkspaceByteReader.readNodeBytes(workspace, node, true);
            }
            return WorkspaceByteReader.readClassBytes(workspace, fullPath, true);
        } catch (IOException e) {
            log.debug("读取类字节失败: {}", fullPath, e);
            return null;
        }
    }

    private Map<String, String> mergedOptions(DecompilerTypeEnum engine,
                                              Map<String, String> overrides) {
        Map<String, String> base = DecompilerOptions.forEngine(config, engine);
        if (overrides == null || overrides.isEmpty()) {
            return base;
        }
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return Map.copyOf(merged);
    }

    /**
     * 选择实际引擎；JD-Core 对 Java 21+ 的 class 回退到 Vineflower。
     *
     * @param classBytes    类字节
     * @param requestedEngine 请求引擎
     * @return 实际引擎
     */
    public static DecompilerTypeEnum effectiveEngine(byte[] classBytes,
                                                     DecompilerTypeEnum requestedEngine) {
        if (requestedEngine == null) {
            return DecompilerTypeEnum.VINEFLOWER;
        }
        if (requestedEngine != DecompilerTypeEnum.JD || classBytes == null) {
            return requestedEngine;
        }
        int major = classMajorVersion(classBytes);
        if (major >= MIN_UNSUPPORTED_JD_CLASS_VERSION) {
            return DecompilerTypeEnum.VINEFLOWER;
        }
        return requestedEngine;
    }

    private static int classMajorVersion(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return -1;
        }
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }
}
