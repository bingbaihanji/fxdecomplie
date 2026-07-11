package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes;

import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.DataReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.JavaClassData;

public interface IJavaAttributeReader {
    IJavaAttribute read(JavaClassData clsData, DataReader reader);
}
