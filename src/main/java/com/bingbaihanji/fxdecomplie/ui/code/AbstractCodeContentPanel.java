package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代码内容面板抽象基类，统一四种视图（源码/Smali/字节码/简化）的生命周期
 *
 * <p>子类只需实现 {@link #buildContentAsync()} 生成内容并返回 Node，
 * 加载状态和错误处理由基类统一管理</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public abstract class AbstractCodeContentPanel extends StackPane {

    /** 未加载标记 */
    private static final int STATE_UNLOADED = 0;
    /** 加载中 */
    private static final int STATE_LOADING = 1;
    /** 已加载 */
    private static final int STATE_LOADED = 2;
    /** 加载失败 */
    private static final int STATE_ERROR = 3;

    private final AtomicInteger state = new AtomicInteger(STATE_UNLOADED);
    private volatile Object cancelToken;

    /** 显示加载指示器 */
    protected AbstractCodeContentPanel() {
        showLoading();
    }

    /** @return 当前面板的内容类型标识 */
    public abstract String getContentType();

    /**
     * 在后台线程中构建内容
     *
     * @param cancelToken 取消令牌，构建过程中应周期性检查
     * @return 渲染后的内容节点
     * @throws Exception 构建失败时抛出
     */
    protected abstract Node buildContentAsync(Object cancelToken) throws Exception;

    /** @return 是否已完成加载 */
    public boolean isLoaded() {
        return state.get() == STATE_LOADED;
    }

    /** @return 是否加载失败 */
    public boolean isError() {
        return state.get() == STATE_ERROR;
    }

    /**
     * 触发异步加载（首次调用生效，后续调用忽略）
     *
     * @param token 取消令牌，用于快速切换时丢弃过期结果
     */
    public final void loadAsync(Object token) {
        if (!state.compareAndSet(STATE_UNLOADED, STATE_LOADING)) {
            return;
        }
        this.cancelToken = token;
        Thread.startVirtualThread(() -> {
            try {
                if (isCancelled(token)) return;
                Node content = buildContentAsync(token);
                if (isCancelled(token)) return;
                Platform.runLater(() -> {
                    if (!isCancelled(token) && state.get() == STATE_LOADING) {
                        getChildren().clear();
                        if (content != null) {
                            getChildren().add(content);
                        }
                        state.set(STATE_LOADED);
                    }
                });
            } catch (Exception e) {
                if (isCancelled(token)) return;
                Platform.runLater(() -> {
                    if (!isCancelled(token) && state.get() == STATE_LOADING) {
                        showError("加载失败: " + e.getMessage());
                        state.set(STATE_ERROR);
                    }
                });
            }
        });
    }

    /** 释放资源，子类可重写以清理 CodeArea 监听器等 */
    public void dispose() {
        cancelToken = new Object();
    }

    /** 加载中占位 */
    protected void showLoading() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(32, 32);
        getChildren().setAll(indicator);
    }

    /** 错误提示 */
    protected void showError(String msg) {
        getChildren().setAll(new Label(msg));
    }

    private boolean isCancelled(Object token) {
        return cancelToken != token;
    }
}
