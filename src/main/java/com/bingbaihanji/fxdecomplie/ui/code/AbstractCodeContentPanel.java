package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代码内容面板抽象基类，统一四种视图（源码/Smali/字节码/简化）的生命周期
 *
 * <p>子类在后台线程中生成文本/数据，在 JavaFX 线程中创建实际节点，
 * 避免在后台线程构造 JavaFX 控件导致随机卡顿或崩溃。</p>
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
    /** 字体族（由 CodeContentDeck 在创建面板后设置） */
    protected volatile String fontFamily = "Consolas";
    /** 字号（由 CodeContentDeck 在创建面板后设置） */
    protected volatile int fontSize = 14;
    /** 行号开关（由 CodeContentDeck 在创建面板后设置） */
    protected volatile boolean lineNumbersEnabled = true;
    private volatile Object cancelToken;
    /** 内容创建完成回调（面板创建 CodeArea 后触发，用于应用字体/行号） */
    private volatile Runnable onContentCreated;

    /** 显示加载指示器 */
    protected AbstractCodeContentPanel() {
        showLoading();
    }

    /** 设置字体参数（CodeContentDeck 在 createPanel 后调用） */
    public void setFontSettings(String fontFamily, int fontSize, boolean lineNumbersEnabled) {
        this.fontFamily = fontFamily != null && !fontFamily.isBlank() ? fontFamily : "Consolas";
        this.fontSize = Math.clamp(fontSize, 8, 48);
        this.lineNumbersEnabled = lineNumbersEnabled;
    }

    /** 子类在 createContent() 中创建 CodeArea 后调用此方法应用字体和行号 */
    protected final void applyFontAndLineNumbers(jfx.incubator.scene.control.richtext.CodeArea area) {
        if (area == null) return;
        try {
            java.net.URL url = getClass().getResource("/ttf/FiraCode-Light.ttf");
            if (url != null) {
                area.setFont(javafx.scene.text.Font.loadFont(url.toExternalForm(), fontSize));
            } else if (fontFamily != null && !fontFamily.isBlank()) {
                area.setFont(javafx.scene.text.Font.font(fontFamily, fontSize));
            } else {
                area.setFont(javafx.scene.text.Font.font("Consolas", fontSize));
            }
        } catch (Exception ignored) {
            area.setFont(javafx.scene.text.Font.font("Consolas", fontSize));
        }
        LineNumberGutter.setEnabled(area, lineNumbersEnabled);
    }

    /** 设置内容创建完成的回调 */
    public void setOnContentCreated(Runnable callback) {
        this.onContentCreated = callback;
    }

    /** @return 当前面板的内容类型标识 */
    public abstract String getContentType();

    /**
     * 在后台线程中构建内容数据，不允许创建 JavaFX Node。
     *
     * @param cancelToken 取消令牌，构建过程中应周期性检查
     * @return 内容数据
     * @throws Exception 构建失败时抛出
     */
    protected abstract Object buildContentAsync(Object cancelToken) throws Exception;

    /**
     * 在 JavaFX 线程中将后台数据渲染为节点。
     *
     * @param contentData {@link #buildContentAsync(Object)} 的返回值
     * @return 渲染节点
     */
    protected abstract javafx.scene.Node createContent(Object contentData);

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
                Object contentData = buildContentAsync(token);
                if (isCancelled(token)) return;
                Platform.runLater(() -> {
                    if (!isCancelled(token) && state.get() == STATE_LOADING) {
                        getChildren().clear();
                        javafx.scene.Node content = createContent(contentData);
                        if (content != null) {
                            getChildren().add(content);
                        }
                        state.set(STATE_LOADED);
                        if (onContentCreated != null) {
                            onContentCreated.run();
                        }
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
