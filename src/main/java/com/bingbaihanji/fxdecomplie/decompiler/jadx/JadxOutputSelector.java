package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 从 jadx 输出类集合中选择本次请求对应的目标类 
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
            if (matchesTarget(cls, normalizedTarget)) {
                return cls;
            }
        }

        String simpleName = ClassNameUtil.simpleName(normalizedTarget);
        for (JavaClass cls : classes) {
            if (simpleName.equals(ClassNameUtil.simpleName(cls.getRawName()))
                    || simpleName.equals(cls.getClassNode().getName())
                    || simpleName.equals(cls.getName())) {
                return cls;
            }
        }
        return null;
    }

    private static boolean matchesTarget(JavaClass cls, String normalizedTarget) {
        return ClassNameUtil.sameInternalName(cls.getRawName(), normalizedTarget)
                || ClassNameUtil.sameInternalName(
                cls.getClassNode().getClassInfo().makeRawFullName(), normalizedTarget)
                || ClassNameUtil.sameInternalName(
                cls.getClassNode().getClassInfo().getFullName(), normalizedTarget)
                || ClassNameUtil.sameInternalName(cls.getFullName(), normalizedTarget);
    }
}
