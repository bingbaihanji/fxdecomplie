package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

import java.util.HashSet;
import java.util.Set;

public class AvoidClsAndPkgNamesCollision extends AbstractDeobfCondition {

    private final Set<String> avoidClsNames = new HashSet<>();

    @Override
    public void init(RootNode root) {
        avoidClsNames.clear();
        for (PackageNode pkg : root.getPackages()) {
            avoidClsNames.add(pkg.getName());
        }
    }

    @Override
    public Action check(ClassNode cls) {
        if (avoidClsNames.contains(cls.getAlias())) {
            return Action.FORCE_RENAME;
        }
        return Action.NO_ACTION;
    }
}
