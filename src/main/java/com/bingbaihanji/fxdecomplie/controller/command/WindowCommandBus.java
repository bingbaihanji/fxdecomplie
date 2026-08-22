package com.bingbaihanji.fxdecomplie.controller.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 窗口级命令总线 — 集中注册和派发 {@link WindowCommand}.
 *
 * <p>菜单栏 Action,工具栏按钮,键盘快捷键通过同一命令名派发,
 * 确保行为一致,无重复代码.命令在窗口初始化时注册.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   commandBus.register("file.open", () -> openFile());
 *   commandBus.register("engine.switch", () -> switchEngine());
 *
 *   // 菜单项点击:
 *   menuItem.setOnAction(e -> commandBus.dispatch("file.open"));
 *
 *   // 快捷键:
 *   keyEvent -> commandBus.dispatch("file.open");
 * }</pre>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class WindowCommandBus {

    private static final Logger log = LoggerFactory.getLogger(WindowCommandBus.class);

    private final Map<String, WindowCommand<?>> commands = new ConcurrentHashMap<>();

    /**
     * 派发命令.命令未注册时记录警告并返回 null.
     *
     * @param commandName 命令名
     * @param <T>         返回值类型
     * @return 命令返回值,未注册时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T dispatch(String commandName) {
        WindowCommand<?> cmd = commands.get(commandName);
        if (cmd == null) {
            log.warn("未注册的命令: {}", commandName);
            return null;
        }
        try {
            return (T) cmd.execute();
        } catch (Exception e) {
            log.error("命令执行失败: {}", commandName, e);
            throw e;
        }
    }

    /** 注册命令(同一名称重复注册会覆盖) */
    public void register(String name, WindowCommand<?> command) {
        commands.put(name, command);
        log.debug("注册命令: {}", name);
    }

    /** 注销命令 */
    public void unregister(String name) {
        commands.remove(name);
    }

    /** 是否存在指定命令 */
    public boolean hasCommand(String name) {
        return commands.containsKey(name);
    }

    /** 已注册命令数 */
    public int size() {
        return commands.size();
    }
}
