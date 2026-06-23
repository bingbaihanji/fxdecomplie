package com.bingbaihanji.fxdecomplie.model;

/**
 * 注释导出作用域。
 *
 * @param workspaceHash 当前工作区标识
 * @param optionsHash   当前反编译选项标识
 */
public record CommentScope(String workspaceHash, String optionsHash) {
    public CommentScope {
        workspaceHash = workspaceHash == null ? "" : workspaceHash;
        optionsHash = optionsHash == null || optionsHash.isBlank() ? "default" : optionsHash;
    }

    public static CommentScope of(Workspace workspace, String optionsHash) {
        return new CommentScope(workspaceHash(workspace), optionsHash);
    }

    public static String workspaceHash(Workspace workspace) {
        if (workspace == null || workspace.getSourceFile() == null) {
            return "";
        }
        java.io.File source = workspace.getSourceFile();
        return source.getAbsolutePath().replace('\\', '/') + "@"
                + source.lastModified() + "@" + source.length();
    }

    public boolean enabled() {
        return !workspaceHash.isBlank();
    }
}
