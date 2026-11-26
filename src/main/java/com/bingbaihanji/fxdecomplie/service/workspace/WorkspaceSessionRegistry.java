package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceLoader;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * UI 与 MCP 共享的工作区会话注册表.
 *
 * <p>工作区加载,活动切换,关闭都从这里经过;UI 通过回调同步标签页,
 * MCP 通过会话 ID 访问工作区.本类不依赖 JavaFX.</p>
 *
 * @author bingbaihanji
 */
public final class WorkspaceSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSessionRegistry.class);

    private final Map<String, WorkspaceSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<WorkspaceSession>> inFlight = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private volatile String activeId;

    private volatile Consumer<WorkspaceSession> onOpened = session -> {
    };

    private volatile Consumer<WorkspaceSession> onClosed = session -> {
    };

    private volatile Consumer<WorkspaceSession> onActiveChanged = session -> {
    };

    private volatile Consumer<com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum>
            onEngineChangeRequested = engine -> {
    };

    private volatile BiConsumer<ClassViewRequest, Consumer<Boolean>> onClassViewRequested =
            (request, completion) -> completion.accept(false);

    public void setOnOpened(Consumer<WorkspaceSession> listener) {
        onOpened = listener == null ? session -> {
        } : listener;
    }

    public void setOnClosed(Consumer<WorkspaceSession> listener) {
        onClosed = listener == null ? session -> {
        } : listener;
    }

    public void setOnActiveChanged(Consumer<WorkspaceSession> listener) {
        onActiveChanged = listener == null ? session -> {
        } : listener;
    }

    public void setOnEngineChangeRequested(
            Consumer<com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum> listener) {
        onEngineChangeRequested = listener == null ? engine -> {
        } : listener;
    }

    /**
     * 注册 GUI 的类视图打开回调.回调在调用线程执行,UI 实现需自行切回 JavaFX 线程;
     * completion 必须且只能调用一次.
     */
    public void setOnClassViewRequested(
            BiConsumer<ClassViewRequest, Consumer<Boolean>> listener) {
        onClassViewRequested = listener == null
                ? (request, completion) -> completion.accept(false) : listener;
    }

    /** MCP 请求切换引擎时调用;由 UI 回调执行实际切换. */
    public void requestEngine(com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum engine) {
        if (engine != null) {
            onEngineChangeRequested.accept(engine);
        }
    }

    /**
     * 请求 GUI 打开类视图;返回是否成功入队/打开.
     *
     * @param request 类视图请求
     * @return 异步结果
     */
    public CompletableFuture<Boolean> requestClassView(ClassViewRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Consumer<Boolean> completion = opened -> {
            if (!result.isDone()) {
                result.complete(Boolean.TRUE.equals(opened));
            }
        };
        try {
            onClassViewRequested.accept(request, completion);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /**
     * 按路径打开工作区;相同路径且相同内容戳会复用已有会话.
     *
     * @param path 本地 JAR/ZIP/目录/class 路径
     * @return 异步会话结果
     */
    public CompletableFuture<WorkspaceSession> open(Path path) {
        Objects.requireNonNull(path, "path");
        String normalized = path.toAbsolutePath().normalize().toString();
        CompletableFuture<WorkspaceSession> existing = inFlight.get(normalized);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<WorkspaceSession> result = new CompletableFuture<>();
        CompletableFuture<WorkspaceSession> raced = inFlight.putIfAbsent(normalized, result);
        if (raced != null) {
            return raced;
        }
        BackgroundTasks.run(BackgroundTasks.PoolType.IO, "WorkspaceOpen-" + path.getFileName(),
                () -> {
                    try {
                        Workspace workspace = WorkspaceLoader.load(new File(normalized));
                        try {
                            result.complete(register(workspace));
                        } catch (RuntimeException e) {
                            workspace.close();
                            throw e;
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    } finally {
                        inFlight.remove(normalized);
                    }
                },
                rejected -> {
                    inFlight.remove(normalized);
                    result.completeExceptionally(
                            new IllegalStateException("工作区加载队列已满", rejected));
                });
        return result;
    }

    private WorkspaceSession register(Workspace workspace) {
        String id = idFor(workspace);
        WorkspaceSession session;
        synchronized (lock) {
            WorkspaceSession existing = sessions.get(id);
            if (existing != null) {
                workspace.close();
                markActive(existing.workspace());
                return existing;
            }
            session = new WorkspaceSession(id, workspace, Instant.now(), true);
            sessions.put(id, session);
            activeId = id;
        }
        onOpened.accept(session);
        onActiveChanged.accept(session);
        log.info("工作区会话已注册: {} ({})", workspace.getName(), id);
        return session;
    }

    private String idFor(Workspace workspace) {
        String identity = workspace.getSourceFile().getAbsolutePath()
                + "|" + workspace.getContentStamp();
        return ByteUtils.sha256Hex(identity).substring(0, 12);
    }

    /**
     * UI 标签页选中时调用;欢迎页等临时空选中传 null 不会清空活动工作区,
     * 工作区关闭由 {@link #unregister(Workspace)} 清理.
     *
     * @param workspace 当前活动工作区
     */
    public void markActive(Workspace workspace) {
        if (workspace == null) {
            // 欢迎页/临时空选中不应清空真正的活动工作区;关闭工作区由 unregister 清理.
            return;
        }
        WorkspaceSession target = findByWorkspace(workspace);
        if (target == null) {
            return;
        }
        synchronized (lock) {
            if (activeId != null && !activeId.equals(target.id())) {
                WorkspaceSession previous = sessions.get(activeId);
                if (previous != null) {
                    sessions.put(activeId, new WorkspaceSession(previous.id(), previous.workspace(),
                            previous.openedAt(), false));
                }
            }
            WorkspaceSession activated = new WorkspaceSession(target.id(), target.workspace(),
                    target.openedAt(), true);
            sessions.put(target.id(), activated);
            activeId = target.id();
            onActiveChanged.accept(activated);
        }
    }

    /**
     * 工作区关闭时移除会话.
     *
     * @param workspace 已关闭的工作区
     */
    public void unregister(Workspace workspace) {
        if (workspace == null) {
            return;
        }
        WorkspaceSession removed;
        synchronized (lock) {
            WorkspaceSession target = findByWorkspace(workspace);
            if (target == null) {
                return;
            }
            removed = sessions.remove(target.id());
            if (removed != null && removed.id().equals(activeId)) {
                activeId = null;
            }
        }
        if (removed != null) {
            onClosed.accept(removed);
            log.info("工作区会话已关闭: {} ({})", workspace.getName(), removed.id());
        }
    }

    public List<WorkspaceSession> sessions() {
        synchronized (lock) {
            return List.copyOf(new ArrayList<>(sessions.values()));
        }
    }

    public Optional<WorkspaceSession> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sessions.get(id));
    }

    public Optional<WorkspaceSession> active() {
        String id = activeId;
        return id == null ? Optional.empty() : find(id);
    }

    private WorkspaceSession findByWorkspace(Workspace workspace) {
        for (WorkspaceSession session : sessions.values()) {
            if (session.workspace() == workspace) {
                return session;
            }
        }
        return null;
    }
}
