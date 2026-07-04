package com.bingbaihanji.fxdecomplie.ui.code;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 ASM ClassVisitor，生成 jadx 风格的 smali 文本。
 *
 * <p>输出格式：.class / .super / .source 头信息 + .field / .method 定义，
 * 方法体包含 .registers / .line / .local 和 smali 风格操作码（小写、带连字符）</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
final class SmaliTextBuilder extends ClassVisitor {

    private static final String INDENT = "    ";
    private static final String METHOD_INDENT = INDENT;

    private final StringBuilder sb = new StringBuilder(4096);
    private final List<FieldInfo> fields = new ArrayList<>();
    private String className;
    private String sourceFile;
    private int version;
    private int access;
    private String superName;
    private String[] interfaces;
    private String currentMethodText;

    SmaliTextBuilder() {
        super(Opcodes.ASM9);
    }

    // -- 类级别 --

    /** 格式化 smali 访问标志 */
    static String formatAccess(int access, boolean isClass) {
        StringBuilder a = new StringBuilder();
        if ((access & Opcodes.ACC_PUBLIC) != 0) a.append("public ");
        if ((access & Opcodes.ACC_PRIVATE) != 0) a.append("private ");
        if ((access & Opcodes.ACC_PROTECTED) != 0) a.append("protected ");
        if ((access & Opcodes.ACC_STATIC) != 0) a.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) a.append("final ");
        if (isClass) {
            if ((access & Opcodes.ACC_ABSTRACT) != 0) a.append("abstract ");
            if ((access & Opcodes.ACC_ANNOTATION) != 0) a.append("annotation ");
            if ((access & Opcodes.ACC_INTERFACE) != 0) a.append("interface ");
            if ((access & Opcodes.ACC_ENUM) != 0) a.append("enum ");
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) a.append("synthetic ");
        } else {
            if ((access & Opcodes.ACC_VOLATILE) != 0) a.append("volatile ");
            if ((access & Opcodes.ACC_TRANSIENT) != 0) a.append("transient ");
            if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) a.append("synchronized ");
            if ((access & Opcodes.ACC_BRIDGE) != 0) a.append("bridge ");
            if ((access & Opcodes.ACC_VARARGS) != 0) a.append("varargs ");
            if ((access & Opcodes.ACC_NATIVE) != 0) a.append("native ");
            if ((access & Opcodes.ACC_ABSTRACT) != 0) a.append("abstract ");
            if ((access & Opcodes.ACC_STRICT) != 0) a.append("strict ");
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) a.append("synthetic ");
            if ((access & Opcodes.ACC_ENUM) != 0) a.append("enum ");
        }
        return a.toString().trim();
    }

    static String formatValue(Object value) {
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        if (value instanceof Type t) {
            return formatType(t);
        }
        return String.valueOf(value);
    }

    /** 将 ASM 操作码转为 smali 风格操作码（小写、连字符分隔） */
    static String toSmaliOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.NOP -> "nop";
            case Opcodes.ACONST_NULL -> "const/4 v0, 0x0";
            case Opcodes.ICONST_M1 -> "const/4 v0, -1";
            case Opcodes.ICONST_0 -> "const/4 v0, 0x0";
            case Opcodes.ICONST_1 -> "const/4 v0, 0x1";
            case Opcodes.ICONST_2 -> "const/4 v0, 0x2";
            case Opcodes.ICONST_3 -> "const/4 v0, 0x3";
            case Opcodes.ICONST_4 -> "const/4 v0, 0x4";
            case Opcodes.ICONST_5 -> "const/4 v0, 0x5";
            case Opcodes.LCONST_0 -> "const-wide/16 v0, 0x0";
            case Opcodes.LCONST_1 -> "const-wide/16 v0, 0x1";
            case Opcodes.FCONST_0 -> "const/4 v0, 0x0";
            case Opcodes.FCONST_1 -> "const/high16 v0, 0x3f800000"; // float 1.0f bit pattern
            case Opcodes.FCONST_2 -> "const/high16 v0, 0x40000000";
            case Opcodes.DCONST_0 -> "const-wide/16 v0, 0x0";
            case Opcodes.DCONST_1 -> "const-wide/high16 v0, 0x3ff0000000000000"; // double 1.0 bit pattern
            case Opcodes.IALOAD -> "aget v0, v0, v1";
            case Opcodes.LALOAD -> "aget-wide v0, v0, v1";
            case Opcodes.FALOAD -> "aget v0, v0, v1";
            case Opcodes.DALOAD -> "aget-wide v0, v0, v1";
            case Opcodes.AALOAD -> "aget-object v0, v0, v1";
            case Opcodes.BALOAD -> "aget-byte v0, v0, v1";
            case Opcodes.CALOAD -> "aget-char v0, v0, v1";
            case Opcodes.SALOAD -> "aget-short v0, v0, v1";
            case Opcodes.IASTORE -> "aput v1, v0, v2";
            case Opcodes.LASTORE -> "aput-wide v1, v0, v2";
            case Opcodes.FASTORE -> "aput v1, v0, v2";
            case Opcodes.DASTORE -> "aput-wide v1, v0, v2";
            case Opcodes.AASTORE -> "aput-object v1, v0, v2";
            case Opcodes.BASTORE -> "aput-byte v1, v0, v2";
            case Opcodes.CASTORE -> "aput-char v1, v0, v2";
            case Opcodes.SASTORE -> "aput-short v1, v0, v2";
            case Opcodes.POP -> "move v0, v0";
            case Opcodes.POP2 -> "move-wide v0, v0";
            case Opcodes.DUP -> "move v0, v0";
            case Opcodes.DUP_X1 -> "move v0, v0";
            case Opcodes.DUP_X2 -> "move v0, v0";
            case Opcodes.DUP2 -> "move-wide v0, v0";
            case Opcodes.DUP2_X1 -> "move-wide v0, v0";
            case Opcodes.DUP2_X2 -> "move-wide v0, v0";
            case Opcodes.SWAP -> "move v0, v0";
            case Opcodes.IADD -> "add-int/2addr v0, v1";
            case Opcodes.LADD -> "add-long/2addr v0, v1";
            case Opcodes.FADD -> "add-float/2addr v0, v1";
            case Opcodes.DADD -> "add-double/2addr v0, v1";
            case Opcodes.ISUB -> "sub-int/2addr v0, v1";
            case Opcodes.LSUB -> "sub-long/2addr v0, v1";
            case Opcodes.FSUB -> "sub-float/2addr v0, v1";
            case Opcodes.DSUB -> "sub-double/2addr v0, v1";
            case Opcodes.IMUL -> "mul-int/2addr v0, v1";
            case Opcodes.LMUL -> "mul-long/2addr v0, v1";
            case Opcodes.FMUL -> "mul-float/2addr v0, v1";
            case Opcodes.DMUL -> "mul-double/2addr v0, v1";
            case Opcodes.IDIV -> "div-int/2addr v0, v1";
            case Opcodes.LDIV -> "div-long/2addr v0, v1";
            case Opcodes.FDIV -> "div-float/2addr v0, v1";
            case Opcodes.DDIV -> "div-double/2addr v0, v1";
            case Opcodes.IREM -> "rem-int/2addr v0, v1";
            case Opcodes.LREM -> "rem-long/2addr v0, v1";
            case Opcodes.FREM -> "rem-float/2addr v0, v1";
            case Opcodes.DREM -> "rem-double/2addr v0, v1";
            case Opcodes.INEG -> "neg-int v0, v0";
            case Opcodes.LNEG -> "neg-long v0, v0";
            case Opcodes.FNEG -> "neg-float v0, v0";
            case Opcodes.DNEG -> "neg-double v0, v0";
            case Opcodes.ISHL -> "shl-int/2addr v0, v1";
            case Opcodes.LSHL -> "shl-long/2addr v0, v1";
            case Opcodes.ISHR -> "shr-int/2addr v0, v1";
            case Opcodes.LSHR -> "shr-long/2addr v0, v1";
            case Opcodes.IUSHR -> "ushr-int/2addr v0, v1";
            case Opcodes.LUSHR -> "ushr-long/2addr v0, v1";
            case Opcodes.IAND -> "and-int/2addr v0, v1";
            case Opcodes.LAND -> "and-long/2addr v0, v1";
            case Opcodes.IOR -> "or-int/2addr v0, v1";
            case Opcodes.LOR -> "or-long/2addr v0, v1";
            case Opcodes.IXOR -> "xor-int/2addr v0, v1";
            case Opcodes.LXOR -> "xor-long/2addr v0, v1";
            case Opcodes.I2L -> "int-to-long v0, v0";
            case Opcodes.I2F -> "int-to-float v0, v0";
            case Opcodes.I2D -> "int-to-double v0, v0";
            case Opcodes.L2I -> "long-to-int v0, v0";
            case Opcodes.L2F -> "long-to-float v0, v0";
            case Opcodes.L2D -> "long-to-double v0, v0";
            case Opcodes.F2I -> "float-to-int v0, v0";
            case Opcodes.F2L -> "float-to-long v0, v0";
            case Opcodes.F2D -> "float-to-double v0, v0";
            case Opcodes.D2I -> "double-to-int v0, v0";
            case Opcodes.D2L -> "double-to-long v0, v0";
            case Opcodes.D2F -> "double-to-float v0, v0";
            case Opcodes.I2B -> "int-to-byte v0, v0";
            case Opcodes.I2C -> "int-to-char v0, v0";
            case Opcodes.I2S -> "int-to-short v0, v0";
            case Opcodes.LCMP -> "cmp-long v0, v0, v1";
            case Opcodes.FCMPL -> "cmpg-float v0, v0, v1";
            case Opcodes.FCMPG -> "cmpg-float v0, v0, v1";
            case Opcodes.DCMPL -> "cmpg-double v0, v0, v1";
            case Opcodes.DCMPG -> "cmpg-double v0, v0, v1";
            case Opcodes.IRETURN -> "return v0";
            case Opcodes.LRETURN -> "return-wide v0";
            case Opcodes.FRETURN -> "return v0";
            case Opcodes.DRETURN -> "return-wide v0";
            case Opcodes.ARETURN -> "return-object v0";
            case Opcodes.RETURN -> "return-void";
            case Opcodes.ARRAYLENGTH -> "array-length v0, v0";
            case Opcodes.ATHROW -> "throw v0";
            case Opcodes.MONITORENTER -> "monitor-enter v0";
            case Opcodes.MONITOREXIT -> "monitor-exit v0";
            // 带操作数的指令通过 visit*Insn 分别处理
            default -> fallbackOpcode(opcode);
        };
    }

    /** 带操作数指令的 smali 前缀（通过 visitXxxInsn 展开参数） */
    static String opcodePrefixSmali(int opcode) {
        return switch (opcode) {
            case Opcodes.BIPUSH -> "const/4";
            case Opcodes.SIPUSH -> "const/16";
            case Opcodes.ILOAD -> "move";
            case Opcodes.LLOAD -> "move-wide";
            case Opcodes.FLOAD -> "move";
            case Opcodes.DLOAD -> "move-wide";
            case Opcodes.ALOAD -> "move-object";
            case Opcodes.ISTORE -> "move";
            case Opcodes.LSTORE -> "move-wide";
            case Opcodes.FSTORE -> "move";
            case Opcodes.DSTORE -> "move-wide";
            case Opcodes.ASTORE -> "move-object";
            case Opcodes.IINC -> "add-int/lit8";
            case Opcodes.GOTO -> "goto";
            case 200 -> "goto/32";
            case Opcodes.IFEQ -> "if-eqz";
            case Opcodes.IFNE -> "if-nez";
            case Opcodes.IFLT -> "if-ltz";
            case Opcodes.IFGE -> "if-gez";
            case Opcodes.IFGT -> "if-gtz";
            case Opcodes.IFLE -> "if-lez";
            case Opcodes.IF_ICMPEQ -> "if-eq";
            case Opcodes.IF_ICMPNE -> "if-ne";
            case Opcodes.IF_ICMPLT -> "if-lt";
            case Opcodes.IF_ICMPGE -> "if-ge";
            case Opcodes.IF_ICMPGT -> "if-gt";
            case Opcodes.IF_ICMPLE -> "if-le";
            case Opcodes.IF_ACMPEQ -> "if-eq";
            case Opcodes.IF_ACMPNE -> "if-ne";
            case Opcodes.IFNULL -> "if-eqz";
            case Opcodes.IFNONNULL -> "if-nez";
            case Opcodes.TABLESWITCH -> "sparse-switch";
            case Opcodes.LOOKUPSWITCH -> "sparse-switch";
            case Opcodes.GETSTATIC -> "sget-object";
            case Opcodes.PUTSTATIC -> "sput-object";
            case Opcodes.GETFIELD -> "iget-object";
            case Opcodes.PUTFIELD -> "iput-object";
            case Opcodes.INVOKEVIRTUAL -> "invoke-virtual";
            case Opcodes.INVOKESPECIAL -> "invoke-direct";
            case Opcodes.INVOKESTATIC -> "invoke-static";
            case Opcodes.INVOKEINTERFACE -> "invoke-interface";
            case Opcodes.INVOKEDYNAMIC -> "invoke-dynamic";
            case Opcodes.NEW -> "new-instance";
            case Opcodes.NEWARRAY -> "new-array";
            case Opcodes.ANEWARRAY -> "new-array";
            case Opcodes.CHECKCAST -> "check-cast";
            case Opcodes.INSTANCEOF -> "instance-of";
            case Opcodes.MULTIANEWARRAY -> "filled-new-array";
            default -> fallbackOpcode(opcode);
        };
    }

    /** 无操作数操作码的降级处理 */
    private static String fallbackOpcode(int opcode) {
        if (opcode < 0 || opcode >= org.objectweb.asm.util.Printer.OPCODES.length) {
            return "unknown-" + opcode;
        }
        String upper = org.objectweb.asm.util.Printer.OPCODES[opcode];
        if (upper == null) {
            return "unknown-" + opcode;
        }
        return upper.toLowerCase().replace('_', '-');
    }

    /** 格式化类型字符串 */
    static String formatType(Type type) {
        if (type == null) {
            return "?";
        }
        return switch (type.getSort()) {
            case Type.VOID -> "V";
            case Type.BOOLEAN -> "Z";
            case Type.CHAR -> "C";
            case Type.BYTE -> "B";
            case Type.SHORT -> "S";
            case Type.INT -> "I";
            case Type.FLOAT -> "F";
            case Type.LONG -> "J";
            case Type.DOUBLE -> "D";
            case Type.ARRAY -> "[" + formatType(type.getElementType());
            case Type.OBJECT -> "L" + type.getInternalName() + ";";
            default -> type.getDescriptor();
        };
    }

    /** 格式化类型为可读形式（用于 .field 和 .local） */
    static String formatTypeReadable(Type type) {
        if (type == null) {
            return "?";
        }
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.CHAR -> "char";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.ARRAY -> formatTypeReadable(type.getElementType()) + "[]";
            case Type.OBJECT -> type.getClassName().replace('.', '/');
            default -> type.getDescriptor();
        };
    }

    /** Java 版本名称 */
    static String javaVersionName(int major) {
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
            case 51 -> "Java 7";
            case 50 -> "Java 6";
            default -> major > 69 ? "Java " + (major - 44) : null;
        };
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.version = version;
        this.access = access;
        this.className = name;
        this.superName = superName;
        this.interfaces = interfaces;
    }

    @Override
    public void visitSource(String source, String debug) {
        this.sourceFile = source;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                   String signature, Object value) {
        FieldInfo f = new FieldInfo();
        f.access = access;
        f.name = name;
        f.descriptor = descriptor;
        f.signature = signature;
        f.value = value;
        fields.add(f);
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        // 先将上一个方法的文本写入
        if (currentMethodText != null) {
            sb.append(currentMethodText);
        }
        return new SmaliMethodWriter(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        if (currentMethodText != null) {
            sb.append(currentMethodText);
        }
    }

    /** 构建最终 smali 文本 */
    String build() {
        StringBuilder out = new StringBuilder(sb.length() + 512);
        String rawName = className != null ? className : "?";
        String dotName = className != null ? className.replace('/', '.') : "?";

        // 标题注释（非 smali 标准，便于辨识）
        out.append("###### Smali: ").append(dotName);
        if (!dotName.equals(rawName)) {
            out.append(" (").append(rawName).append(")");
        }
        out.append('\n');

        // 版本号查看
        int major = version & 0xFFFF;
        out.append("# version ").append(major);
        String javaVer = javaVersionName(major);
        if (javaVer != null) {
            out.append(" (").append(javaVer).append(")");
        }
        out.append('\n');

        // .class
        String accessStr = formatAccess(access, true);
        out.append(".class");
        if (!accessStr.isEmpty()) {
            out.append(' ').append(accessStr);
        }
        out.append(" L").append(rawName).append(";\n");

        // .super
        if (superName != null && !"java/lang/Object".equals(superName)) {
            out.append(".super L").append(superName).append(";\n");
        }

        // .source
        if (sourceFile != null) {
            out.append(".source \"").append(sourceFile).append("\"\n");
        }

        // .implements
        if (interfaces != null) {
            for (String iface : interfaces) {
                out.append(".implements L").append(iface).append(";\n");
            }
        }
        out.append('\n');

        // 字段 — 分组：静态字段 / 实例字段
        List<FieldInfo> staticFields = new ArrayList<>();
        List<FieldInfo> instanceFields = new ArrayList<>();
        for (FieldInfo f : fields) {
            if ((f.access & Opcodes.ACC_STATIC) != 0) {
                staticFields.add(f);
            } else {
                instanceFields.add(f);
            }
        }

        if (!staticFields.isEmpty()) {
            out.append("# static fields\n");
            for (FieldInfo f : staticFields) {
                appendField(out, f);
            }
            out.append('\n');
        }

        if (!instanceFields.isEmpty()) {
            out.append("# instance fields\n");
            for (FieldInfo f : instanceFields) {
                appendField(out, f);
            }
            out.append('\n');
        }

        // 方法 — 分组：direct methods / virtual methods
        out.append(sb);
        return out.toString();
    }

    private void appendField(StringBuilder out, FieldInfo f) {
        String accessStr = formatAccess(f.access, false);
        String typeStr = formatTypeReadable(Type.getType(f.descriptor));
        out.append(INDENT).append(".field");
        if (!accessStr.isEmpty()) {
            out.append(' ').append(accessStr);
        }
        out.append(' ').append(f.name).append(':').append(typeStr);
        if (f.value != null) {
            out.append(" = ").append(formatValue(f.value));
        }
        out.append('\n');
    }

    // -- 内部数据结构 --

    private static class FieldInfo {
        int access;
        String name;
        String descriptor;
        String signature;
        Object value;
    }

    // -- 方法写入器 --

    private class SmaliMethodWriter extends MethodVisitor {
        private final StringBuilder mb = new StringBuilder(256);
        private final int methodAccess;
        private final String methodName;
        private final String methodDesc;
        private final String[] exceptions;
        private int maxStack;
        private int maxLocals;
        private boolean isDirect;

        SmaliMethodWriter(int access, String name, String desc,
                          String signature, String[] exceptions) {
            super(Opcodes.ASM9);
            this.methodAccess = access;
            this.methodName = name;
            this.methodDesc = desc;
            this.exceptions = exceptions;
            // direct methods: <init>, <clinit>, private, static, final
            this.isDirect = "<init>".equals(name) || "<clinit>".equals(name)
                    || (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != 0;
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) {
            if (name == null || name.isEmpty()) return;
            String typeName = formatTypeReadable(Type.getType(descriptor));
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append(".local v").append(index)
                    .append(", \"").append(name).append("\":L")
                    .append(typeName).append(";\n");
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append(".line ").append(line).append('\n');
        }

        // -- 指令访问 --

        @Override
        public void visitInsn(int opcode) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append(toSmaliOpcode(opcode)).append('\n');
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            String prefix = opcodePrefixSmali(opcode);
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix);
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                mb.append(" v0, 0x").append(Integer.toHexString(operand));
            } else {
                mb.append(" v0, v").append(operand);
            }
            mb.append("    # ").append(fallbackOpcode(opcode)).append(' ').append(operand).append('\n');
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            String prefix = opcodePrefixSmali(opcode);
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix);
            // store: vVar ← result; load: result ← vVar
            if (opcode == Opcodes.ISTORE || opcode == Opcodes.LSTORE
                    || opcode == Opcodes.FSTORE || opcode == Opcodes.DSTORE
                    || opcode == Opcodes.ASTORE) {
                mb.append(" v").append(var).append(", v0");
            } else {
                mb.append(" v0, v").append(var);
            }
            mb.append("    # ").append(fallbackOpcode(opcode)).append(' ').append(var).append('\n');
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            String prefix = opcodePrefixSmali(opcode);
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix);
            String typeDesc = type.replace('.', '/');
            mb.append(" v0, L").append(typeDesc).append(";");
            mb.append("    # ").append(fallbackOpcode(opcode)).append(' ').append(typeDesc).append('\n');
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String prefix = opcodePrefixSmali(opcode);
            String typeStr = formatTypeReadable(Type.getType(descriptor));
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix)
                    .append(" v0, L").append(owner).append(";->")
                    .append(name).append(':').append(typeStr);
            mb.append("    # ").append(fallbackOpcode(opcode)).append('\n');
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {
            String prefix = opcodePrefixSmali(opcode);
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix)
                    .append(" {}, L").append(owner).append(";->")
                    .append(name).append('(');
            // 解析参数类型
            Type[] argTypes = Type.getArgumentTypes(descriptor);
            for (int i = 0; i < argTypes.length; i++) {
                if (i > 0) {
                    mb.append(", ");
                }
                mb.append(formatTypeReadable(argTypes[i]));
            }
            mb.append(')').append(formatTypeReadable(Type.getReturnType(descriptor)));
            mb.append("    # ").append(fallbackOpcode(opcode)).append('\n');
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append("invoke-dynamic {}, ")
                    .append(name).append(descriptor);
            mb.append("    # invokedynamic\n");
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            String prefix = opcodePrefixSmali(opcode);
            mb.append(METHOD_INDENT).append(METHOD_INDENT).append(prefix)
                    .append(" v0, :label_??");
            mb.append("    # ").append(fallbackOpcode(opcode)).append('\n');
        }

        @Override
        public void visitLdcInsn(Object cst) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT);
            if (cst instanceof String s) {
                mb.append("const-string v0, \"").append(BytecodeTextBuilder.escape(s)).append('"');
            } else if (cst instanceof Type t) {
                mb.append("const-class v0, L").append(t.getInternalName()).append(';');
            } else if (cst instanceof Integer || cst instanceof Float) {
                mb.append("const v0, ").append(cst);
            } else if (cst instanceof Long || cst instanceof Double) {
                mb.append("const-wide v0, ").append(cst);
            } else {
                mb.append("const v0, ").append(cst);
            }
            mb.append("    # ldc\n");
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            String sign = increment >= 0 ? "+" : "";
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append("add-int/lit8 v").append(var).append(", v").append(var)
                    .append(", ").append(sign).append(increment);
            mb.append("    # iinc ").append(var).append(' ').append(increment).append('\n');
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append("sparse-switch v0, :label_??");
            mb.append("    # tableswitch ").append(min).append("..").append(max).append('\n');
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append("sparse-switch v0, :label_??");
            mb.append("    # lookupswitch [").append(keys.length).append(" cases]\n");
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            mb.append(METHOD_INDENT).append(METHOD_INDENT)
                    .append("filled-new-array v0, [");
            for (int i = 0; i < numDimensions; i++) {
                if (i > 0) {
                    mb.append(", ");
                }
                mb.append("v0");
            }
            mb.append("], ").append(descriptor);
            mb.append("    # multianewarray\n");
        }

        @Override
        public void visitEnd() {
            // 构建方法完整文本
            StringBuilder header = new StringBuilder(128);
            // 分组标题
            String group = isDirect ? "# direct methods\n" : "# virtual methods\n";

            // 方法签名
            String accessStr = formatAccess(methodAccess, false);
            header.append(INDENT).append(".method");
            if (!accessStr.isEmpty()) {
                header.append(' ').append(accessStr);
            }
            header.append(' ').append(methodName);

            // 格式化方法描述符（smali 风格: 参数类型用 () 括起来，后面跟返回类型）
            Type[] argTypes = Type.getArgumentTypes(methodDesc);
            Type retType = Type.getReturnType(methodDesc);
            header.append('(');
            for (int i = 0; i < argTypes.length; i++) {
                header.append(formatTypeReadable(argTypes[i]));
            }
            header.append(')').append(formatTypeReadable(retType));
            header.append('\n');

            // .registers
            int registers = maxLocals + maxStack;
            if (registers < 1) {
                registers = 1;
            }
            header.append(METHOD_INDENT).append(".registers ").append(registers).append('\n');

            header.append(mb);
            header.append(INDENT).append(".end method\n\n");

            // 整合分组标题和方法文本
            currentMethodText = group + header.toString();
        }
    }
}
