package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry;

import java.util.List;

/**
 * 资源条目
 * <p>
 * 表示 Android 资源表 (resources.arsc)中的单个资源项，包含其 32 位资源 ID、
 * 所属包名、类型名、键名、配置限定符，以及对应的取值 (proto 值 / 简单值 / 命名值列表)
 * 和父样式引用该对象为不可变的标识信息载体，取值部分可通过 setter 补充填充
 */
public final class ResourceEntry {

    private final int id;
    private final String pkgName;
    private final String typeName;
    private final String keyName;
    private final String config;

    private int parentRef;
    private ProtoValue protoValue;
    private RawValue simpleValue;
    private List<RawNamedValue> namedValues;

    public ResourceEntry(int id, String pkgName, String typeName, String keyName, String config) {
        this.id = id;
        this.pkgName = pkgName;
        this.typeName = typeName;
        this.keyName = keyName;
        this.config = config;
    }

    /**
     * 以新的键名复制当前资源条目 (保留取值与父引用)
     *
     * @param newKeyName 新的键名
     * @return 复制得到的资源条目
     */
    public ResourceEntry copy(String newKeyName) {
        ResourceEntry copy = new ResourceEntry(id, pkgName, typeName, newKeyName, config);
        copy.parentRef = this.parentRef;
        copy.protoValue = this.protoValue;
        copy.simpleValue = this.simpleValue;
        copy.namedValues = this.namedValues;
        return copy;
    }

    /**
     * 复制当前资源条目，并将键名替换为带资源 ID 的形式 ({@code 资源名_res_0x十六进制ID})
     *
     * @param resName 资源名称
     * @return 复制得到的资源条目
     */
    public ResourceEntry copyWithId(String resName) {
        return copy(String.format("%s_res_0x%08x", resName, id));
    }

    /**
     * AOSP 中定义的 32 位资源 ID
     *
     * <ol>
     * <li>包 ID (8 位)</li>
     * <li>类型 ID (8 位)</li>
     * <li>条目 ID (16 位)</li>
     * </ol>
     *
     * 参见 ResourceUtils.h 中的 <code>make_resid()</code>
     *
     * @return 资源 ID
     */
    public int getId() {
        return id;
    }

    public String getPkgName() {
        return pkgName;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getKeyName() {
        return keyName;
    }

    public String getConfig() {
        return config;
    }

    public int getParentRef() {
        return parentRef;
    }

    public void setParentRef(int parentRef) {
        this.parentRef = parentRef;
    }

    public ProtoValue getProtoValue() {
        return protoValue;
    }

    public void setProtoValue(ProtoValue protoValue) {
        this.protoValue = protoValue;
    }

    public RawValue getSimpleValue() {
        return simpleValue;
    }

    public void setSimpleValue(RawValue simpleValue) {
        this.simpleValue = simpleValue;
    }

    public List<RawNamedValue> getNamedValues() {
        return namedValues;
    }

    public void setNamedValues(List<RawNamedValue> namedValues) {
        this.namedValues = namedValues;
    }

    @Override
    public String toString() {
        return "  0x" + Integer.toHexString(id) + " (" + id + ')' + config + " = " + typeName + '.' + keyName;
    }
}
