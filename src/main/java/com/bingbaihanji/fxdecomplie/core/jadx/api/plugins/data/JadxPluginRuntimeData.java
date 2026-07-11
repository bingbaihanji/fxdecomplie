package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.data;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.JadxCodeInput;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.JadxPluginOptions;

/**
 * Runtime plugin data.
 */
public interface JadxPluginRuntimeData {
	boolean isInitialized();

	String getPluginId();

	JadxPlugin getPluginInstance();

	JadxPluginInfo getPluginInfo();

	List<JadxCodeInput> getCodeInputs();

	@Nullable
	JadxPluginOptions getOptions();

	String getInputsHash();

	/**
	 * Convenient method to simplify code loading from custom files.
	 */
	ICodeLoader loadCodeFiles(List<Path> files, @Nullable Closeable closeable);
}
