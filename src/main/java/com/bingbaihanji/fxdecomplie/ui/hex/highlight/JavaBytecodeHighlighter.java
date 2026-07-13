package com.bingbaihanji.fxdecomplie.ui.hex.highlight;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import javafx.scene.paint.Color;

/**
 * Recognizes and highlights Java .class file structure.
 *
 * Layout reference: JVM Specification §4
 *   magic (4) + minor_version (2) + major_version (2) + constant_pool + ...
 */
public class JavaBytecodeHighlighter implements BuiltinHighlighter {

    private static final int MAGIC = 0xCAFEBABE;

    private static int readU1(HexDataProvider p, long addr) {
        byte[] b = new byte[1];
        p.read(addr, b, 0, 1);
        return b[0] & 0xFF;
    }

    private static int u2(byte[] buf) {
        return ((buf[0] & 0xFF) << 8) | (buf[1] & 0xFF);
    }

    private static long u4be(byte[] buf) {
        return (((buf[0] & 0xFFL) << 24) | ((buf[1] & 0xFFL) << 16)
                | ((buf[2] & 0xFFL) << 8) | (buf[3] & 0xFFL)) & 0xFFFF_FFFFL;
    }

    // ===== Helpers =====

    private static int readU2(HexDataProvider p, long addr) {
        byte[] b = new byte[2];
        p.read(addr, b, 0, 2);
        return u2(b);
    }

    private static long readU4(HexDataProvider p, long addr) {
        byte[] b = new byte[4];
        p.read(addr, b, 0, 4);
        return u4be(b);
    }

    private static void addConstantPoolEntryDetails(PatternModel model, HexDataProvider p,
                                                    long pos, int index, int tag,
                                                    HexViewController ctrl) {
        Color color = ctrl.getPoolColor();
        String parent = "cp[" + index + "]";
        model.addRegion(new PatternModel.Region(pos, 1, parent + ".tag",
                "Tag: " + constantPoolTagName(tag), color, parent));
        switch (tag) {
            case 1 -> {
                int length = readU2(p, pos + 1);
                model.addRegion(new PatternModel.Region(pos + 1, 2, parent + ".length",
                        "UTF-8 byte length: " + length, color, parent));
                if (length > 0) {
                    model.addRegion(new PatternModel.Region(pos + 3, length, parent + ".bytes",
                            "Modified UTF-8 bytes", color, parent));
                }
            }
            case 3 -> model.addRegion(new PatternModel.Region(pos + 1, 4, parent + ".bytes",
                    "Integer bytes: 0x" + String.format("%08X", readU4(p, pos + 1)), color, parent));
            case 4 -> model.addRegion(new PatternModel.Region(pos + 1, 4, parent + ".bytes",
                    "Float bytes: 0x" + String.format("%08X", readU4(p, pos + 1)), color, parent));
            case 5 -> model.addRegion(new PatternModel.Region(pos + 1, 8, parent + ".bytes",
                    "Long bytes", color, parent));
            case 6 -> model.addRegion(new PatternModel.Region(pos + 1, 8, parent + ".bytes",
                    "Double bytes", color, parent));
            case 7 -> addIndexRegion(model, pos + 1, parent + ".name_index", "Name index", color, parent, p);
            case 8 -> addIndexRegion(model, pos + 1, parent + ".string_index", "String index", color, parent, p);
            case 9, 10, 11 -> {
                addIndexRegion(model, pos + 1, parent + ".class_index", "Class index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".name_and_type_index", "Name and type index", color, parent, p);
            }
            case 12 -> {
                addIndexRegion(model, pos + 1, parent + ".name_index", "Name index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".descriptor_index", "Descriptor index", color, parent, p);
            }
            case 15 -> {
                model.addRegion(new PatternModel.Region(pos + 1, 1, parent + ".reference_kind",
                        "Reference kind: " + readU1(p, pos + 1), color, parent));
                addIndexRegion(model, pos + 2, parent + ".reference_index", "Reference index", color, parent, p);
            }
            case 16 ->
                    addIndexRegion(model, pos + 1, parent + ".descriptor_index", "Descriptor index", color, parent, p);
            case 17, 18 -> {
                addIndexRegion(model, pos + 1, parent + ".bootstrap_method_attr_index",
                        "Bootstrap method attr index", color, parent, p);
                addIndexRegion(model, pos + 3, parent + ".name_and_type_index", "Name and type index", color, parent, p);
            }
            case 19 -> addIndexRegion(model, pos + 1, parent + ".name_index", "Module name index", color, parent, p);
            case 20 -> addIndexRegion(model, pos + 1, parent + ".name_index", "Package name index", color, parent, p);
            default -> {
            }
        }
    }

    private static void addIndexRegion(PatternModel model, long address, String name,
                                       String label, Color color, String parent, HexDataProvider p) {
        model.addRegion(new PatternModel.Region(address, 2, name,
                label + ": " + readU2(p, address), color, parent));
    }

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

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

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
     * Return the byte size of a constant pool entry given its tag.
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

        model.addRegion(new PatternModel.Region(pos, 4, "magic",
                "Magic number 0xCAFEBABE", ctrl.getMagicColor(), null));
        pos += 4;

        if (pos + 2 > size) {
            return;
        }
        int minor = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "minor_version",
                "Minor version: " + minor, ctrl.getVersionColor(), null));
        pos += 2;

        if (pos + 2 > size) {
            return;
        }
        int major = readU2(provider, pos);
        String verName = javaVersionName(major);
        model.addRegion(new PatternModel.Region(pos, 2, "major_version",
                "Major version: " + major + (verName != null ? " (" + verName + ")" : ""),
                ctrl.getVersionColor(), null));
        pos += 2;

        if (pos + 2 > size) {
            return;
        }
        int cpCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "constant_pool_count",
                "Constant pool count: " + cpCount, ctrl.getPoolColor(), null));
        pos += 2;

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
            if (tag == 5 || tag == 6) {
                i++;
            }
        }

        if (pos + 2 > size) {
            return;
        }
        int access = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "access_flags",
                "Access flags: 0x" + String.format("%04X", access) + " " + accessFlagsName(access),
                ctrl.getAccessColor(), null));
        pos += 2;

        if (pos + 2 > size) {
            return;
        }
        model.addRegion(new PatternModel.Region(pos, 2, "this_class",
                "This class (cp index): " + readU2(provider, pos), ctrl.getAccessColor(), null));
        pos += 2;

        if (pos + 2 > size) {
            return;
        }
        model.addRegion(new PatternModel.Region(pos, 2, "super_class",
                "Super class (cp index): " + readU2(provider, pos), ctrl.getAccessColor(), null));
        pos += 2;

        if (pos + 2 > size) {
            return;
        }
        int ifaceCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "interfaces_count",
                "Interfaces count: " + ifaceCount, ctrl.getInterfaceColor(), null));
        pos += 2;

        for (int i = 0; i < ifaceCount && pos + 2 <= size; i++) {
            model.addRegion(new PatternModel.Region(pos, 2, "interface[" + i + "]",
                    "Interface #" + i + " class index: " + readU2(provider, pos),
                    ctrl.getInterfaceColor(), "interfaces"));
            pos += 2;
        }

        if (pos + 2 > size) {
            return;
        }
        int fieldsCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "fields_count",
                "Fields count: " + fieldsCount, ctrl.getFieldColor(), null));
        pos += 2;

        for (int i = 0; i < fieldsCount && pos + 8 <= size; i++) {
            long next = addMemberInfo(model, provider, pos, i, "field", ctrl.getFieldColor(), size);
            if (next <= pos) {
                break;
            }
            pos = next;
        }

        if (pos + 2 > size) {
            return;
        }
        int methodsCount = readU2(provider, pos);
        model.addRegion(new PatternModel.Region(pos, 2, "methods_count",
                "Methods count: " + methodsCount, ctrl.getMethodColor(), null));
        pos += 2;

        for (int i = 0; i < methodsCount && pos + 8 <= size; i++) {
            long next = addMemberInfo(model, provider, pos, i, "method", ctrl.getMethodColor(), size);
            if (next <= pos) {
                break;
            }
            pos = next;
        }

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
