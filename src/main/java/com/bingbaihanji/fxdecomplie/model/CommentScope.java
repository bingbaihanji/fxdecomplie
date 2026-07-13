package com.bingbaihanji.fxdecomplie.model;

/**
 * 注释导出作用域,用于标识注释所属的工作区和反编译选项上下文
 *
 * @param workspaceHash 当前工作区标识
 * @param optionsHash   当前反编译选项标识
 * @author bingbaihanji
 * @date 2026-06-21
 */
public record CommentScope(String workspaceHash, String optionsHash) {
    public CommentScope {
        workspaceHash = workspaceHash == null ? "" : workspaceHash;
        optionsHash = optionsHash == null || optionsHash.isBlank() ? "default" : optionsHash;
    }

    /**
     * 根据工作区和选项哈希创建注释作用域
     *
     * @param workspace   当前工作区
     * @param optionsHash 反编译选项哈希
     * @return 新的 CommentScope 实例
     */
    public static CommentScope of(Workspace workspace, String optionsHash) {
        return new CommentScope(workspaceHash(workspace), optionsHash);
    }

    /**
     * 计算工作区的唯一哈希标识
     * 基于源文件的绝对路径 最后修改时间和文件大小生成
     *
     * @param workspace 当前工作区
     * @return 工作区哈希字符串,workspace 为 null 或源文件不存在时返回空字符串
     */
    public static String workspaceHash(Workspace workspace) {
        if (workspace == null || workspace.getSourceFile() == null) {
            return "";
        }
        java.io.File source = workspace.getSourceFile();
        return source.getAbsolutePath().replace('\\', '/') + "@"
                + source.lastModified() + "@" + source.length();
    }

    /**
     * 判断当前注释作用域是否有效
     *
     * @return workspaceHash 非空时返回 true
     */
    public boolean enabled() {
        return !workspaceHash.isBlank();
    }
}
