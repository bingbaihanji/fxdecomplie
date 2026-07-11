package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public interface IRenameCondition {

	void init(RootNode root);

	boolean shouldRename(PackageNode pkg);

	boolean shouldRename(ClassNode cls);

	boolean shouldRename(FieldNode fld);

	boolean shouldRename(MethodNode mth);
}
