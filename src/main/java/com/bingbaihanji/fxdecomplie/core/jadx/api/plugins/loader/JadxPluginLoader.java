package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.loader;

import java.io.Closeable;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;

public interface JadxPluginLoader extends Closeable {

	List<JadxPlugin> load();
}
