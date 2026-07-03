package com.bingbaihanji.fxdecomplie.model;

/**
 * 搜索对话框中选定的搜索执行范围
 */
public enum SearchScope {
    ALL(true),
    CLASS(false),
    METHOD(true),
    CODE(true),
    RESOURCE(false),
    COMMENT(true),
    BYTECODE(false);

    private final boolean sourceCacheRelevant;

    SearchScope(boolean sourceCacheRelevant) {
        this.sourceCacheRelevant = sourceCacheRelevant;
    }

    /**
     * @return true 表示完整源码缓存可能改变此范围的搜索结果集
     */
    public boolean sourceCacheRelevant() {
        return sourceCacheRelevant;
    }
}
