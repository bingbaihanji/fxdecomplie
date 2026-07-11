package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 从 jadx 输出类集合中选择本次请求对应的目标类。
 */
public final class JadxOutputSelector {

    private JadxOutputSelector() {
        throw new AssertionError("utility class");
    }

    @Nullable
    public static JavaClass select(List<JavaClass> classes, String targetType) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        String normalizedTarget = ClassNameUtil.normalizeInternalName(targetType);
        for (JavaClass cls : classes) {
            String fullName = cls.getClassNode().getClassInfo().getFullName();
            if (ClassNameUtil.sameInternalName(fullName, normalizedTarget)) {
                return cls;
            }
        }

        String simpleName = ClassNameUtil.simpleName(normalizedTarget);
        for (JavaClass cls : classes) {
            if (simpleName.equals(cls.getName())) {
                return cls;
            }
        }
        return null;
    }
}
