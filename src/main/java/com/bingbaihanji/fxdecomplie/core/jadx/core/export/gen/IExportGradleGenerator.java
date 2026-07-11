package com.bingbaihanji.fxdecomplie.core.jadx.core.export.gen;

import com.bingbaihanji.fxdecomplie.core.jadx.core.export.OutDirs;

public interface IExportGradleGenerator {

	void init();

	OutDirs getOutDirs();

	void generateFiles();
}
