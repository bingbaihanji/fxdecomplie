package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeRename;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaCodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.InfoStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class UserRenames {
    private static final Logger LOG = LoggerFactory.getLogger(UserRenames.class);

    public static void apply(RootNode root) {
        ICodeData codeData = root.getArgs().getCodeData();
        if (codeData == null || codeData.getRenames().isEmpty()) {
            return;
        }
        InfoStorage infoStorage = root.getInfoStorage();
        codeData.getRenames().stream()
                .filter(r -> r.getCodeRef() == null && r.getNodeRef().getType() != IJavaNodeRef.RefType.PKG)
                .collect(Collectors.groupingBy(r -> r.getNodeRef().getDeclaringClass()))
                .forEach((clsRawName, renames) -> {
                    ClassInfo clsInfo = infoStorage.getCls(ArgType.object(clsRawName));
                    if (clsInfo != null) {
                        ClassNode cls = root.resolveClass(clsInfo);
                        if (cls != null) {
                            for (ICodeRename rename : renames) {
                                applyRename(cls, rename);
                            }
                            return;
                        }
                    }
                    LOG.warn("Class info with reference '{}' not found", clsRawName);
                });
        applyPkgRenames(root, codeData.getRenames());
    }

    private static void applyRename(ClassNode cls, ICodeRename rename) {
        IJavaNodeRef nodeRef = rename.getNodeRef();
        switch (nodeRef.getType()) {
            case CLASS:
                cls.rename(rename.getNewName());
                break;

            case FIELD:
                FieldNode fieldNode = cls.searchFieldByShortId(nodeRef.getShortId());
                if (fieldNode == null) {
                    String fieldName = CodegenEscapeUtils.getPrefix(nodeRef.getShortId(), ":");
                    String fieldSign = cls.getFields().stream()
                            .filter(f -> f.getFieldInfo().getName().equals(fieldName))
                            .map(f -> f.getFieldInfo().getShortId())
                            .collect(Collectors.joining());
                    LOG.warn("Field reference not found: {}. Fields with same name: {}", nodeRef, fieldSign);
                } else {
                    fieldNode.rename(rename.getNewName());
                }
                break;

            case METHOD:
                MethodNode mth = cls.searchMethodByShortId(nodeRef.getShortId());
                if (mth == null) {
                    LOG.warn("Method reference not found: {}", nodeRef);
                } else {
                    IJavaCodeRef codeRef = rename.getCodeRef();
                    if (codeRef == null) {
                        mth.rename(rename.getNewName());
                    }
                }
                break;
        }
    }

    private static void applyPkgRenames(RootNode root, List<ICodeRename> renames) {
        renames.stream()
                .filter(r -> r.getNodeRef().getType() == IJavaNodeRef.RefType.PKG)
                .forEach(pkgRename -> {
                    String pkgFullName = pkgRename.getNodeRef().getDeclaringClass();
                    PackageNode pkgNode = root.resolvePackage(pkgFullName);
                    if (pkgNode == null) {
                        LOG.warn("Package for rename not found: {}", pkgFullName);
                    } else {
                        pkgNode.rename(pkgRename.getNewName());
                    }
                });
    }
}
