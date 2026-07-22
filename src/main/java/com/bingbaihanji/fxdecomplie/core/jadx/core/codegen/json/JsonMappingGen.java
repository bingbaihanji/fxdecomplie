package com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.mapping.JsonClsMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.mapping.JsonFieldMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.mapping.JsonMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.mapping.JsonMthMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class JsonMappingGen {
    private static final Logger LOG = LoggerFactory.getLogger(JsonMappingGen.class);

    private static final ObjectMapper GSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonMappingGen() {
    }

    public static void dump(RootNode root) {
        JsonMapping mapping = new JsonMapping();
        fillMapping(mapping, root);

        JadxArgs args = root.getArgs();
        File outDirSrc = args.getOutDirSrc().getAbsoluteFile();
        File mappingFile = new File(outDirSrc, "mapping.json");
        IoUtils.makeDirsForFile(mappingFile);
        try (Writer writer = new FileWriter(mappingFile)) {
            GSON.writeValue(writer, mapping);
            LOG.info("Save mappings to {}", mappingFile.getAbsolutePath());
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to save mapping json", e);
        }
    }

    private static void fillMapping(JsonMapping mapping, RootNode root) {
        List<ClassNode> classes = root.getClasses(true);
        mapping.setClasses(new ArrayList<>(classes.size()));
        for (ClassNode cls : classes) {
            ClassInfo classInfo = cls.getClassInfo();
            JsonClsMapping jsonCls = new JsonClsMapping();
            jsonCls.setName(classInfo.getRawName());
            jsonCls.setAlias(classInfo.getAliasFullName());
            jsonCls.setInner(classInfo.isInner());
            jsonCls.setJson(cls.getTopParentClass().getClassInfo().getAliasFullPath() + ".json");
            if (classInfo.isInner()) {
                jsonCls.setTopClass(cls.getTopParentClass().getClassInfo().getFullName());
            }
            addFields(cls, jsonCls);
            addMethods(cls, jsonCls);
            mapping.getClasses().add(jsonCls);
        }
    }

    private static void addMethods(ClassNode cls, JsonClsMapping jsonCls) {
        List<MethodNode> methods = cls.getMethods();
        if (methods.isEmpty()) {
            return;
        }
        jsonCls.setMethods(new ArrayList<>(methods.size()));
        for (MethodNode method : methods) {
            JsonMthMapping jsonMethod = new JsonMthMapping();
            MethodInfo methodInfo = method.methodInfo();
            jsonMethod.setSignature(methodInfo.getShortId());
            jsonMethod.setName(methodInfo.getName());
            jsonMethod.setAlias(methodInfo.getAlias());
            jsonMethod.setOffset("0x" + Long.toHexString(method.getMethodCodeOffset()));
            jsonCls.getMethods().add(jsonMethod);
        }
    }

    private static void addFields(ClassNode cls, JsonClsMapping jsonCls) {
        List<FieldNode> fields = cls.getFields();
        if (fields.isEmpty()) {
            return;
        }
        jsonCls.setFields(new ArrayList<>(fields.size()));
        for (FieldNode field : fields) {
            JsonFieldMapping jsonField = new JsonFieldMapping();
            jsonField.setName(field.getName());
            jsonField.setAlias(field.getAlias());
            jsonCls.getFields().add(jsonField);
        }
    }
}
