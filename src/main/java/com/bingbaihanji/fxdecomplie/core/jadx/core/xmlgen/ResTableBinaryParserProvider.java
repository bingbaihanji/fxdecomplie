package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources.IResTableParserProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

public class ResTableBinaryParserProvider implements IResTableParserProvider {
	private RootNode root;

	@Override
	public void init(RootNode root) {
		this.root = root;
	}

	@Override
	public @Nullable IResTableParser getParser(ResourceFile resFile) {
		String fileName = resFile.getOriginalName();
		if (!fileName.endsWith(".arsc")) {
			return null;
		}
		return new ResTableBinaryParser(root);
	}
}
