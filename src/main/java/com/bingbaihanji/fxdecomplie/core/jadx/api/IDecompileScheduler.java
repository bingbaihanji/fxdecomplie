package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.util.List;

public interface IDecompileScheduler {
	List<List<JavaClass>> buildBatches(List<JavaClass> classes);
}
