package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;

import java.util.Collections;
import java.util.List;

public final class EmptyAttrStorage extends AttributeStorage {

    public static final AttributeStorage INSTANCE = new EmptyAttrStorage();

    private EmptyAttrStorage() {
        // singleton
    }

    @Override
    public boolean contains(AFlag flag) {
        return false;
    }

    @Override
    public <T extends IJadxAttribute> boolean contains(IJadxAttrType<T> type) {
        return false;
    }

    @Override
    public <T extends IJadxAttribute> T get(IJadxAttrType<T> type) {
        return null;
    }

    @Override
    public IAnnotation getAnnotation(String cls) {
        return null;
    }

    @Override
    public <T> List<T> getAll(IJadxAttrType<AttrList<T>> type) {
        return Collections.emptyList();
    }

    @Override
    public void remove(AFlag flag) {
        // ignore
    }

    @Override
    public <T extends IJadxAttribute> void remove(IJadxAttrType<T> type) {
        // ignore
    }

    @Override
    public void remove(IJadxAttribute attr) {
        // ignore
    }

    @Override
    public List<String> getAttributeStrings() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toString() {
        return "";
    }
}
