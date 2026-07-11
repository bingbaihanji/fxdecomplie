package com.bingbaihanji.fxdecomplie.ui.code;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义 ClassVisitor + 原始字节解析,生成 jadx 风格的字节码文本
 *
 * <p>包含：magic/version 头、访问标志(hex 值)、常量池逐条列出(含引用注释)、
 * 每个方法的 descriptor/flags/Code 属性(stack/locals/args_size)、
 * 指令 hex 偏移 + 原始字节 + | + 偏移 + 操作码</p>
 *
 * <p>两阶段处理：阶段1 从 raw bytes 解析常量池和 Code 属性文件偏移 
 * 阶段2 用 ASM ClassVisitor 遍历类结构,MethodVisitor 中输出 hex+指令</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
final class BytecodeTextBuilder extends ClassVisitor {

    private static final Logger log = LoggerFactory.getLogger(BytecodeTextBuilder.class);

    private static final String INDENT = "    ";
    private static final String INSN_INDENT = INDENT + INDENT;

    private final byte[] raw;
    private final StringBuilder sb = new StringBuilder(8192);
    private final Map<String, Integer> methodCodeOffsets;
    private String className;
    private String internalName;

    BytecodeTextBuilder(byte[] raw) {
        super(Opcodes.ASM9);
        this.raw = raw.clone();
        this.methodCodeOffsets = scanCodeOffsets(this.raw);
    }

    // -- 阶段1：原始字节解析工具 --

    // ==================== 常量池 ====================

    /** 从 raw bytes 解析并格式化常量池,每行一个条目带注释 */
    static String formatConstantPool(byte[] raw) {
        if (raw == null || raw.length < 10) {
            return "";
        }
        try {
            int count = readU2(raw, 8);
            // 防御：count 不能超过文件大小(每个条目最少1字节tag)
            if (count < 1 || count > raw.length) {
                return "Constant pool: <invalid count=" + count + ">\n\n";
            }
            StringBuilder cp = new StringBuilder(count * 80);
            cp.append("Constant pool:\n");
            int off = 10;
            for (int i = 1; i < count && off < raw.length; i++) {
                int tag = raw[off++] & 0xFF;
                cp.append(String.format("   #%-3d = ", i));
                switch (tag) {
                    case 1 -> { // Utf8
                        int len = readU2(raw, off);
                        off += 2;
                        String val = new String(raw, off, len, java.nio.charset.StandardCharsets.UTF_8);
                        off += len;
                        cp.append("Utf8           \"").append(escape(val)).append('"');
                    }
                    case 3 -> { // Integer
                        int val = readS4(raw, off);
                        off += 4;
                        cp.append("Integer        ").append(val);
                    }
                    case 4 -> { // Float
                        float val = Float.intBitsToFloat(readS4(raw, off));
                        off += 4;
                        cp.append("Float          ").append(val).append('f');
                    }
                    case 5 -> { // Long
                        long val = readS8(raw, off);
                        off += 8;
                        cp.append("Long           ").append(val).append('L');
                        i++; // Long/Double takes 2 CP slots
                    }
                    case 6 -> { // Double
                        double val = Double.longBitsToDouble(readS8(raw, off));
                        off += 8;
                        cp.append("Double         ").append(val);
                        i++;
                    }
                    case 7 -> { // Class
                        int ni = readU2(raw, off);
                        off += 2;
                        cp.append("Class          #").append(ni)
                                .append("  // ").append(cpUtf8(raw, ni));
                    }
                    case 8 -> { // String
                        int ni = readU2(raw, off);
                        off += 2;
                        cp.append("String         #").append(ni)
                                .append("  // \"").append(escape(cpUtf8(raw, ni))).append('"');
                    }
                    case 9 -> { // Fieldref
                        int ci = readU2(raw, off);
                        int nti = readU2(raw, off + 2);
                        off += 4;
                        cp.append("Fieldref       #").append(ci).append(".#").append(nti)
                                .append("  // ").append(cpRef(raw, ci, nti));
                    }
                    case 10 -> { // Methodref
                        int ci = readU2(raw, off);
                        int nti = readU2(raw, off + 2);
                        off += 4;
                        cp.append("Methodref      #").append(ci).append(".#").append(nti)
                                .append("  // ").append(cpRef(raw, ci, nti));
                    }
                    case 11 -> { // InterfaceMethodref
                        int ci = readU2(raw, off);
                        int nti = readU2(raw, off + 2);
                        off += 4;
                        cp.append("InterfaceMethodref #").append(ci).append(".#").append(nti)
                                .append("  // ").append(cpRef(raw, ci, nti));
                    }
                    case 12 -> { // NameAndType
                        int ni = readU2(raw, off);
                        int di = readU2(raw, off + 2);
                        off += 4;
                        cp.append("NameAndType    #").append(ni).append(":#").append(di)
                                .append("  // ").append(cpUtf8(raw, ni))
                                .append(':').append(cpUtf8(raw, di));
                    }
                    case 15 -> { // MethodHandle
                        int refKind = raw[off] & 0xFF;
                        int idx = readU2(raw, off + 1);
                        off += 3;
                        cp.append("MethodHandle   kind=").append(refKind)
                                .append(" #").append(idx);
                    }
                    case 16 -> { // MethodType
                        int di = readU2(raw, off);
                        off += 2;
                        cp.append("MethodType     #").append(di)
                                .append("  // ").append(cpUtf8(raw, di));
                    }
                    case 17 -> { // Dynamic
                        int bsm = readU2(raw, off);
                        int nti = readU2(raw, off + 2);
                        off += 4;
                        cp.append("Dynamic        #").append(bsm).append(":#").append(nti)
                                .append("  // ").append(cpRef(raw, bsm, nti));
                    }
                    case 18 -> { // InvokeDynamic
                        int bsm = readU2(raw, off);
                        int nti = readU2(raw, off + 2);
                        off += 4;
                        cp.append("InvokeDynamic  #").append(bsm).append(":#").append(nti)
                                .append("  // ").append(cpRef(raw, bsm, nti));
                    }
                    case 19 -> { // Module
                        int ni = readU2(raw, off);
                        off += 2;
                        cp.append("Module         #").append(ni)
                                .append("  // ").append(cpUtf8(raw, ni));
                    }
                    case 20 -> { // Package
                        int ni = readU2(raw, off);
                        off += 2;
                        cp.append("Package        #").append(ni)
                                .append("  // ").append(cpUtf8(raw, ni));
                    }
                    default -> cp.append("unknown tag=").append(tag);
                }
                cp.append('\n');
            }
            cp.append('\n');
            return cp.toString();
        } catch (Exception e) {
            return "Constant pool: <parse error: " + e.getMessage() + ">\n\n";
        }
    }

    /** 格式化类头信息(magic、版本号、访问标志、this_class、super_class) */
    static String formatClassHeader(byte[] raw, String className, String superName,
                                    String[] interfaces) {
        if (raw == null || raw.length < 10) {
            return "";
        }
        StringBuilder h = new StringBuilder(512);

        // Class 路径标记
        h.append("###### 字节码: ").append(className != null ? className.replace('/', '.') : "?").append('\n');
        h.append('\n');

        // magic + 版本
        int magic = readS4(raw, 0);
        int minor = readU2(raw, 4);
        int major = readU2(raw, 6);
        h.append(String.format("magic: %08x\n", magic));
        h.append("minor version: ").append(minor).append('\n');
        h.append("major version: ").append(major).append("  # ").append(javaVersionName(major)).append('\n');

        // 类体起始偏移(跳过常量池后),布局：access_flags(2) + this_class(2) + super_class(2) + if_count(2)
        int bodyOff = skipToClassBody(raw);
        // 访问标志(+0)
        int access = readU2(raw, bodyOff);
        h.append(String.format("flags: (0x%04x) %s\n", access, formatAccessFlags(access)));

        // this_class(+2)
        int thisIdx = readU2(raw, bodyOff + 2);
        String thisName = cpUtf8(raw, thisIdx);
        h.append(String.format("this_class: #%d  // %s\n", thisIdx, thisName));

        // super_class(+4)
        int superIdx = readU2(raw, bodyOff + 4);
        String superNameResolved = superIdx > 0 ? cpUtf8(raw, superIdx) : "java/lang/Object";
        h.append(String.format("super_class: #%d  // %s\n", superIdx, superNameResolved));

        // interfaces_count(+6),接口列表从 +8 开始
        int ifCount = readU2(raw, bodyOff + 6);
        h.append("interfaces: ").append(ifCount);
        if (ifCount > 0) {
            h.append(" [");
            int ifOff = bodyOff + 8;
            for (int i = 0; i < ifCount; i++) {
                if (i > 0) {
                    h.append(", ");
                }
                int ifIdx = readU2(raw, ifOff + i * 2);
                h.append('#').append(ifIdx).append(" // ").append(cpUtf8(raw, ifIdx));
            }
            h.append(']');
        }
        h.append('\n').append('\n');
        return h.toString();
    }

    /** 解析类文件属性名(SourceFile、Signature 等) */
    static String parseClassAttributes(byte[] raw) {
        if (raw == null || raw.length < 10) {
            return "";
        }
        StringBuilder attrs = new StringBuilder();
        try {
            int off = skipToClassBody(raw) + 6; // skip access, this, super
            int ifCount = readU2(raw, off);
            off += 2 + ifCount * 2; // skip interfaces

            // fields
            int fieldsCount = readU2(raw, off);
            off += 2;
            for (int i = 0; i < fieldsCount; i++) {
                off = skipAttributes(raw, off + 6);
            }

            // methods
            int methodsCount = readU2(raw, off);
            off += 2;
            for (int i = 0; i < methodsCount; i++) {
                off = skipAttributes(raw, off + 6);
            }

            // class attributes
            int attrsCount = readU2(raw, off);
            off += 2;
            for (int a = 0; a < attrsCount; a++) {
                int nameIdx = readU2(raw, off);
                int len = readS4(raw, off + 2);
                String name = cpUtf8(raw, nameIdx);
                attrs.append("  ").append(name);
                switch (name) {
                    case "SourceFile" -> {
                        int srcIdx = readU2(raw, off + 6);
                        attrs.append(": #").append(srcIdx).append(" // \"")
                                .append(escape(cpUtf8(raw, srcIdx))).append('"');
                    }
                    case "Signature" -> {
                        int sigIdx = readU2(raw, off + 6);
                        attrs.append(": #").append(sigIdx).append(" // \"")
                                .append(escape(cpUtf8(raw, sigIdx))).append('"');
                    }
                    case "InnerClasses" -> {
                        int count = readU2(raw, off + 6);
                        attrs.append(": ").append(count).append(" entries");
                    }
                }
                attrs.append('\n');
                off += 6 + len;
            }
        } catch (Exception e) {
            attrs.append("  <parse error: ").append(e.getMessage()).append(">\n");
        }
        return attrs.toString();
    }

    // ==================== Code 偏移扫描 ====================

    /** 扫描所有方法的 Code 属性文件偏移,返回 methodKey → code[] 的文件偏移 */
    static Map<String, Integer> scanCodeOffsets(byte[] raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.length < 10) {
            return map;
        }
        try {
            int off = skipToClassBody(raw) + 6; // skip access, this, super
            int ifCount = readU2(raw, off);
            off += 2 + ifCount * 2; // skip interfaces

            // fields
            int fieldsCount = readU2(raw, off);
            off += 2;
            for (int i = 0; i < fieldsCount; i++) {
                off = skipAttributes(raw, off + 6);
            }

            // methods
            int methodsCount = readU2(raw, off);
            off += 2;
            for (int i = 0; i < methodsCount; i++) {
                int nameIndex = readU2(raw, off + 2);
                int descIndex = readU2(raw, off + 4);
                String methodName = cpUtf8(raw, nameIndex);
                String methodDesc = cpUtf8(raw, descIndex);
                String key = methodName + methodDesc;
                off += 6;
                int attrsCount = readU2(raw, off);
                off += 2;
                for (int a = 0; a < attrsCount; a++) {
                    int attrNameIdx = readU2(raw, off);
                    int attrLen = readS4(raw, off + 2);
                    String attrName = cpUtf8(raw, attrNameIdx);
                    if ("Code".equals(attrName) && !map.containsKey(key)) {
                        // code[] starts at off + 6 (attr header) + 2(max_stack) + 2(max_locals)
                        // + 4(code_length) = off + 14
                        map.put(key, off + 14);
                    }
                    off += 6 + attrLen;
                }
            }
        } catch (Exception ignored) {
            log.debug("扫描 Code 属性偏移失败", ignored);
        }
        return map;
    }

    /** 获取方法的 Code 属性信息(stack、locals、code_length) */
    static String formatCodeHeader(byte[] raw, String methodKey, boolean isStatic) {
        Map<String, Integer> offsets = scanCodeOffsets(raw);
        Integer codeOff = offsets.get(methodKey);
        if (codeOff == null) {
            return "";
        }
        // codeOff = file offset of code[] start
        // codeOff - 14 = Code attribute start
        // codeOff - 14 + 6 = attribute header end
        int attrOff = codeOff - 14;
        int maxStack = readU2(raw, attrOff + 6);
        int maxLocals = readU2(raw, attrOff + 8);
        int codeLen = readS4(raw, attrOff + 10);
        int argsSize = computeArgsSize(methodKey, isStatic);
        String info = String.format("      stack=%d, locals=%d, args_size=%d\n",
                maxStack, maxLocals, argsSize) +
                String.format("      code_length=%d (0x%x)\n", codeLen, codeLen);
        return info;
    }

    /**
     * 根据方法描述符计算 args_size(参数占用的寄存器字数,long/double 占2,实例方法 +1 给 this)
     *
     * @param methodKey 方法名 + 描述符(如 "foo(I)V")
     * @param isStatic  方法是否为 static(ACC_STATIC 标志)
     */
    private static int computeArgsSize(String methodKey, boolean isStatic) {
        // methodKey = name + desc
        // 从 methodKey 中提取描述符部分
        int paren = methodKey.indexOf('(');
        if (paren < 0) {
            return isStatic ? 0 : 1;
        }
        String desc = methodKey.substring(paren);
        int size = 0;
        boolean inArray = false;
        boolean inObject = false;
        for (int i = 1; i < desc.length() && desc.charAt(i) != ')'; i++) {
            char c = desc.charAt(i);
            if (inArray) {
                if (c == '[') {
                    // still array
                } else if (c == 'L') {
                    inObject = true;
                    inArray = false;
                } else {
                    size++;
                    inArray = false;
                }
                continue;
            }
            if (inObject) {
                if (c == ';') {
                    size++;
                    inObject = false;
                }
                continue;
            }
            switch (c) {
                case 'J', 'D' -> size += 2; // long/double = 2 words
                case '[' -> inArray = true;
                case 'L' -> inObject = true;
                default -> size++; // B, C, D, F, I, S, Z = 1 word
            }
        }
        // 静态方法没有 this 参数
        if (!isStatic) {
            size++; // 实例方法/构造器的 this
        }
        return Math.max(isStatic ? 0 : 1, size);
    }

    // -- 字节操作工具 --

    /**
     * 从字节数组中读取无符号 2 字节整数(大端序)
     *
     * @param b   字节数组
     * @param off 起始偏移
     * @return 无符号 16 位整数值,越界返回 0
     */
    static int readU2(byte[] b, int off) {
        if (off < 0 || off + 1 >= b.length) {
            return 0;
        }
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static int readS4(byte[] b, int off) {
        if (off < 0 || off + 3 >= b.length) {
            return 0;
        }
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static long readS8(byte[] b, int off) {
        if (off < 0 || off + 7 >= b.length) {
            return 0;
        }
        return ((long) (b[off] & 0xFF) << 56) | ((long) (b[off + 1] & 0xFF) << 48)
                | ((long) (b[off + 2] & 0xFF) << 40) | ((long) (b[off + 3] & 0xFF) << 32)
                | ((long) (b[off + 4] & 0xFF) << 24) | ((long) (b[off + 5] & 0xFF) << 16)
                | ((long) (b[off + 6] & 0xFF) << 8) | (long) (b[off + 7] & 0xFF);
    }

    /** 常量池字节数(不含 tag 字节,用于跳过常量池) */
    private static int cpByteSize(byte[] raw) {
        int count = readU2(raw, 8);
        if (count < 1 || count > raw.length) {
            return 0;
        }
        int off = 10;
        for (int i = 1; i < count && off < raw.length; i++) {
            int tag = raw[off++] & 0xFF;
            off += cpEntryDataSize(tag, raw, off);
            if (tag == 5 || tag == 6) {
                i++;
            }
        }
        return off - 10;
    }

    /** 跳转到类体(跳过常量池后的偏移) */
    private static int skipToClassBody(byte[] raw) {
        return 10 + cpByteSize(raw);
    }

    /** 常量池条目数据部分大小(不含 tag) */
    private static int cpEntryDataSize(int tag, byte[] raw, int off) {
        return switch (tag) {
            case 1 -> {
                int len = readU2(raw, off);
                yield 2 + len;
            }
            case 3, 4 -> 4;
            case 5, 6 -> 8;
            case 7, 8 -> 2;
            case 9, 10, 11, 12, 17, 18 -> 4;
            case 15 -> 3;
            case 16 -> 2;
            case 19, 20 -> 2;
            default -> 0;
        };
    }

    /** 跳过属性表 */
    private static int skipAttributes(byte[] raw, int off) {
        int count = readU2(raw, off);
        off += 2;
        for (int a = 0; a < count; a++) {
            int len = readS4(raw, off + 2);
            off += 6 + len;
        }
        return off;
    }

    /** 获取 index 处的 UTF8 常量池内容 */
    static String cpUtf8(byte[] raw, int index) {
        if (index < 1 || raw == null || raw.length < 10) {
            return "?";
        }
        // 防御：index 不应超过可用的常量池条目数
        if (index > raw.length) {
            return "?";
        }
        try {
            int off = 10;
            int cpCount = readU2(raw, 8);
            for (int i = 1; i < index && i < cpCount && off < raw.length; i++) {
                int tag = raw[off++] & 0xFF;
                off += cpEntryDataSize(tag, raw, off);
                if (tag == 5 || tag == 6) {
                    i++;
                }
            }
            if (off >= raw.length) {
                return "?";
            }
            int tag = raw[off++] & 0xFF;
            if (tag == 1) {
                int len = readU2(raw, off);
                if (off + 2 + len > raw.length) {
                    return "?";
                } // 防御：UTF8内容不超出数组
                return new String(raw, off + 2, len, java.nio.charset.StandardCharsets.UTF_8);
            }
            return "?";
        } catch (Exception e) {
            return "?";
        }
    }

    /** 获取 Methodref/Fieldref 的格式化引用字符串 */
    private static String cpRef(byte[] raw, int classIdx, int natIdx) {
        String cls = cpUtf8(raw, classIdx);
        String nat = cpUtf8(raw, natIdx);
        if ("?".equals(nat)) {
            return cls + ".?";
        }
        int colon = nat.indexOf(':');
        String namePart = colon >= 0 ? nat.substring(0, colon) : nat;
        String descPart = colon >= 0 ? nat.substring(colon + 1) : "";
        return cls + "." + namePart + ":" + descPart;
    }

    /** 指令大小 */
    static int sizeOf(int opcode) {
        return switch (opcode) {
            case Opcodes.ALOAD, Opcodes.ASTORE, Opcodes.ILOAD, Opcodes.ISTORE,
                 Opcodes.FLOAD, Opcodes.FSTORE, Opcodes.DLOAD, Opcodes.DSTORE,
                 Opcodes.LLOAD, Opcodes.LSTORE, Opcodes.RET -> 2;
            case Opcodes.BIPUSH, Opcodes.NEWARRAY -> 2;
            case Opcodes.SIPUSH -> 3;
            case Opcodes.LDC -> 2;
            case 19, 20 -> 3; // LDC_W, LDC2_W
            case Opcodes.IINC -> 3;
            case Opcodes.GOTO, Opcodes.JSR -> 3;
            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE,
                 Opcodes.IFGT, Opcodes.IFLE, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE,
                 Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                 Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
                 Opcodes.IFNULL, Opcodes.IFNONNULL -> 3;
            case 200, 201 -> 5; // GOTO_W, JSR_W
            case Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD,
                 Opcodes.PUTFIELD -> 3;
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC -> 3;
            case Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC -> 5;
            case Opcodes.NEW, Opcodes.ANEWARRAY, Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> 3;
            case Opcodes.MULTIANEWARRAY -> 4;
            case 196 -> 1; // WIDE — 前缀本身占1字节,实际大小由 actualSize 计算
            default -> 1;
        };
    }

    /**
     * 获取指令在字节码中的实际大小(含 WIDE 前缀)
     *
     * @param raw  原始字节码
     * @param offset 当前指令在 raw 中的偏移
     * @return 指令实际占用字节数
     */
    static int actualSize(byte[] raw, int offset) {
        if (offset < 0 || offset >= raw.length) {
            return 1;
        }
        int b = raw[offset] & 0xFF;
        if (b != 0xC4) {
            return sizeOf(b);
        }
        // WIDE 前缀：边界检查防止截断字节码
        if (offset + 1 >= raw.length) {
            return 1; // 截断的 WIDE,仅消耗前缀字节
        }
        int next = raw[offset + 1] & 0xFF;
        if (next == Opcodes.IINC) {
            return 6; // WIDE(1) + IINC(1) + index(2) + const(2)
        }
        return 4; // WIDE(1) + opcode(1) + wideIndex(2)
    }

    /** 转义特殊字符 */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 展开访问标志为 ACC_XXX | ... 格式 */
    static String formatAccessFlags(int access) {
        StringBuilder a = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            a.append("ACC_PUBLIC | ");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            a.append("ACC_PRIVATE | ");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            a.append("ACC_PROTECTED | ");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            a.append("ACC_STATIC | ");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            a.append("ACC_FINAL | ");
        }
        if ((access & Opcodes.ACC_SUPER) != 0) {
            a.append("ACC_SUPER | ");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            a.append("ACC_SYNCHRONIZED | ");
        }
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
            a.append("ACC_VOLATILE | ");
        }
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            a.append("ACC_BRIDGE | ");
        }
        if ((access & Opcodes.ACC_VARARGS) != 0) {
            a.append("ACC_VARARGS | ");
        }
        if ((access & Opcodes.ACC_TRANSIENT) != 0) {
            a.append("ACC_TRANSIENT | ");
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            a.append("ACC_NATIVE | ");
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            a.append("ACC_INTERFACE | ");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            a.append("ACC_ABSTRACT | ");
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            a.append("ACC_STRICT | ");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            a.append("ACC_SYNTHETIC | ");
        }
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            a.append("ACC_ANNOTATION | ");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            a.append("ACC_ENUM | ");
        }
        if ((access & Opcodes.ACC_MODULE) != 0) {
            a.append("ACC_MODULE | ");
        }
        if (!a.isEmpty()) {
            a.setLength(a.length() - 3); // 去掉末尾 " | "
        }
        return a.toString();
    }

    /** 获取操作码助记符(小写) */
    static String opcodeName(int opcode) {
        if (opcode < 0 || opcode >= org.objectweb.asm.util.Printer.OPCODES.length) {
            return "unknown_" + opcode;
        }
        String name = org.objectweb.asm.util.Printer.OPCODES[opcode];
        return name != null ? name.toLowerCase() : "unknown_" + opcode;
    }

    /** Java 版本名称映射 */
    private static String javaVersionName(int major) {
        return switch (major) {
            case 69 -> "Java 25";
            case 68 -> "Java 24";
            case 67 -> "Java 23";
            case 66 -> "Java 22";
            case 65 -> "Java 21";
            case 64 -> "Java 20";
            case 63 -> "Java 19";
            case 62 -> "Java 18";
            case 61 -> "Java 17";
            case 60 -> "Java 16";
            case 59 -> "Java 15";
            case 58 -> "Java 14";
            case 57 -> "Java 13";
            case 56 -> "Java 12";
            case 55 -> "Java 11";
            case 54 -> "Java 10";
            case 53 -> "Java 9";
            case 52 -> "Java 8";
            default -> major > 69 ? "Java " + (major - 44) : "";
        };
    }

    // ==================== 阶段2：ASM Visitor ====================

    /** 访问类头信息：输出版本号、访问标志、常量池、字段/方法计数等 */
    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.className = name.replace('/', '.');
        this.internalName = name;

        // 类头(magic、版本、标志、this_class、super_class)
        sb.append(formatClassHeader(raw, name, superName, interfaces));

        // 类属性(SourceFile 等)
        sb.append("Attributes:\n");
        sb.append(parseClassAttributes(raw));
        sb.append('\n');

        // 常量池
        sb.append(formatConstantPool(raw));

        // 字段计数 — 类体布局: access_flags(2) + this_class(2) + super_class(2) + if_count(2) + interfaces[if_count](2*if_count)
        int bodyOff = skipToClassBody(raw);
        int ifCount = readU2(raw, bodyOff + 6);
        int fieldsCount = readU2(raw, bodyOff + 8 + ifCount * 2);
        sb.append("Fields count: ").append(fieldsCount).append('\n');

        // 方法计数 — 位于字段之后
        int methodsOff = bodyOff + 8 + ifCount * 2 + 2; // +2 for fields_count itself
        // 跳过字段
        for (int i = 0; i < fieldsCount; i++) {
            methodsOff = skipAttributes(raw, methodsOff + 6);
        }
        int methodsCount = readU2(raw, methodsOff);
        sb.append("Methods count: ").append(methodsCount).append('\n').append('\n');
    }

    @Override
    public void visitSource(String source, String debug) {
        // 已通过 parseClassAttributes 处理,这里不再重复输出
    }

    /** 访问字段：输出字段签名、访问标志及可选初始值 */
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        String typeStr = SmaliTextBuilder.formatTypeReadable(Type.getType(descriptor));
        sb.append(INDENT).append(".field ").append(SmaliTextBuilder.formatAccess(access, false))
                .append(' ').append(name).append(':').append(typeStr);
        if (value != null) {
            sb.append(" = ").append(SmaliTextBuilder.formatValue(value));
        }
        sb.append('\n');
        sb.append(INDENT).append(INDENT)
                .append(String.format("flags: (0x%04x) %s\n", access, formatAccessFlags(access)));
        if (signature != null) {
            sb.append(INDENT).append(INDENT).append("Signature: ").append(signature).append('\n');
        }
        sb.append('\n');
        return null;
    }

    /** 访问方法：输出方法签名、描述符、访问标志及 Code 属性字节码指令 */
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        String key = name + descriptor;
        Integer codeOff = methodCodeOffsets.get(key);

        // 方法签名
        String accessStr = SmaliTextBuilder.formatAccess(access, false);
        sb.append(INDENT).append(".method");
        if (!accessStr.isEmpty()) {
            sb.append(' ').append(accessStr);
        }
        sb.append(' ').append(name).append('(');
        Type[] argTypes = Type.getArgumentTypes(descriptor);
        for (int i = 0; i < argTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(SmaliTextBuilder.formatTypeReadable(argTypes[i]));
        }
        sb.append(')').append(SmaliTextBuilder.formatTypeReadable(Type.getReturnType(descriptor)));
        sb.append('\n');

        // 描述符
        sb.append(INDENT).append(INDENT).append("descriptor: ").append(descriptor).append('\n');

        // 访问标志
        sb.append(INDENT).append(INDENT)
                .append(String.format("flags: (0x%04x) %s\n", access, formatAccessFlags(access)));

        // 签名
        if (signature != null) {
            sb.append(INDENT).append(INDENT).append("Signature: ").append(signature).append('\n');
        }

        // 异常
        if (exceptions != null && exceptions.length > 0) {
            sb.append(INDENT).append(INDENT).append("Exceptions:\n");
            for (String ex : exceptions) {
                sb.append(INDENT).append(INDENT).append(INDENT).append(ex).append('\n');
            }
        }

        if (codeOff == null) {
            // 抽象/native 方法
            sb.append(INDENT).append(".end method\n\n");
            return null;
        }

        // Code 属性
        sb.append(INDENT).append(INDENT).append("Code:\n");
        sb.append(formatCodeHeader(raw, key, (access & Opcodes.ACC_STATIC) != 0));
        sb.append(INDENT).append(INDENT).append("  bytecode:\n");

        return new BytecodeMethodWriter(access, name, descriptor, raw, codeOff);
    }

    /** 构建最终文本 */
    String build() {
        return sb.toString();
    }

    // ==================== 方法级字节码 Writer ====================

    /**
     * 方法级字节码写入器,逐条输出 hex 偏移 + 原始字节 + 操作码助记符
     *
     * <p>通过 ASM MethodVisitor 回调遍历每条指令,从原始字节数组中
     * 读取对应偏移的 hex 数据并格式化输出</p>
     */
    private class BytecodeMethodWriter extends MethodVisitor {
        private final byte[] raw;
        private final int codeOffset;
        private int pc;

        BytecodeMethodWriter(int access, String name, String desc,
                             byte[] raw, int codeOffset) {
            super(Opcodes.ASM9);
            this.raw = raw;
            this.codeOffset = codeOffset;
        }

        private static int readS2(byte[] b, int off) {
            if (off < 0 || off + 1 >= b.length) {
                return 0;
            }
            return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
        }

        /** 方法体开始,重置 PC 计数器 */
        @Override
        public void visitCode() {
            this.pc = 0;
        }

        /** 输出行号调试信息 */
        @Override
        public void visitLineNumber(int line, Label start) {
            sb.append(INSN_INDENT)
                    .append(String.format("    ; .line %d\n", line));
        }

        /**
         * 输出局部变量调试信息
         *
         * @param name       变量名
         * @param descriptor 类型描述符
         * @param signature  泛型签名(可能为 null)
         * @param start      作用域起始标签
         * @param end        作用域结束标签
         * @param index      变量在局部变量表中的槽位索引
         */
        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (name == null) {
                return;
            }
            String typeStr = SmaliTextBuilder.formatTypeReadable(Type.getType(descriptor));
            sb.append(INSN_INDENT)
                    .append(String.format("    ; .local p%d, \"%s\":L%s;\n", index, name, typeStr));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 已在 formatCodeHeader 中输出
        }

        // ==================== 指令访问 ====================

        /** 方法体结束标记 */
        @Override
        public void visitEnd() {
            sb.append(INDENT).append(".end method\n\n");
        }

        /** 输出零操作数指令(如 return、dup、aconst_null 等) */
        @Override
        public void visitInsn(int opcode) {
            appendInsnHex(opcode, 1);
            sb.append(' ').append(opcodeName(opcode)).append('\n');
            pc += 1;
        }

        /** 输出单操作数整数指令(如 bipush、sipush、newarray) */
        @Override
        public void visitIntInsn(int opcode, int operand) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            sb.append(' ').append(opcodeName(opcode)).append(' ').append(operand).append('\n');
            pc += size;
        }

        /** 输出局部变量指令(如 iload、istore、aload 等) */
        @Override
        public void visitVarInsn(int opcode, int var) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            sb.append(' ').append(opcodeName(opcode)).append(' ').append(var).append('\n');
            pc += size;
        }

        /** 输出类型指令(如 new、anewarray、checkcast、instanceof) */
        @Override
        public void visitTypeInsn(int opcode, String type) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            sb.append(' ').append(opcodeName(opcode)).append(' ').append(type.replace('/', '.'));
            sb.append("    // L").append(type.replace('.', '/')).append(';').append('\n');
            pc += size;
        }

        /**
         * 输出字段访问指令
         *
         * @param opcode     操作码
         * @param owner      字段所属类内部名
         * @param name       字段名
         * @param descriptor 字段类型描述符
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            int idx = readU2(raw, codeOffset + pc + 1); // 操作数中的字段引用索引
            String typeStr = SmaliTextBuilder.formatTypeReadable(Type.getType(descriptor));
            sb.append(' ').append(opcodeName(opcode)).append(" #").append(idx)
                    .append("  // ").append(owner).append('.').append(name).append(" : ").append(typeStr)
                    .append('\n');
            pc += size;
        }

        /**
         * 输出方法调用指令
         *
         * @param opcode      操作码
         * @param owner       方法所属类内部名
         * @param name        方法名
         * @param descriptor  方法描述符
         * @param isInterface 是否为接口方法调用
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            int idx = readU2(raw, codeOffset + pc + 1);
            sb.append(' ').append(opcodeName(opcode)).append(" #").append(idx)
                    .append("  // ").append(owner).append('.').append(name).append(descriptor)
                    .append('\n');
            pc += size;
        }

        /** 输出 invokedynamic 指令(动态方法调用) */
        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           Handle bsm, Object... bsmArgs) {
            int size = 5;
            appendInsnHex(Opcodes.INVOKEDYNAMIC, size);
            int idx = readU2(raw, codeOffset + pc + 1);
            sb.append(" invokedynamic #").append(idx)
                    .append("  // ").append(name).append(descriptor).append('\n');
            pc += size;
        }

        /** 输出跳转指令(如 ifeq、goto 等),含分支偏移量和目标地址 */
        @Override
        public void visitJumpInsn(int opcode, Label label) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(opcode, size);
            int offset;
            // GOTO_W(200) 和 JSR_W(201) 使用 4 字节有符号分支偏移量
            if (opcode == 200 || opcode == 201) {
                offset = readS4(raw, codeOffset + pc + 1);
            } else {
                offset = readS2(raw, codeOffset + pc + 1);
            }
            int target = pc + offset;
            sb.append(' ').append(opcodeName(opcode))
                    .append(String.format(" %+d → %04x", offset, target)).append('\n');
            pc += size;
        }

        /** 输出 LDC 系列指令(加载常量：字符串、整数、浮点数、类型等) */
        @Override
        public void visitLdcInsn(Object cst) {
            int size;
            int opcode;
            int idx;
            if (cst instanceof Integer || cst instanceof Float) {
                size = 2;
                opcode = Opcodes.LDC;
                // 边界检查：截断的 class 文件中 codeOffset+pc+1 可能超出数组范围
                int rawIdx = codeOffset + pc + 1;
                idx = (rawIdx >= 0 && rawIdx < raw.length) ? (raw[rawIdx] & 0xFF) : 0;
            } else if (cst instanceof Long || cst instanceof Double) {
                size = 3;
                opcode = 20; // LDC2_W
                idx = readU2(raw, codeOffset + pc + 1);
            } else {
                size = 3;
                opcode = 19; // LDC_W
                idx = readU2(raw, codeOffset + pc + 1);
            }

            appendInsnHex(opcode, size);
            sb.append(' ').append(opcodeName(opcode)).append(" #").append(idx);
            // 注释解析后的值
            sb.append("  // ");
            if (cst instanceof String s) {
                sb.append('"').append(escape(s)).append('"');
            } else if (cst instanceof Type t) {
                sb.append("class ").append(t.getInternalName());
            } else {
                sb.append(cst);
            }
            sb.append('\n');
            pc += size;
        }

        /** 输出 IINC 指令(局部变量自增/自减) */
        @Override
        public void visitIincInsn(int var, int increment) {
            int size = actualSize(raw, codeOffset + pc);
            appendInsnHex(Opcodes.IINC, size);
            sb.append(' ').append(opcodeName(Opcodes.IINC))
                    .append(' ').append(var).append(' ').append(increment).append('\n');
            pc += size;
        }

        /**
         * 输出 tableswitch 指令(连续区间跳转表)
         *
         * @param min    键最小值
         * @param max    键最大值
         * @param dflt   默认跳转标签
         * @param labels 各 case 对应的跳转标签
         */
        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            int pad = (4 - (pc + 1) % 4) % 4;
            int size = 1 + pad + 4 + 4 + 4 + labels.length * 4;
            appendInsnHex(Opcodes.TABLESWITCH, size);
            sb.append(' ').append(opcodeName(Opcodes.TABLESWITCH))
                    .append(' ').append(min).append("..").append(max)
                    .append(" (").append(labels.length).append(" cases)\n");
            pc += size;
        }

        /**
         * 输出 lookupswitch 指令(稀疏键值跳转表)
         *
         * @param dflt   默认跳转标签
         * @param keys   键值数组
         * @param labels 各键对应的跳转标签
         */
        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            int pad = (4 - (pc + 1) % 4) % 4;
            int size = 1 + pad + 4 + 4 + keys.length * 8;
            appendInsnHex(Opcodes.LOOKUPSWITCH, size);
            sb.append(' ').append(opcodeName(Opcodes.LOOKUPSWITCH))
                    .append(" (").append(keys.length).append(" cases)\n");
            pc += size;
        }

        /** 输出 multianewarray 指令(多维数组创建) */
        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            int size = 4;
            appendInsnHex(Opcodes.MULTIANEWARRAY, size);
            int idx = readU2(raw, codeOffset + pc + 1);
            sb.append(' ').append(opcodeName(Opcodes.MULTIANEWARRAY))
                    .append(" #").append(idx).append(' ')
                    .append(numDimensions).append(" dims  // ").append(descriptor).append('\n');
            pc += size;
        }

        // ==================== hex 输出辅助 ====================

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            // 跳过帧信息,保持输出整洁
        }

        /** 输出 hex 偏移 + 原始字节 + | + PC偏移 + : */
        private void appendInsnHex(int opcode, int size) {
            int fileOff = codeOffset + pc;
            sb.append(INSN_INDENT)
                    .append(String.format("%08x: ", fileOff));
            // 原始字节(最多6字节),防御截断的字节码
            int safeSize = Math.min(size, raw.length - Math.max(0, fileOff));
            for (int i = 0; i < safeSize && i < 6; i++) {
                int b = raw[fileOff + i] & 0xFF;
                sb.append(String.format("%02x ", b));
            }
            // 对齐：6字节 = 18字符 + 10(addr) + 2 = 30, 对齐到38
            int printed = 10 + size * 3;
            while (printed < 38) {
                sb.append(' ');
                printed++;
            }
            // PC偏移
            sb.append(String.format(" |%04x:", pc));
        }
    }
}
