package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.IResTableParser;

/**
 * Provides the resource table parser instance for specific resource table file format. Can be used
 * in plugins via {@code context.getResourcesLoader().addResTableParserProvider()} to parse
 * resources from tables
 * in different formats.
 */
public interface IResTableParserProvider {

	/**
	 * Optional init method
	 */
	default void init(RootNode root) {
	}

	/**
	 * Checks a file format and provides the instance if the format is expected.
	 *
	 * @return {@link IResTableParser} if resource table is of expected format, {@code null} otherwise.
	 */
	@Nullable
	IResTableParser getParser(ResourceFile resFile);
}
