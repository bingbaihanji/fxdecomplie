package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/*
 * Modifies android:name attributes and xml tags which were changed during deobfuscation
 */
public class XmlDeobf {

    private XmlDeobf() {
    }

    @Nullable
    public static String deobfClassName(RootNode root, String potentialClassName, String packageName) {
        if (potentialClassName.indexOf('.') == -1) {
            return null;
        }
        if (packageName != null && potentialClassName.startsWith(".")) {
            potentialClassName = packageName + potentialClassName;
        }
        ArgType clsType = ArgType.object(potentialClassName);
        ClassInfo classInfo = root.getInfoStorage().getCls(clsType);
        if (classInfo == null) {
            // unknown class reference
            return null;
        }
        return classInfo.getAliasFullName();
    }

    public static boolean isDuplicatedAttr(String attrFullName, Set<String> attrCache) {
        return !attrCache.add(attrFullName);
    }
}
