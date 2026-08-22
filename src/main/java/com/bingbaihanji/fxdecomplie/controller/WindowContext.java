package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.controller.command.WindowCommandBus;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;

/**
 * 窗口级上下文 — 状态和命令的单一来源.
 *
 * <p>替代子控制器通过 {@code MainWindow} 包级访问器读写共享状态的模式.
 * 提供可观察属性(绑定工具栏/状态栏自动刷新)和命令总线(菜单/工具栏/快捷键统一派发).</p>
 *
 * <p>使用方式:
 * <ol>
 *   <li>{@code MainWindow} 创建 WindowContext</li>
 *   <li>子控制器构造参数从 MainWindow 逐步改为 WindowContext + 需要的 Service</li>
 *   <li>UI 命令通过 {@code commandBus.register/dispatch} 统一管理</li>
 * </ol>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class WindowContext {

    private final Stage stage;

    private final WindowCommandBus commandBus;

    // ─── 可观察状态 ───

    /** 当前打开的工作区 */
    private final ObjectProperty<Workspace> workspaceSelection =
            new SimpleObjectProperty<>();

    /** 当前选中的反编译引擎 */
    private final ObjectProperty<DecompilerTypeEnum> engineSelection =
            new SimpleObjectProperty<>(DecompilerTypeEnum.JADX);

    // ─── 构造 ───

    public WindowContext(Stage stage) {
        this.stage = stage;
        this.commandBus = new WindowCommandBus();
    }

    // ─── Stage ───

    public Stage stage() {
        return stage;
    }

    // ─── 命令总线 ───

    public WindowCommandBus commandBus() {
        return commandBus;
    }

    // ─── 工作区状态 ───

    public ObjectProperty<Workspace> workspaceProperty() {
        return workspaceSelection;
    }

    public Workspace currentWorkspace() {
        return workspaceSelection.get();
    }

    public void setCurrentWorkspace(Workspace ws) {
        workspaceSelection.set(ws);
    }

    // ─── 引擎状态 ───

    public ObjectProperty<DecompilerTypeEnum> engineProperty() {
        return engineSelection;
    }

    public DecompilerTypeEnum currentEngine() {
        return engineSelection.get();
    }

    public void setCurrentEngine(DecompilerTypeEnum engine) {
        engineSelection.set(engine);
    }
}
