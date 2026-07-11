package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidResourcesMap;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.BinaryXMLStrings;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ParserConstants;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.XmlGenUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 资源值解析器
 * <p>
 * 负责将资源表 (resources.arsc)中的原始数据 (数据类型 + 数据值)解码为可读的字符串表示，
 * 支持字符串、整型、布尔、浮点、颜色、尺寸、分数以及资源引用、属性引用等各类数据类型，
 * 同时可解析简单值、命名值列表以及资源名称引用
 */
public class ValuesParser extends ParserConstants {
    private static final Logger LOG = LoggerFactory.getLogger(ValuesParser.class);

    private final BinaryXMLStrings strings;
    private final Map<Integer, String> resMap;

    /**
     * @param strings 二进制 XML 字符串池，用于解析字符串类型的值
     * @param resMap  资源 ID 到资源名称的映射表，用于解析引用类型的值
     */
    public ValuesParser(BinaryXMLStrings strings, Map<Integer, String> resMap) {
        this.strings = strings;
        this.resMap = resMap;
    }

    /**
     * 获取资源条目的简单值字符串
     * <p>
     * 优先返回 proto 值，否则解码其简单原始值
     *
     * @param ri 资源条目
     * @return 简单值字符串，若不存在则返回 {@code null}
     */
    @Nullable
    public String getSimpleValueString(ResourceEntry ri) {
        ProtoValue protoValue = ri.getProtoValue();
        if (protoValue != null) {
            return protoValue.getValue();
        }
        RawValue simpleValue = ri.getSimpleValue();
        if (simpleValue == null) {
            return null;
        }
        return decodeValue(simpleValue);
    }

    /**
     * 获取资源条目的完整值字符串
     * <p>
     * 依次处理 proto 值、简单值和命名值列表：命名值以 {@code name=value} 形式拼接为列表
     *
     * @param ri 资源条目
     * @return 值字符串，若不存在则返回 {@code null}
     */
    @Nullable
    public String getValueString(ResourceEntry ri) {
        ProtoValue protoValue = ri.getProtoValue();
        if (protoValue != null) {
            if (protoValue.getValue() != null) {
                return protoValue.getValue();
            }
            List<ProtoValue> values = protoValue.getNamedValues();
            List<String> strList = new ArrayList<>(values.size());
            for (ProtoValue value : values) {
                if (value.getName() == null) {
                    strList.add(value.getValue());
                } else {
                    strList.add(value.getName() + '=' + value.getValue());
                }
            }
            return strList.toString();
        }
        RawValue simpleValue = ri.getSimpleValue();
        if (simpleValue != null) {
            return decodeValue(simpleValue);
        }
        List<RawNamedValue> namedValues = ri.getNamedValues();
        List<String> strList = new ArrayList<>(namedValues.size());
        for (RawNamedValue value : namedValues) {
            String nameStr = decodeNameRef(value.getNameRef());
            String valueStr = decodeValue(value.getRawValue());
            if (nameStr == null) {
                strList.add(valueStr);
            } else {
                strList.add(nameStr + '=' + valueStr);
            }
        }
        return strList.toString();
    }

    /**
     * 解码原始值对象
     *
     * @param value 原始值 (包含数据类型与数据)
     * @return 解码后的字符串，若为空类型则返回 {@code null}
     */
    @Nullable
    public String decodeValue(RawValue value) {
        int dataType = value.getDataType();
        int data = value.getData();
        return decodeValue(dataType, data);
    }

    /**
     * 根据数据类型解码资源数据值
     *
     * @param dataType 数据类型 (TYPE_* 常量)
     * @param data     原始数据值
     * @return 解码后的字符串表示，空类型返回 {@code null}
     */
    @Nullable
    public String decodeValue(int dataType, int data) {
        switch (dataType) {
            case TYPE_NULL:
                return null;
            case TYPE_STRING:
                return strings.get(data);
            case TYPE_INT_DEC:
                return Integer.toString(data);
            case TYPE_INT_HEX:
                return "0x" + Integer.toHexString(data);
            case TYPE_INT_BOOLEAN:
                return data == 0 ? "false" : "true";
            case TYPE_FLOAT:
                return XmlGenUtils.floatToString(Float.intBitsToFloat(data));
            case TYPE_INT_COLOR_ARGB8:
                return String.format("#%08x", data);
            case TYPE_INT_COLOR_RGB8:
                return String.format("#%06x", data & 0xFFFFFF);
            case TYPE_INT_COLOR_ARGB4:
                return String.format("#%04x", data & 0xFFFF);
            case TYPE_INT_COLOR_RGB4:
                return String.format("#%03x", data & 0xFFF);

            case TYPE_DYNAMIC_REFERENCE:
            case TYPE_REFERENCE: {
                String ri = resMap.get(data);
                if (ri == null) {
                    String androidRi = AndroidResourcesMap.getResName(data);
                    if (androidRi != null) {
                        return "@android:" + androidRi;
                    }
                    if (data == 0) {
                        return "0";
                    }
                    return "?unknown_ref: " + Integer.toHexString(data);
                }
                return '@' + ri;
            }

            case TYPE_ATTRIBUTE: {
                String ri = resMap.get(data);
                if (ri == null) {
                    String androidRi = AndroidResourcesMap.getResName(data);
                    if (androidRi != null) {
                        return "?android:" + androidRi;
                    }
                    return "?unknown_attr_ref: " + Integer.toHexString(data);
                }
                return '?' + ri;
            }

            case TYPE_DIMENSION:
                return XmlGenUtils.decodeComplex(data, false);
            case TYPE_FRACTION:
                return XmlGenUtils.decodeComplex(data, true);
            case TYPE_DYNAMIC_ATTRIBUTE:
                LOG.warn("Data type TYPE_DYNAMIC_ATTRIBUTE not yet supported: {}", data);
                return "  TYPE_DYNAMIC_ATTRIBUTE: " + data;

            default:
                LOG.warn("Unknown data type: 0x{} {}", Integer.toHexString(dataType), data);
                return "  ?0x" + Integer.toHexString(dataType) + ' ' + data;
        }
    }

    /**
     * 解码名称引用，将资源 ID 引用还原为资源名称
     *
     * @param nameRef 名称引用 ID
     * @return 解码后的名称 (以 '.' 分隔)，无法解析时返回十六进制表示
     */
    public String decodeNameRef(int nameRef) {
        int ref = nameRef;
        if (isResInternalId(nameRef)) {
            ref = nameRef & ATTR_TYPE_ANY;
            if (ref == 0) {
                return null;
            }
        }
        String ri = resMap.get(ref);
        if (ri != null) {
            return ri.replace('/', '.');
        } else {
            String androidRi = AndroidResourcesMap.getResName(ref);
            if (androidRi != null) {
                return "android:" + androidRi.replace('/', '.');
            }
        }
        return "?0x" + Integer.toHexString(nameRef);
    }
}
