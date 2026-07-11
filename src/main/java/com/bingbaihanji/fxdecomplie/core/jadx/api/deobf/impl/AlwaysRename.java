package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public class AlwaysRename implements IRenameCondition {

	public static final IRenameCondition INSTANCE = new AlwaysRename();

	private AlwaysRename() {
	}

	@Override
	public void init(RootNode root) {
	}

	@Override
	public boolean shouldRename(PackageNode pkg) {
		return true;
	}

	@Override
	public boolean shouldRename(ClassNode cls) {
		return true;
	}

	@Override
	public boolean shouldRename(FieldNode fld) {
		return true;
	}

	@Override
	public boolean shouldRename(MethodNode mth) {
		return true;
	}
}
