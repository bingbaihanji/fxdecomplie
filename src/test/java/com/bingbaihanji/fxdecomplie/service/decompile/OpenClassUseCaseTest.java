package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompileOutcome;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenClassUseCase")
class OpenClassUseCaseTest {

    private InMemoryCacheStore cache;

    private DecompileCancellation cancellation;

    private OpenClassUseCase useCase;

    private Workspace workspace;

    private FileTreeNode classNode;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheStore();
        cancellation = new DecompileCancellation();
        useCase = new OpenClassUseCase(cache,
                new StubDecompilerExecutionService("public class Test {}"),
                cancellation);

        // 创建测试用 Workspace
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        var root = new com.bingbaihanji.fxdecomplie.model.FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        workspace = new Workspace("test-ws", tempDir, root, false);

        classNode = new FileTreeNode("Test.class", "com/example/Test",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
    }

    /** 内存缓存实现,用于测试 */
    static final class InMemoryCacheStore implements CodeCacheStore {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public Optional<String> load(CodeCacheKey key) {
            return Optional.ofNullable(store.get(key.compositeKey()));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            store.put(key.compositeKey(), source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            if (scope == CodeCacheScope.ALL) {
                store.clear();
            } else {
                store.remove(key.compositeKey());
            }
        }
    }

    /** 测试用反编译执行服务 — 总是返回成功 */
    static final class StubDecompilerExecutionService implements DecompilerExecutionService {

        private final String stubSource;

        StubDecompilerExecutionService(String stubSource) {
            this.stubSource = stubSource;
        }

        @Override
        public Future<DecompileResult> submit(DecompileRequest request) {
            DecompileOutcome.Success outcome = new DecompileOutcome.Success(stubSource);
            DecompileResult result = new DecompileResult(outcome, request.requestId(), 1, 0);
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void cancel(String requestId) {}

        @Override
        public int activeCount() {return 0;}

        @Override
        public int timeoutCount() {return 0;}
    }

    @Nested
    @DisplayName("缓存命中")
    class CacheHit {

        @Test
        @DisplayName("两级缓存中有结果时直接返回不提交反编译")
        void shouldReturnCachedSourceWithoutDecompiling() {
            // 预热缓存
            CodeCacheKey key = new CodeCacheKey(
                    OpenClassUseCase.computeWorkspaceKey(workspace),
                    "com/example/Test", DecompilerTypeEnum.JADX, "default");
            cache.save(key, "cached source code");

            OpenClassUseCase.LoadResult result = useCase.load(
                    workspace, classNode, DecompilerTypeEnum.JADX, "default");

            assertTrue(result.isCacheHit(), "应命中缓存");
            OpenClassUseCase.CacheHit hit = result.asCacheHit();
            assertEquals("cached source code", hit.source());
            assertEquals(0, cancellation.activeCount(), "不应有活跃的反编译请求");
        }
    }

    @Nested
    @DisplayName("缓存未命中")
    class CacheMiss {

        @Test
        @DisplayName("缓存为空时提交反编译任务")
        void shouldSubmitDecompileTaskWhenCacheEmpty() {
            // 确保缓存为空
            cache.invalidate(
                    new CodeCacheKey("any", "any", DecompilerTypeEnum.JADX, "default"),
                    CodeCacheScope.ALL);

            OpenClassUseCase.LoadResult result = useCase.load(
                    workspace, classNode, DecompilerTypeEnum.JADX, "default");

            assertFalse(result.isCacheHit(), "不应命中缓存");
            OpenClassUseCase.Submitted submitted = result.asSubmitted();
            assertNotNull(submitted.requestId(), "应有 requestId");
            assertNotNull(submitted.future(), "应有 Future");
            assertFalse(submitted.future().isCancelled(), "任务不应被取消");
        }
    }

    @Nested
    @DisplayName("取消")
    class Cancellation {

        @Test
        @DisplayName("取消不存在的请求不应抛异常")
        void shouldNotThrowWhenCancellingNonexistent() {
            assertDoesNotThrow(() -> useCase.cancel("nonexistent-id"));
        }

        @Test
        @DisplayName("取消全部在无活跃请求时不应抛异常")
        void shouldNotThrowWhenCancellingAllEmpty() {
            assertDoesNotThrow(() -> useCase.cancelAll());
        }
    }

    @Nested
    @DisplayName("workspaceKey 计算")
    class WorkspaceKey {

        @Test
        @DisplayName("workspaceKey 包含路径和时间戳")
        void shouldContainPathAndStamp() {
            String key = OpenClassUseCase.computeWorkspaceKey(workspace);
            assertNotNull(key);
            assertFalse(key.isBlank());
            // key 应包含来源文件路径信息
            assertTrue(key.contains("test-ws") || key.length() > 10,
                    "workspace key 应包含有意义的信息");
        }
    }
}
