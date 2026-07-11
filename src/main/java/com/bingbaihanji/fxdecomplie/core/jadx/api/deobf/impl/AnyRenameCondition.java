package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl;

import java.util.function.BiPredicate;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IDexNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public class AnyRenameCondition implements IRenameCondition {

	private final BiPredicate<String, IDexNode> predicate;

	public AnyRenameCondition(BiPredicate<String, IDexNode> predicate) {
		this.predicate = predicate;
	}

	@Override
	public void init(RootNode root) {
	}

	@Override
	public boolean shouldRename(PackageNode pkg) {
		return predicate.test(pkg.getAliasPkgInfo().getName(), pkg);
	}

	@Override
	public boolean shouldRename(ClassNode cls) {
		return predicate.test(cls.getAlias(), cls);
	}

	@Override
	public boolean shouldRename(FieldNode fld) {
		return predicate.test(fld.getAlias(), fld);
	}

	@Override
	public boolean shouldRename(MethodNode mth) {
		return predicate.test(mth.getAlias(), mth);
	}
}
