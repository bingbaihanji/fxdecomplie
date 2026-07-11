package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.JavaClassData;

public class JavaClassReader {
	private final int id;
	private final String fileName;
	private final byte[] data;

	public JavaClassReader(int id, String fileName, byte[] data) {
		this.id = id;
		this.fileName = fileName;
		this.data = data;
	}

	public IClassData loadClassData() {
		return new JavaClassData(this);
	}

	public int getId() {
		return id;
	}

	public String getFileName() {
		return fileName;
	}

	public byte[] getData() {
		return data;
	}

	@Override
	public String toString() {
		return fileName;
	}
}
