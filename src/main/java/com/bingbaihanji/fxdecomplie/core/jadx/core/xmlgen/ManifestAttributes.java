package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen;

import com.bingbaihanji.fxdecomplie.core.jadx.api.security.IJadxSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.RawNamedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ResourceEntry;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ValuesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.*;

// TODO: 移动到 Android 专用模块！

/**
 * 加载并存储 Android Manifest 属性规范。
 * <p>
 * 从 classpath 中的 attrs.xml 和 attrs_manifest.xml 解析 Android 标准属性定义，
 * 支持对 ENUM（枚举）和 FLAG（标志位）类型的属性值进行解码。
 * </p>
 */
public class ManifestAttributes {
    private static final Logger LOG = LoggerFactory.getLogger(ManifestAttributes.class);

    private static final String ATTR_XML = "/android/attrs.xml";
    private static final String MANIFEST_ATTR_XML = "/android/attrs_manifest.xml";
    private final IJadxSecurity security;
    /**
     * 存储默认 Android 资源属性定义的映射表。
     * 键为 Android 属性名（例如 "android:layout_width"），
     * 值为对应的 {@link MAttr} 对象。
     */
    private final Map<String, MAttr> attrMap = new HashMap<>();
    /** 存储应用自定义属性定义的映射表，键为属性键名，值为对应的 {@link MAttr} 对象 */
    private final Map<String, MAttr> appAttrMap = new HashMap<>();

    /**
     * 构造 Manifest 属性解析器，并立即解析内置的属性定义文件。
     *
     * @param security 安全接口，用于安全地解析 XML
     */
    public ManifestAttributes(IJadxSecurity security) {
        this.security = security;
        parseAll();
    }

    private void parseAll() {
        parse(loadXML(ATTR_XML));
        parse(loadXML(MANIFEST_ATTR_XML));
        LOG.debug("Loaded android attributes count: {}", attrMap.size());
    }

    private Document loadXML(String xml) {
        Document doc;
        try (InputStream xmlStream = ManifestAttributes.class.getResourceAsStream(xml)) {
            if (xmlStream == null) {
                throw new JadxRuntimeException(xml + " not found in classpath");
            }
            doc = security.parseXml(xmlStream);
        } catch (Exception e) {
            throw new JadxRuntimeException("Xml load error, file: " + xml, e);
        }
        return doc;
    }

    private void parse(Document doc) {
        NodeList nodeList = doc.getChildNodes();
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node node = nodeList.item(count);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.hasChildNodes()) {
                parseAttrList(node.getChildNodes());
            }
        }
    }

    private void parseAttrList(NodeList nodeList) {
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);
            if (tempNode.getNodeType() == Node.ELEMENT_NODE
                    && tempNode.hasAttributes()
                    && tempNode.hasChildNodes()) {
                String name = null;
                NamedNodeMap nodeMap = tempNode.getAttributes();
                for (int i = 0; i < nodeMap.getLength(); i++) {
                    Node node = nodeMap.item(i);
                    if ("name".equals(node.getNodeName())) {
                        name = node.getNodeValue();
                        break;
                    }
                }
                if (name != null && "attr".equals(tempNode.getNodeName())) {
                    parseValues(name, tempNode.getChildNodes());
                } else {
                    parseAttrList(tempNode.getChildNodes());
                }
            }
        }
    }

    private void parseValues(String name, NodeList nodeList) {
        MAttr attr = null;
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);
            if (tempNode.getNodeType() == Node.ELEMENT_NODE
                    && tempNode.hasAttributes()) {
                if (attr == null) {
                    if ("enum".equals(tempNode.getNodeName())) {
                        attr = new MAttr(MAttrType.ENUM);
                    } else if ("flag".equals(tempNode.getNodeName())) {
                        attr = new MAttr(MAttrType.FLAG);
                    }
                    if (attr == null) {
                        return;
                    }
                    attrMap.put("android:" + name, attr);
                }
                NamedNodeMap attributes = tempNode.getAttributes();
                Node nameNode = attributes.getNamedItem("name");
                if (nameNode != null) {
                    Node valueNode = attributes.getNamedItem("value");
                    if (valueNode != null) {
                        try {
                            long key;
                            String nodeValue = valueNode.getNodeValue();
                            if (nodeValue.startsWith("0x")) {
                                nodeValue = nodeValue.substring(2);
                                key = Long.parseLong(nodeValue, 16);
                            } else {
                                key = Long.parseLong(nodeValue);
                            }
                            attr.addValue(key, nameNode.getNodeValue());
                        } catch (NumberFormatException e) {
                            LOG.debug("Failed parse manifest number", e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 将属性的原始数值解码为可读的名称。
     * <p>
     * 对于 ENUM 类型，直接返回数值对应的名称；
     * 对于 FLAG 类型，将数值按标志位拆解，返回以 "|" 连接的名称组合。
     * 查找顺序为先默认属性表，未命中再查应用自定义属性表。
     * </p>
     *
     * @param attrName 属性名
     * @param value    属性的原始数值
     * @return 解码后的名称字符串，若无法解码则返回 {@code null}
     */
    public String decode(String attrName, long value) {
        MAttr attr = attrMap.get(attrName);
        if (attr == null) {
            if (attrName.contains(":")) {
                attrName = attrName.split(":", 2)[1];
            }
            attr = appAttrMap.get(attrName);
            if (attr == null) {
                return null;
            }
        }

        Map<Long, String> attrValuesMap = attr.getValues();
        if (attr.getType() == MAttrType.ENUM) {
            return attrValuesMap.get(value);
        } else if (attr.getType() == MAttrType.FLAG) {
            List<String> flagList = new ArrayList<>();
            List<Long> attrKeys = new ArrayList<>(attrValuesMap.keySet());
            attrKeys.sort((a, b) -> Long.compare(b, a)); // 降序排序
            for (Long key : attrKeys) {
                String attrValue = attrValuesMap.get(key);
                if (value == key) {
                    flagList.add(attrValue);
                    break;
                } else if ((key != 0) && ((value & key) == key)) {
                    flagList.add(attrValue);
                    value ^= key;
                }
            }
            return String.join("|", flagList);
        }
        return null;
    }

    /**
     * 根据资源表解析器中的资源条目，更新应用自定义属性映射表。
     * <p>
     * 遍历所有 "attr" 类型的资源条目，识别其为 FLAG 或 ENUM 类型，
     * 并将各命名值加入对应的 {@link MAttr} 中，供后续解码使用。
     * </p>
     *
     * @param parser 资源表解析器
     */
    public void updateAttributes(IResTableParser parser) {
        appAttrMap.clear();

        ResourceStorage resStorage = parser.getResStorage();
        ValuesParser vp = new ValuesParser(parser.getStrings(), resStorage.getResourcesNames());

        for (ResourceEntry ri : resStorage.getResources()) {
            if (ri.getProtoValue() != null) {
                // Aapt proto 解码器会自行解析属性
                continue;
            }

            if ("attr".equals(ri.getTypeName()) && ri.getNamedValues().size() > 1) {
                RawNamedValue first = ri.getNamedValues().get(0);
                MAttrType attrTyp;
                int attrTypeVal = first.getRawValue().getData() & 0xff0000;
                if (attrTypeVal == ValuesParser.ATTR_TYPE_FLAGS) {
                    attrTyp = MAttrType.FLAG;
                } else if (attrTypeVal == ValuesParser.ATTR_TYPE_ENUM) {
                    attrTyp = MAttrType.ENUM;
                } else {
                    continue;
                }
                MAttr attr = new MAttr(attrTyp);
                for (int i = 1; i < ri.getNamedValues().size(); i++) {
                    RawNamedValue rv = ri.getNamedValues().get(i);
                    String value = vp.decodeNameRef(rv.getNameRef());
                    attr.addValue(rv.getRawValue().getData(), value.startsWith("id.") ? value.substring(3) : value);
                }
                appAttrMap.put(ri.getKeyName(), attr);
            }
        }
    }

    /** 属性值类型：ENUM（枚举，单值）、FLAG（标志位，可按位组合） */
    private enum MAttrType {
        ENUM, FLAG
    }

    /**
     * 描述单个 Android 属性的可选值集合。
     * 包含属性类型（枚举或标志位）以及「数值 → 名称」的映射。
     */
    private static class MAttr {
        private final MAttrType type;
        private final Map<Long, String> values = new LinkedHashMap<>();

        public MAttr(MAttrType type) {
            this.type = type;
        }

        public MAttrType getType() {
            return type;
        }

        public Map<Long, String> getValues() {
            return values;
        }

        public void addValue(long key, String value) {
            values.put(key, value);
        }

        @Override
        public String toString() {
            return "[" + type + ", " + values + ']';
        }
    }
}
