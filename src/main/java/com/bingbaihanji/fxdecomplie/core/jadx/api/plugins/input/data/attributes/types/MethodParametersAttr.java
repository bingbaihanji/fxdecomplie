package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlagsScope;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;

import java.util.List;

public class MethodParametersAttr extends PinnedAttribute {

    private final List<Info> list;

    public MethodParametersAttr(List<Info> list) {
        this.list = list;
    }

    public List<Info> getList() {
        return list;
    }

    @Override
    public IJadxAttrType<MethodParametersAttr> getAttrType() {
        return JadxAttrType.METHOD_PARAMETERS;
    }

    @Override
    public String toString() {
        return "METHOD_PARAMETERS: " + list;
    }

    public static class Info {
        private final int accFlags;
        private final String name;

        public Info(int accFlags, String name) {
            this.accFlags = accFlags;
            this.name = name;
        }

        public int getAccFlags() {
            return accFlags;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return AccessFlags.format(accFlags, AccessFlagsScope.METHOD) + name;
        }
    }
}
