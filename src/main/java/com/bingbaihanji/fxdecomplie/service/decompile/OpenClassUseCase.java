package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * 打开 class 文件的业务用例 — 纯数据流,不依赖 JavaFX.
 *
 * <p>编排缓存查询 → 反编译提交的完整流程,返回 {@link LoadResult}
 * 供 UI 层({@code ClassTabOpener})消费.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class OpenClassUseCase {

    private static final Logger log = LoggerFactory.getLogger(OpenClassUseCase.class);

    private final CodeCacheStore cacheStore;

    private final DecompilerExecutionService decompilerExecution;

    private final DecompileCancellation cancellation;

    public OpenClassUseCase(CodeCacheStore cacheStore,
                            DecompilerExecutionService decompilerExecution,
                            DecompileCancellation cancellation) {
        this.cacheStore = cacheStore;
        this.decompilerExecution = decompilerExecution;
        this.cancellation = cancellation;
    }

    static String computeWorkspaceKey(Workspace workspace) {
        File source = workspace.getSourceFile();
        return (source.getAbsolutePath() + "_" + workspace.getContentStamp())
                .replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    /**
     * 加载类的反编译源码.
     *
     * @param workspace   当前工作区
     * @param node        要打开的类节点
     * @param engine      首选反编译引擎
     * @param optionsHash 反编译选项哈希
     * @return LoadResult — 要么缓存命中,要么已提交反编译
     */
    public LoadResult load(Workspace workspace, FileTreeNode node,
                           DecompilerTypeEnum engine, String optionsHash) {
        String internalName = node.getFullPath();
        String wsKey = computeWorkspaceKey(workspace);
        String requestId = UUID.randomUUID().toString();

        CodeCacheKey cacheKey = new CodeCacheKey(wsKey, internalName, engine, optionsHash);

        // 1. 查两级缓存
        Optional<String> cached = cacheStore.load(cacheKey);
        if (cached.isPresent()) {
            log.debug("缓存命中: {}", internalName);
            return LoadResult.cacheHit(requestId, cached.get());
        }

        // 2. 提交反编译任务
        DecompileRequest request = DecompileRequest.builder()
                .workspace(workspace)
                .classNode(node)
                .preferredEngine(engine)
                .optionsHash(optionsHash)
                .requestId(requestId)
                .build();

        Future<DecompileResult> future = decompilerExecution.submit(request);
        cancellation.register(requestId, future);

        return LoadResult.submitted(requestId, future, cacheKey);
    }

    /** 取消指定请求 */
    public void cancel(String requestId) {
        cancellation.cancel(requestId);
    }

    /** 取消所有活跃请求 */
    public void cancelAll() {
        cancellation.cancelAll();
    }

    // ─── 工作区缓存键 ───

    /** 获取缓存存储(供 UI 层在结果返回后写入缓存) */
    public CodeCacheStore cacheStore() {
        return cacheStore;
    }

    // ─── 结果类型 ───

    /** LoadResult 的内部状态 */
    public sealed interface LoadStatus {}

    public record CacheHit(String requestId, String source) implements LoadStatus {}

    public record Submitted(String requestId, Future<DecompileResult> future,
                            CodeCacheKey cacheKey) implements LoadStatus {}

    /** 加载结果 */
    public record LoadResult(LoadStatus status) {

        public static LoadResult cacheHit(String requestId, String source) {
            return new LoadResult(new CacheHit(requestId, source));
        }

        public static LoadResult submitted(String requestId, Future<DecompileResult> future,
                                           CodeCacheKey cacheKey) {
            return new LoadResult(new Submitted(requestId, future, cacheKey));
        }

        public boolean isCacheHit() {return status instanceof CacheHit;}

        public CacheHit asCacheHit() {return (CacheHit) status;}

        public Submitted asSubmitted() {return (Submitted) status;}
    }
}
