package com.bingbaihanji.fxdecomplie.ui.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 代码标签页工厂 — 管理标签页的创建,查找和状态绑定.
 *
 * <p>维护 fullPath → CodeEditorTab 的映射,确保同一个类不会被重复打开.
 * 实际的标签页创建委托给传入的工厂函数.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class CodeTabFactory {

    private final Map<String, CodeEditorTab> pathToTab = new ConcurrentHashMap<>();

    private final Map<CodeEditorTab, CodeTabState> tabStates = new ConcurrentHashMap<>();

    /**
     * 查找已有标签页,或使用 factory 创建新标签页.
     *
     * @param fullPath 类的完整路径(用作去重 key)
     * @param factory  创建新 CodeEditorTab 的工厂函数(仅在未找到时调用)
     * @return TabResult — isNew 为 true 表示新创建的标签页
     */
    public TabResult findOrCreate(String fullPath,
                                  Function<String, CodeEditorTab> factory) {
        CodeEditorTab existing = pathToTab.get(fullPath);
        if (existing != null) {
            CodeTabState state = tabStates.get(existing);
            return new TabResult(existing, state, false);
        }
        CodeEditorTab newTab = factory.apply(fullPath);
        CodeTabState state = new CodeTabState();
        pathToTab.put(fullPath, newTab);
        tabStates.put(newTab, state);
        return new TabResult(newTab, state, true);
    }

    /** 按 fullPath 查找已有标签页 */
    public CodeEditorTab findByPath(String fullPath) {
        return pathToTab.get(fullPath);
    }

    /** 获取标签页对应的状态 */
    public CodeTabState stateOf(CodeEditorTab tab) {
        return tabStates.get(tab);
    }

    /** 移除标签页及其关联状态 */
    public void remove(CodeEditorTab tab, String fullPath) {
        tabStates.remove(tab);
        if (fullPath != null) {
            pathToTab.remove(fullPath);
        }
    }

    /** 当前打开的标签页数量 */
    public int size() {
        return pathToTab.size();
    }

    /** 清空所有映射(工作区关闭时调用) */
    public void clear() {
        tabStates.clear();
        pathToTab.clear();
    }

    /**
     * 标签页查找/创建结果
     */
    public record TabResult(CodeEditorTab tab, CodeTabState state, boolean isNew) {}
}
