package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources;

public interface IResourcesLoader {

	void addResContainerFactory(IResContainerFactory resContainerFactory);

	void addResTableParserProvider(IResTableParserProvider resTableParserProvider);
}
