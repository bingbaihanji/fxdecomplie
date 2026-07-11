package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public interface IAliasProvider {

	default void init(RootNode root) {
		// optional
	}

	String forPackage(PackageNode pkg);

	String forClass(ClassNode cls);

	String forField(FieldNode fld);

	String forMethod(MethodNode mth);

	/**
	 * Optional method to set initial max indexes loaded from mapping
	 */
	default void initIndexes(int pkg, int cls, int fld, int mth) {
		// optional
	}
}
