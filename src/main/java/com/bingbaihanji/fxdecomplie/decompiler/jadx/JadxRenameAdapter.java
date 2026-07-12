package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 桥接项目 RenameService 的持久重命名与 jadx 内核 alias
 * <p>
 * 反编译完成后对源码应用项目级重命名，确保源码展示和导出路径与 RenameService 一致
 * 无 rename 时为空适配器（no-op）
 *
 * @author bingbaihanji
 */
public final class JadxRenameAdapter {

    /** 空适配器：无项目 rename 时使用 */
    public static final JadxRenameAdapter EMPTY = new JadxRenameAdapter(Map.of());
    private final Map<String, String> classRenameMap; // raw internal name -> project rename

    private JadxRenameAdapter(Map<String, String> classRenameMap) {
        this.classRenameMap = Map.copyOf(classRenameMap);
    }

    /** 从 rawName→rename 映射构建适配器 */
    public static JadxRenameAdapter of(Map<String, String> renameMap) {
        if (renameMap == null || renameMap.isEmpty()) {
            return EMPTY;
        }
        return new JadxRenameAdapter(renameMap);
    }

    /**
     * 对反编译后的源码应用项目重命名
     * 替换所有出现在源码中的 internal name 的 Java 表示形式
     */
    public String applyRenames(String source) {
        if (classRenameMap.isEmpty() || source == null || source.isBlank()) {
            return source;
        }
        String result = source;
        for (var entry : classRenameMap.entrySet()) {
            String rawJavaName = entry.getKey().replace('/', '.');
            String renamed = entry.getValue();
            if (!rawJavaName.equals(renamed)) {
                result = result.replace(rawJavaName, renamed);
            }
        }
        return result;
    }

    /** rename 快照 hash，用于缓存键无 rename 时返回固定值 */
    public String snapshotHash() {
        if (classRenameMap.isEmpty()) {
            return "no-rename";
        }
        return ByteUtils.sha256Hex(classRenameMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",")));
    }
}
