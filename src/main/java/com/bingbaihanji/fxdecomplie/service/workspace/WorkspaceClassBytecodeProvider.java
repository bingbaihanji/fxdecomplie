package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
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
 * 解析顺序由 {@link WorkspaceByteReader} 保证:文件树节点 → 归档条目 → 磁盘文件.
 * 未命中结果会被记住,避免批量导出时重复查找不存在的依赖类.</p>
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
        try {
            byte[] bytes = WorkspaceByteReader.readClassBytes(workspace, normalized, true);
            if (bytes == null) {
                missingClasses.add(normalized);
            }
            return bytes;
        } catch (IOException e) {
            log.debug("懒加载类字节失败: {}", normalized, e);
            missingClasses.add(normalized);
            return null;
        }
    }
}
