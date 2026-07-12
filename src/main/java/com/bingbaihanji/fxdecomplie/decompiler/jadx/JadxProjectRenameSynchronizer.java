package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 jadx 内核运行时产生的类 alias 同步到项目级 RenameService。
 */
public final class JadxProjectRenameSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(JadxProjectRenameSynchronizer.class);
    public static final String CACHE_STATE_VERSION = "project-rename-sync-v1";

    private JadxProjectRenameSynchronizer() {
        throw new AssertionError("utility class");
    }

    public static int syncDeobfAliases(String workspaceHash, List<JavaClass> classes) {
        if (workspaceHash == null || workspaceHash.isBlank() || classes == null || classes.isEmpty()) {
            return 0;
        }
        List<RenameEntry> entries = new ArrayList<>();
        for (JavaClass cls : classes) {
            collectClassAlias(entries, cls);
        }
        if (entries.isEmpty()) {
            return 0;
        }
        int saved = RenameService.saveAll(workspaceHash, entries, true);
        if (saved > 0) {
            log.debug("同步 jadx 去混淆类名到项目重命名状态: {} / {}", saved, entries.size());
        }
        return saved;
    }

    private static void collectClassAlias(List<RenameEntry> entries, JavaClass cls) {
        if (cls == null) {
            return;
        }
        String rawName = ClassNameUtil.normalizeInternalName(cls.getRawName());
        String aliasName = ClassNameUtil.normalizeInternalName(cls.getFullName());
        if (isMeaningfulClassAlias(rawName, aliasName)) {
            String rawLeaf = ClassNameUtil.simpleName(rawName);
            String aliasLeaf = ClassNameUtil.simpleName(aliasName);
            if (!rawLeaf.isBlank() && !aliasLeaf.isBlank()) {
                entries.add(new RenameEntry(RenameService.TYPE_CLASS,
                        rawName, rawLeaf, aliasLeaf, ""));
            }
        }
    }

    private static boolean isMeaningfulClassAlias(String rawName, String aliasName) {
        if (rawName.isBlank() || aliasName.isBlank()
                || ClassNameUtil.sameInternalName(rawName, aliasName)) {
            return false;
        }
        return ClassNameUtil.packageName(rawName).equals(ClassNameUtil.packageName(aliasName));
    }
}
