package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.IJadxEvent;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.JadxEventType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.JadxEvents;

/**
 * 重新加载项目事件。
 * <p>
 * 该事件为无状态的单例事件，通过 {@link #EVENT} 常量获取实例，
 * 用于通知监听者需要重新加载当前项目。
 */
public class ReloadProject implements IJadxEvent {

    /** 全局唯一的重新加载项目事件实例 */
    public static final ReloadProject EVENT = new ReloadProject();

    private ReloadProject() {
        // 单例
    }

    /**
     * 获取事件类型。
     *
     * @return 重新加载项目事件的类型 {@link JadxEvents#RELOAD_PROJECT}
     */
    @Override
    public JadxEventType<ReloadProject> getType() {
        return JadxEvents.RELOAD_PROJECT;
    }

    @Override
    public String toString() {
        return "RELOAD_PROJECT";
    }
}
