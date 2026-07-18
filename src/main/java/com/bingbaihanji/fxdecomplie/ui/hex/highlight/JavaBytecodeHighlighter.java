package com.bingbaihanji.fxdecomplie.ui.hex.highlight;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import javafx.scene.paint.Color;

/**
 * 识别并高亮 Java {@code .class} 文件结构的 {@link BuiltinHighlighter} 实现 
 * <p>
 * 参照《Java 虚拟机规范》§4 定义的类文件格式,对以下结构进行颜色标记：
 * <ul>
 *   <li>魔数(Magic)</li>
 *   <li>次/主版本号</li>
 *   <li>常量池(Constant Pool)及其各项条目</li>
 *   <li>访问标志(Access Flags)</li>
 *   <li>当前类、父类、接口列表</li>
 *   <li>字段(Fields)和属性(Attributes)</li>
 *   <li>方法(Methods)和属性</li>
 *   <li>类级属性</li>
 * </ul>
 * 颜色方案通过 {@link HexViewController} 获取,保持与全局主题一致 
 * </p>
 *
 * @author BingBaiHanJi
 * @see BuiltinHighlighter
 * @see PatternModel
 * @see HexViewController
 */
public class JavaBytecodeHighlighter implements BuiltinHighlighter {

    /** Java 类文件魔数 {@code 0xCAFEBABE} */
    private static final int MAGIC = 0xCAFEBABE;

    // ---------- 字节读取工具 ----------

    /**
     * 从指定地址读取一个无符号字节(U1) 
     *
     * @param p    数据提供者
     * @param addr 地址
     * @return 无符号字节值(0~255)
     */
    private static int readU1(HexDataProvider p, long addr) {
        byte[] b = new byte[1];
        p.read(addr, b, 0, 1);
        return b[0] & 0xFF;
    }

    /**
     * 将字节数组(大端序)转换为无符号 16 位整数(U2) 
     *
     * @param buf 长度为 2 的字节数组
     * @return 无符号整数(0~65535)
     */
    private static int u2(byte[] buf) {
        return ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
    }

    /**
     * 将字节数组(大端序)转换为无符号 32 位整数(U4) 
     *
     * @param buf 长度为 4 的字节数组
     * @return 无符号长整数(0~2^32-1)
     */
    private static long u4be(byte[] buf) {
        return (((buf[0] & 0xFFL) << 24) | ((buf[1] & 0xFFL) << 16)
                | ((buf[2] & 0xFFL) << 8) | (buf[3] & 0xFFL)) & 0xFFFF_FFFFL;
    }

    /**
     * 从指定地址读取一个无符号 16 位整数(大端序) 
     *
     * @param p    数据提供者
     * @param addr 地址
     * @return U2 值
     */
    private static int readU2(HexDataProvider p, long addr) {
        byte[] b = new byte[2];
        p.read(addr, b, 0, 2);
        return u2(b);
    }

    /**
     * 从指定地址读取一个无符号 32 位整数(大端序) 
     *
     * @param p    数据提供者
     * @param addr 地址
     * @return U4 值(long 类型)
     */
    private static long readU4(HexDataProvider p, long addr) {
        byte[] b = new byte[4];
        p.read(addr, b, 0, 4);
        return u4be(b);
    }

    // ---------- 常量池条目详情添加 ----------

    /**
     * 为常量池中的单个条目添加详细的子区域(Region)到模型中 
     *
     * @param model 模式模型
     * @param p     数据提供者
     * @param pos   常量池条目起始位置
     * @param index 条目索引(从 1 开始)
     * @param tag   条目标签(CONSTANT_*)
     * @param ctrl  HexViewController 用于获取颜色
     */
    private static void addConstantPoolEntryDetails(PatternModel model, HexDataProvider p,
                                                    long pos, int index, int tag,
                                                    HexViewController ctrl) {
        Color color = ctrl.getPoolColor();
        String parent = "cp[" + index + "]";
        model.addRegion(new PatternModel.Region(pos, 1, parent + ".tag",
                "Tag: " + constantPoolTagName(tag), color, parent));
        switch (tag) {
            case 1 -> { // CONSTANT_Utf8
                int length = readU2(p, pos + 1);
                model.addRegion(new PatternModel.Region(pos + 1, 2, parent + ".length",
                        "UTF-8 byte length: " + length, color, parent));
                if (length > 0) {
                    model.addRegion(new PatternModel.Region(pos + 3, length, parent + ".bytes",
                            "Modified UTF-8 bytes", color, parent));
                }
            }
            case 3 -> // CONSTANT_Integer
                    model.addRegion(new PatternModel.Region(pos + 1, 4, parent + ".bytes",
                            "Integer bytes: 0x" + String.format("%08X", readU4(p, pos + 1)), color, parent));
            case 4 -> // CONSTANT_Float
                    model.addRegion(new PatternModel.Region(pos + 1, 4, parent + ".bytes",
                            "Float bytes: 0x" + String.format("%08X", readU4(p, pos + 1)), color, parent));
            case 5 -> // CONSTANT_Long
                    model.addRegion(new PatternModel.Region(pos + 1, 8, parent + ".bytes",
                            "Long bytes", color, parent));
            case 6 -> // CONSTANT_Double
                    model.addRegion(new PatternModel.Region(pos + 1, 8, parent + ".bytes",
                            "Double bytes", color, parent));
            case 7 -> // CONSTANT_Class
                    addIndexRegion(model, pos + 1, parent + ".name_index", "Name index", color, parent, p);
            case 8 -> // CONSTANT_String
                    addIndexRegion(model, pos + 1, parent + ".string_index", "String index", color, parent, p);
            case 9, 10, 11 -> { // Fieldref, Methodref, InterfaceMethodref
                addIndexRegion(model, pos + 1, parent + ".class_index", "Class index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".name_and_type_index", "Name and type index", color, parent, p);
            }
            case 12 -> { // CONSTANT_NameAndType
                addIndexRegion(model, pos + 1, parent + ".name_index", "Name index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".descriptor_index", "Descriptor index", color, parent, p);
            }
            case 15 -> { // CONSTANT_MethodHandle
                model.addRegion(new PatternModel.Region(pos + 1, 1, parent + ".reference_kind",
                        "Reference kind: " + readU1(p, pos + 1), color, parent));
                addIndexRegion(model, pos + 2, parent + ".reference_index", "Reference index", color, parent, p);
            }
            case 16 -> // CONSTANT_MethodType
                    addIndexRegion(model, pos + 1, parent + ".descriptor_index", "Descriptor index", color, parent, p);
            case 17, 18 -> { // CONSTANT_Dynamic, CONSTANT_InvokeDynamic
                addIndexRegion(model, pos + 1, parent + ".bootstrap_method_attr_index",
                        "Bootstrap method attr index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".name_and_type_index", "Name and type index", color, parent, p);
            }
            case 19 -> // CONSTANT_Module
                    addIndexRegion(model, pos + 1, parent + ".name_index", "Module name index", color, parent, p);
            case 20 -> // CONSTANT_Package
                    addIndexRegion(model, pos + 1, parent + ".name_index", "Package name index", color, parent, p);
            default -> { /* unknown tag, ignore */ }
        }
    }

    /**
     * 添加一个 2 字节索引区域到模型中 
     *
     * @param model   模式模型
     * @param address 索引起始地址
     * @param name    区域名称
     * @param label   显示标签
     * @param color   颜色
     * @param parent  父区域名称
     * @param p       数据提供者(用于读取索引值)
     */
    private static void addIndexRegion(PatternModel model, long address, String name,
                                       String label, Color color, String parent, HexDataProvider p) {
        model.addRegion(new PatternModel.Region(address, 2, name,
                label + ": " + readU2(p, address), color, parent));
    }

    /**
     * 添加一个成员(字段或方法)及其属性到模型中 
     *
     * @param model   模式模型
     * @param p       数据提供者
     * @param pos     成员起始位置
     * @param index   成员索引(字段/方法编号)
     * @param kind    类型("field" 或 "method")
     * @param color   颜色
     * @param maxSize 数据总大小(防止越界)
     * @return 解析后的下一个位置(属性之后)
     */
    private static long addMemberInfo(PatternModel model, HexDataProvider p, long pos, int index,
                                      String kind, Color color, long maxSize) {
        long start = pos;
        if (pos + 8 > maxSize) {
            return pos;
        }

        String parent = kind + "[" + index + "]";
        int attrCount = readU2(p, pos + 6);
        long afterAttributes = addAttributes(model, p, pos + 8, attrCount, maxSize,
                parent + ".attribute", color, parent + ".attributes");
        long length = afterAttributes - start;
        if (length <= 0) {
            return pos;
        }

        model.addRegion(new PatternModel.Region(start, length, parent,
                capitalize(kind) + " #" + index + " (" + attrCount + " attributes)",
                color, kind + "s"));
        model.addRegion(new PatternModel.Region(pos, 2, parent + ".access_flags",
                "Access flags: 0x" + String.format("%04X", readU2(p, pos)), color, parent));
        addIndexRegion(model, pos + 2, parent + ".name_index", "Name index", color, parent, p);
        addIndexRegion(model, pos + 4, parent + ".descriptor_index", "Descriptor index", color, parent, p);
        model.addRegion(new PatternModel.Region(pos + 6, 2, parent + ".attributes_count",
                "Attributes count: " + attrCount, color, parent));
        return afterAttributes;
    }

    /**
     * 添加一系列属性(Attributes)到模型中 
     *
     * @param model      模式模型
     * @param p          数据提供者
     * @param pos        属性起始位置
     * @param count      属性数量
     * @param maxSize    数据总大小
     * @param namePrefix 属性区域名称前缀
     * @param color      颜色
     * @param parent     父区域名称
     * @return 解析后的下一个位置
     */
    private static long addAttributes(PatternModel model, HexDataProvider p, long pos, int count,
                                      long maxSize, String namePrefix, Color color, String parent) {
        for (int i = 0; i < count; i++) {
            if (pos + 6 > maxSize) {
                break;
            }
            long start = pos;
            int nameIndex = readU2(p, pos);
            long len = readU4(p, pos + 2);
            long end = Math.min(maxSize, pos + 6 + len);
            String name = namePrefix + "[" + i + "]";
            model.addRegion(new PatternModel.Region(start, end - start, name,
                    "Attribute #" + i + " name_index=" + nameIndex + " length=" + len,
                    color, parent));
            model.addRegion(new PatternModel.Region(pos, 2, name + ".attribute_name_index",
                    "Attribute name index: " + nameIndex, color, name));
            model.addRegion(new PatternModel.Region(pos + 2, 4, name + ".attribute_length",
                    "Attribute length: " + len, color, name));
            if (len > 0 && pos + 6 < maxSize) {
                model.addRegion(new PatternModel.Region(pos + 6, end - (pos + 6),
                        name + ".info", "Attribute info bytes", color, name));
            }
            pos = end;
        }
        return pos;
    }

    // ---------- 辅助字符串工具 ----------

    /**
     * 将字符串首字母大写 
     *
     * @param value 输入字符串
     * @return 首字母大写的字符串
     */
    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * 根据主版本号返回对应的 JDK 版本名称 
     *
     * @param major 主版本号
     * @return JDK 名称,若未知则返回 {@code null}
     */
    private static String javaVersionName(int major) {
        return switch (major) {
            case 45 -> "JDK 1.1";
            case 46 -> "JDK 1.2";
            case 47 -> "JDK 1.3";
            case 48 -> "JDK 1.4";
            case 49 -> "JDK 5";
            case 50 -> "JDK 6";
            case 51 -> "JDK 7";
            case 52 -> "JDK 8";
            case 53 -> "JDK 9";
            case 54 -> "JDK 10";
            case 55 -> "JDK 11";
            case 56 -> "JDK 12";
            case 57 -> "JDK 13";
            case 58 -> "JDK 14";
            case 59 -> "JDK 15";
            case 60 -> "JDK 16";
            case 61 -> "JDK 17";
            case 62 -> "JDK 18";
            case 63 -> "JDK 19";
            case 64 -> "JDK 20";
            case 65 -> "JDK 21";
            case 66 -> "JDK 22";
            case 67 -> "JDK 23";
            default -> null;
        };
    }

    /**
     * 根据常量池标签编号返回标签名称 
     *
     * @param tag 标签值(1~20 等)
     * @return 对应的常量池类型名称(如 "CONSTANT_Utf8")
     */
    private static String constantPoolTagName(int tag) {
        return switch (tag) {
            case 1 -> "CONSTANT_Utf8";
            case 3 -> "CONSTANT_Integer";
            case 4 -> "CONSTANT_Float";
            case 5 -> "CONSTANT_Long";
            case 6 -> "CONSTANT_Double";
            case 7 -> "CONSTANT_Class";
            case 8 -> "CONSTANT_String";
            case 9 -> "CONSTANT_Fieldref";
            case 10 -> "CONSTANT_Methodref";
            case 11 -> "CONSTANT_InterfaceMethodref";
            case 12 -> "CONSTANT_NameAndType";
            case 15 -> "CONSTANT_MethodHandle";
            case 16 -> "CONSTANT_MethodType";
            case 17 -> "CONSTANT_Dynamic";
            case 18 -> "CONSTANT_InvokeDynamic";
            case 19 -> "CONSTANT_Module";
            case 20 -> "CONSTANT_Package";
            default -> "UNKNOWN(" + tag + ")";
        };
    }

    /**
     * 计算给定标签的常量池条目所占字节数 
     *
     * @param tag 条目标签
     * @param p   数据提供者(用于读取 UTF-8 长度)
     * @param pos 条目起始位置
     * @return 字节数,若数据不足或无效则返回 -1
     */
    private static int constantPoolEntrySize(int tag, HexDataProvider p, long pos) {
        return switch (tag) {
            case 1 -> { // Utf8: tag(1) + length(2) + bytes(length)
                if (pos + 3 > p.getSize()) {
                    yield -1;
                }
                byte[] b2 = new byte[2];
                p.read(pos + 1, b2, 0, 2);
                yield 3 + u2(b2);
            }
            case 3, 4 -> 5;  // Integer/Float: tag(1) + bytes(4)
            case 5, 6 -> 9;  // Long/Double: tag(1) + bytes(8)
            case 7, 8 -> 3;  // Class/String: tag(1) + index(2)
            case 9, 10, 11, 12 -> 5;  // Ref/NameAndType: tag(1) + index(2) + index(2)
            case 15 -> 4;  // MethodHandle: tag(1) + kind(1) + index(2)
            case 16 -> 3;  // MethodType: tag(1) + index(2)
            case 17, 18 -> 5;  // Dynamic/InvokeDynamic: tag(1) + index(2) + index(2)
            case 19, 20 -> 3;  // Module/Package: tag(1) + index(2)
            default -> -1;
        };
    }

    /**
     * 将访问标志(Access Flags)转换为可读的修饰符名称字符串 
     *
     * @param flags 访问标志位
     * @return 空格分隔的修饰符名称(如 "PUBLIC FINAL")
     */
    private static String accessFlagsName(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & 0x0001) != 0) {
            sb.append("PUBLIC ");
        }
        if ((flags & 0x0010) != 0) {
            sb.append("FINAL ");
        }
        if ((flags & 0x0020) != 0) {
            sb.append("SUPER ");
        }
        if ((flags & 0x0200) != 0) {
            sb.append("INTERFACE ");
        }
        if ((flags & 0x0400) != 0) {
            sb.append("ABSTRACT ");
        }
        if ((flags & 0x1000) != 0) {
            sb.append("SYNTHETIC ");
        }
        if ((flags & 0x2000) != 0) {
            sb.append("ANNOTATION ");
        }
        if ((flags & 0x4000) != 0) {
            sb.append("ENUM ");
        }
        if ((flags & 0x8000) != 0) {
            sb.append("MODULE ");
        }
        return sb.toString().trim();
    }

    // ---------- BuiltinHighlighter 实现 ----------

    @Override
    public String getName() {
        return "Java Bytecode";
    }

    @Override
    public boolean matches(HexDataProvider provider) {
        if (provider.getSize() < 4) {
            return false;
        }
        byte[] buf = new byte[4];
        provider.read(0, buf, 0, 4);
        int magic = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16)
                | ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
        return magic == MAGIC;
    }

    @Override
    public void highlight(HexDataProvider provider, PatternModel model) {
        long pos = 0;
        long size = provider.getSize();
        HexViewController ctrl = HexViewController.getInstance();

        // --- 魔数 ---
        model.addRegion(new PatternModel.Region(pos, 4, "magic",
                "Magic number 0xCAFEBABE", ctrl.getMagicColor(), null));
        pos += 4;

        // --- 次版本号 ---
        if (pos + 2 > size) {
            return;
        }
        int minor = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "minor_version",
                "Minor version: " + minor, ctrl.getVersionColor(), null));
        pos += 2;

        // --- 主版本号 ---
        if (pos + 2 > size) {
            return;
        }
        int major = readU2(provider, pos);
        String verName = javaVersionName(major);
        model.addRegion(new PatternModel.Region(pos, 2, "major_version",
                "Major version: " + major + (verName != null ? " (" + verName + ")" : ""),
                ctrl.getVersionColor(), null));
        pos += 2;

        // --- 常量池计数 ---
        if (pos + 2 > size) {
            return;
        }
        int cpCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "constant_pool_count",
                "Constant pool count: " + cpCount, ctrl.getPoolColor(), null));
        pos += 2;

        // --- 常量池条目 ---
        for (int i = 1; i < cpCount && pos < size; i++) {
            int tag = readU1(provider, pos);
            int entrySize = constantPoolEntrySize(tag, provider, pos);
            String tagName = constantPoolTagName(tag);

            if (entrySize <= 0 || pos + entrySize > size) {
                break;
            }

            model.addRegion(new PatternModel.Region(pos, entrySize, "cp[" + i + "]",
                    "Constant pool #" + i + ": " + tagName, ctrl.getPoolColor(), "constant_pool"));
            addConstantPoolEntryDetails(model, provider, pos, i, tag, ctrl);
            pos += entrySize;
            // Long/Double 占用两个索引位
            if (tag == 5 || tag == 6) {
                i++;
            }
        }

        // --- 访问标志 ---
        if (pos + 2 > size) {
            return;
        }
        int access = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "access_flags",
                "Access flags: 0x" + String.format("%04X", access) + " " + accessFlagsName(access),
                ctrl.getAccessColor(), null));
        pos += 2;

        // --- 当前类索引 ---
        if (pos + 2 > size) {
            return;
        }
        model.addRegion(new PatternModel.Region(pos, 2, "this_class",
                "This class (cp index): " + readU2(provider, pos), ctrl.getAccessColor(), null));
        pos += 2;

        // --- 父类索引 ---
        if (pos + 2 > size) {
            return;
        }
        model.addRegion(new PatternModel.Region(pos, 2, "super_class",
                "Super class (cp index): " + readU2(provider, pos), ctrl.getAccessColor(), null));
        pos += 2;

        // --- 接口计数 ---
        if (pos + 2 > size) {
            return;
        }
        int ifaceCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "interfaces_count",
                "Interfaces count: " + ifaceCount, ctrl.getInterfaceColor(), null));
        pos += 2;

        // --- 接口列表 ---
        for (int i = 0; i < ifaceCount && pos + 2 <= size; i++) {
            model.addRegion(new PatternModel.Region(pos, 2, "interface[" + i + "]",
                    "Interface #" + i + " class index: " + readU2(provider, pos),
                    ctrl.getInterfaceColor(), "interfaces"));
            pos += 2;
        }

        // --- 字段计数 ---
        if (pos + 2 > size) {
            return;
        }
        int fieldsCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "fields_count",
                "Fields count: " + fieldsCount, ctrl.getFieldColor(), null));
        pos += 2;

        // --- 字段列表 ---
        for (int i = 0; i < fieldsCount && pos + 8 <= size; i++) {
            long next = addMemberInfo(model, provider, pos, i, "field", ctrl.getFieldColor(), size);
            if (next <= pos) {
                break;
            }
            pos = next;
        }

        // --- 方法计数 ---
        if (pos + 2 > size) {
            return;
        }
        int methodsCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "methods_count",
                "Methods count: " + methodsCount, ctrl.getMethodColor(), null));
        pos += 2;

        // --- 方法列表 ---
        for (int i = 0; i < methodsCount && pos + 8 <= size; i++) {
            long next = addMemberInfo(model, provider, pos, i, "method", ctrl.getMethodColor(), size);
            if (next <= pos) {
                break;
            }
            pos = next;
        }

        // --- 类级属性(剩余数据) ---
        if (pos < size) {
            int classAttrCount = pos + 2 <= size ? readU2(provider, pos) : 0;
            if (pos + 2 <= size) {
                model.addRegion(new PatternModel.Region(pos, 2,
                        "attributes_count", "Class attributes count: " + classAttrCount,
                        ctrl.getAttributeColor(), null));
                pos += 2;
                addAttributes(model, provider, pos, classAttrCount, size,
                        "attribute", ctrl.getAttributeColor(), "attributes");
            } else {
                model.addRegion(new PatternModel.Region(pos, size - pos,
                        "trailing_bytes", "Trailing bytes", ctrl.getUnknownColor(), null));
            }
        }
    }
}