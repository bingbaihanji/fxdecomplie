package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

/**
 * 代码视图上下文，聚合 CodeViewPanel 所需的所有数据，不依赖 MainWindow
 *
 * <p>通过 record 和 CodeActionHandler 解耦，主窗口、独立代码窗口、未来多窗口共享同一套代码视图</p>
 *
 * @param workspace      当前工作区
 * @param node           文件树节点
 * @param openFile       打开的文件元数据
 * @param classBytes     类文件原始字节码（构造时已 clone）
 * @param metadata       反编译元数据（行号→引用映射）
 * @param workspaceIndex 工作区全局索引（可能尚未构建完成）
 * @param workspaceHash  基于输入文件路径/mtime/size 的稳定标识，用于注释隔离
 * @param sourceHash     当前反编译源码文本 hash
 * @param optionsHash    反编译引擎+选项组合 hash，与缓存键口径一致
 * @author bingbaihanji
 * @date 2026-06-21
 */
public record CodeViewContext(
        Workspace workspace,
        FileTreeNode node,
        OpenFile openFile,
        byte[] classBytes,
        CodeMetadata metadata,
        WorkspaceIndex workspaceIndex,
        String workspaceHash,
        String sourceHash,
        String optionsHash
) {
    public CodeViewContext {
        if (classBytes != null) {
            classBytes = classBytes.clone();
        }
    }

    /** @return 类字节码副本，可能为 null */
    public byte[] classBytes() {
        return classBytes == null ? null : classBytes.clone();
    }

    /** @return 当前类的内部全路径名 */
    public String classInternalName() {
        return openFile != null ? openFile.fullPath() : null;
    }
}
