package com.bingbaihanji.fxdecomplie.controller.command;

/**
 * UI 命令抽象 — 菜单项、工具栏按钮、快捷键绑定复用同一命令实例。
 *
 * <p>命令通过 {@link WindowCommandBus} 注册和派发，避免分散在各控制器中的重复业务逻辑。</p>
 *
 * @param <T> 命令返回值类型
 * @author bingbaihanji
 * @date 2026-07-26
 */
@FunctionalInterface
public interface WindowCommand<T> {
    T execute();
}
