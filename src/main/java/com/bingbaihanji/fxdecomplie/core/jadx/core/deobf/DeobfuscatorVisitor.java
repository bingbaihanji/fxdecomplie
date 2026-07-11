package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IAliasProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

public class DeobfuscatorVisitor extends AbstractVisitor {

    public static void process(RootNode root, IRenameCondition renameCondition, IAliasProvider aliasProvider) {
        boolean pkgUpdated = false;
        for (PackageNode pkg : root.getPackages()) {
            if (renameCondition.shouldRename(pkg)) {
                String alias = aliasProvider.forPackage(pkg);
                if (alias != null) {
                    pkg.rename(alias, false);
                    pkgUpdated = true;
                }
            }
        }
        if (pkgUpdated) {
            root.runPackagesUpdate();
        }

        for (ClassNode cls : root.getClasses()) {
            if (renameCondition.shouldRename(cls)) {
                String clsAlias = aliasProvider.forClass(cls);
                if (clsAlias != null) {
                    cls.rename(clsAlias);
                }
            }
            for (FieldNode fld : cls.getFields()) {
                if (renameCondition.shouldRename(fld)) {
                    String fldAlias = aliasProvider.forField(fld);
                    if (fldAlias != null) {
                        fld.rename(fldAlias);
                    }
                }
            }
            for (MethodNode mth : cls.getMethods()) {
                if (renameCondition.shouldRename(mth)) {
                    String mthAlias = aliasProvider.forMethod(mth);
                    if (mthAlias != null) {
                        mth.rename(mthAlias);
                    }
                }
            }
        }
    }

    @Override
    public void init(RootNode root) throws JadxException {
        JadxArgs args = root.getArgs();
        if (!args.isDeobfuscationOn()) {
            return;
        }
        DeobfPresets mapping = DeobfPresets.build(root);
        if (args.getGeneratedRenamesMappingFileMode().shouldRead()) {
            if (mapping.load()) {
                mapping.apply(root);
            }
        }
        IAliasProvider aliasProvider = args.getAliasProvider();
        IRenameCondition renameCondition = args.getRenameCondition();
        mapping.initIndexes(aliasProvider);
        process(root, renameCondition, aliasProvider);
    }

    @Override
    public String getName() {
        return "DeobfuscatorVisitor";
    }
}
