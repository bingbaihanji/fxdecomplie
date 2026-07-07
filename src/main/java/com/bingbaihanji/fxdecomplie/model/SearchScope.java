package com.bingbaihanji.fxdecomplie.model;

/**
 * 搜索对话框中选定的搜索执行范围
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public enum SearchScope {
    /** 搜索所有类别 */
    ALL(true),
    /** 按类名搜索 */
    CLASS(false),
    /** 按方法签名搜索 */
    METHOD(true),
    /** 全文搜索源码 */
    CODE(true),
    /** 按资源文件名搜索 */
    RESOURCE(false),
    /** 按注释文本搜索 */
    COMMENT(true),
    /** 按字节码文本搜索 */
    BYTECODE(false);

    private final boolean sourceCacheRelevant;

    SearchScope(boolean sourceCacheRelevant) {
        this.sourceCacheRelevant = sourceCacheRelevant;
    }

    /**
     * 判断该搜索范围是否与源码缓存相关
     *
     * @return true 表示完整源码缓存可能改变此范围的搜索结果集
     */
    public boolean sourceCacheRelevant() {
        return sourceCacheRelevant;
    }
}
