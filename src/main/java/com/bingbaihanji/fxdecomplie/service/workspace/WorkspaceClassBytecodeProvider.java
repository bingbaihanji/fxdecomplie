package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.WorkspaceByteReader;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于工作区文件树的懒加载字节码提供器.
 *
 * <p>导出等场景只需要"内部类名 → 字节码",不必等待完整工作区索引.
 * 解析顺序:文件树节点优先;非归档工作区允许磁盘回退.
 * 归档工作区在发现阶段已枚举全部条目,节点缺失即视为未命中,
 * 避免每个未命中的类都打开一次 ZipFile.</p>
 *
 * <p>线程安全:可被多个导出工作线程共享;未命中结果在提供器生命周期内永久缓存.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-28
 */
public final class WorkspaceClassBytecodeProvider implements ClassBytecodeProvider {

    private static final Logger log =
            LoggerFactory.getLogger(WorkspaceClassBytecodeProvider.class);

    private final Workspace workspace;

    /** 已确认不存在的类,避免重复解析 */
    private final Set<String> missingClasses = ConcurrentHashMap.newKeySet();

    public WorkspaceClassBytecodeProvider(Workspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    @Override
    public byte[] getClassBytes(String internalName) {
        String normalized = ClassNameUtil.normalizeInternalName(internalName);
        if (normalized.isBlank() || missingClasses.contains(normalized)) {
            return null;
        }
        FileTreeNode node = workspace.findNodeByPath(normalized + ".class");
        if (node != null) {
            byte[] bytes = readNodeBytes(normalized, node);
            if (bytes != null) {
                return bytes;
            }
            missingClasses.add(normalized);
            return null;
        }
        if (!workspace.isArchive()) {
            byte[] bytes = readClassBytes(normalized);
            if (bytes != null) {
                return bytes;
            }
        }
        missingClasses.add(normalized);
        return null;
    }

    private byte[] readNodeBytes(String normalized, FileTreeNode node) {
        try {
            return WorkspaceByteReader.readNodeBytes(workspace, node, true);
        } catch (IOException e) {
            log.debug("懒加载类字节失败: {}", normalized, e);
            return null;
        }
    }

    private byte[] readClassBytes(String normalized) {
        try {
            return WorkspaceByteReader.readClassBytes(workspace, normalized, true);
        } catch (IOException e) {
            log.debug("懒加载类字节失败: {}", normalized, e);
            return null;
        }
    }
}
