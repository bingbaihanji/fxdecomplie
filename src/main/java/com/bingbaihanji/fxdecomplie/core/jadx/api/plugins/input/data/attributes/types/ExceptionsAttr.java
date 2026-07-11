package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;

import java.util.List;

public class ExceptionsAttr extends PinnedAttribute {
    private final List<String> list;

    public ExceptionsAttr(List<String> list) {
        this.list = list;
    }

    public List<String> getList() {
        return list;
    }

    @Override
    public IJadxAttrType<ExceptionsAttr> getAttrType() {
        return JadxAttrType.EXCEPTIONS;
    }

    @Override
    public String toString() {
        return "EXCEPTIONS:" + list;
    }
}
