package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.stack.StackFrame;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class StackMapTableAttr implements IJavaAttribute {
    public static final StackMapTableAttr EMPTY = new StackMapTableAttr(Collections.emptyMap());

    private final Map<Integer, StackFrame> map;

    public StackMapTableAttr(Map<Integer, StackFrame> map) {
        this.map = map;
    }

    public @Nullable StackFrame getFor(int offset) {
        return map.get(offset);
    }
}
