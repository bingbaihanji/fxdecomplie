package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.AnnotationVisibility;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.JadxAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.ConstPoolReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.DataReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.JavaClassData;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaAnnotationsAttr implements IJavaAttribute {
    private final List<IAnnotation> list;

    public JavaAnnotationsAttr(List<IAnnotation> list) {
        this.list = list;
    }

    public static IJavaAttributeReader reader(AnnotationVisibility visibility) {
        return (clsData, reader) -> new JavaAnnotationsAttr(readAnnotationsList(visibility, clsData, reader));
    }

    public static List<IAnnotation> readAnnotationsList(AnnotationVisibility visibility, JavaClassData clsData, DataReader reader) {
        int len = reader.readU2();
        List<IAnnotation> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(readAnnotation(visibility, clsData, reader));
        }
        return list;
    }

    public static JadxAnnotation readAnnotation(AnnotationVisibility visibility, JavaClassData clsData, DataReader reader) {
        ConstPoolReader constPool = clsData.getConstPoolReader();
        String type = constPool.getUtf8(reader.readU2());
        int pairsCount = reader.readU2();
        Map<String, EncodedValue> pairs = new LinkedHashMap<>(pairsCount);
        for (int j = 0; j < pairsCount; j++) {
            String name = constPool.getUtf8(reader.readU2());
            EncodedValue value = EncodedValueReader.read(clsData, reader);
            pairs.put(name, value);
        }
        return new JadxAnnotation(visibility, type, pairs);
    }

    public static AnnotationsAttr merge(JavaAttrStorage storage) {
        JavaAnnotationsAttr runtimeAnnAttr = storage.get(JavaAttrType.RUNTIME_ANNOTATIONS);
        JavaAnnotationsAttr buildAnnAttr = storage.get(JavaAttrType.BUILD_ANNOTATIONS);
        if (runtimeAnnAttr == null && buildAnnAttr == null) {
            return null;
        }
        if (buildAnnAttr == null) {
            return AnnotationsAttr.pack(runtimeAnnAttr.getList());
        }
        if (runtimeAnnAttr == null) {
            return AnnotationsAttr.pack(buildAnnAttr.getList());
        }
        return AnnotationsAttr.pack(Utils.concat(runtimeAnnAttr.getList(), buildAnnAttr.getList()));
    }

    public List<IAnnotation> getList() {
        return list;
    }
}
