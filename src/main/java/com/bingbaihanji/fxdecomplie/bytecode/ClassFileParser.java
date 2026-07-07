package com.bingbaihanji.fxdecomplie.bytecode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 最小化的 class 文件解析器,用于元数据路径,
 * 确保在 ASM 尚不支持最新 class 文件主版本号时仍可正常工作
 * <p>
 * 该解析器只读取 class 文件的基本结构信息（版本、类名、父类、接口、字段、方法签名）,
 * 不解析方法体、属性等详细内容,因此轻量且能兼容任意版本的 class 文件
 *
 * @author bingbaihanji
 */
public final class ClassFileParser {

    private static final Logger log = LoggerFactory.getLogger(ClassFileParser.class);

    /** class 文件的魔数标识：0xCAFEBABE */
    private static final int CLASS_MAGIC = 0xCAFEBABE;

    /** 工具类不可实例化 */
    private ClassFileParser() {
        throw new AssertionError("utility class");
    }

    /**
     * 安全解析 class 文件字节数组,将解析异常包装为 {@link Optional#empty()},
     * 适用于只需 best-effort 获取元数据的场景
     *
     * @param bytes class 文件原始字节
     * @return 解析成功时包含元数据的 Optional,否则为空
     */
    public static Optional<ClassFileMetadata> tryParse(byte[] bytes) {
        try {
            return Optional.of(parse(bytes));
        } catch (IOException | ClassFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * 解析 class 文件字节数组,提取基本元数据
     * <p>
     * 解析流程：魔数校验 → 版本号 → 常量池 → 访问标志 → 类名/父类/接口 → 字段/方法列表
     * 方法体、属性表等详细内容不会被解析,仅跳过
     *
     * @param bytes class 文件原始字节
     * @return 解析出的元数据对象
     * @throws IOException           读取 class 文件发生 I/O 错误
     * @throws ClassFormatException class 文件格式不合法（魔数错误、结构截断等）
     * @throws NullPointerException  bytes 为 null
     */
    public static ClassFileMetadata parse(byte[] bytes) throws IOException, ClassFormatException {
        if (bytes == null || bytes.length < 10) {
            throw new ClassFormatException("class 文件为空或被截断");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int magic = in.readInt();
            if (magic != CLASS_MAGIC) {
                throw new ClassFormatException("无效的 class 文件魔数");
            }

            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            int constantPoolCount = in.readUnsignedShort();
            Object[] constantPool = readConstantPool(in, constantPoolCount);

            int accessFlags = in.readUnsignedShort();
            String internalName = className(constantPool, in.readUnsignedShort());
            String superName = className(constantPool, in.readUnsignedShort());

            int interfaceCount = in.readUnsignedShort();
            List<String> interfaces = new ArrayList<>(interfaceCount);
            for (int i = 0; i < interfaceCount; i++) {
                interfaces.add(className(constantPool, in.readUnsignedShort()));
            }

            List<ClassFileMetadata.MemberInfo> fields = readMembers(in, constantPool);
            List<ClassFileMetadata.MemberInfo> methods = readMembers(in, constantPool);

            // 跳过类级属性
            skipAttributes(in);

            return new ClassFileMetadata(minor, major, accessFlags, internalName, superName,
                    interfaces, constantPoolCount, fields, methods);
        }
    }

    /**
     * 从字节数组生成人类可读的类文件摘要字符串解析失败时返回友好的提示信息
     *
     * @param bytes class 文件原始字节
     * @return 格式化的摘要文本
     */
    public static String summary(byte[] bytes) {
        return tryParse(bytes)
                .map(ClassFileParser::summary)
                .orElse("// 无法解析 class 文件元数据");
    }

    /**
     * 将元数据对象格式化为人类可读的摘要字符串,包括版本、类名、父类、接口、字段和方法信息
     *
     * @param metadata class 文件元数据
     * @return 格式化的摘要文本
     */
    public static String summary(ClassFileMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("// class 文件元数据\n");
        sb.append("// 版本: ").append(metadata.majorVersion())
                .append('.').append(metadata.minorVersion())
                .append(" (Java ").append(javaVersion(metadata.majorVersion())).append(")\n");
        sb.append("// 类: ").append(metadata.internalName()).append('\n');
        sb.append("// 父类: ").append(metadata.superName() == null ? "(无)" : metadata.superName()).append('\n');
        if (metadata.interfaces().isEmpty()) {
            sb.append("// 接口: (无)\n");
        } else {
            sb.append("// 接口: ").append(String.join(", ", metadata.interfaces())).append('\n');
        }
        sb.append('\n');
        sb.append("// 字段\n");
        for (ClassFileMetadata.MemberInfo field : metadata.fields()) {
            sb.append(field.descriptor()).append(' ').append(field.name()).append('\n');
        }
        sb.append('\n');
        sb.append("// 方法\n");
        for (ClassFileMetadata.MemberInfo method : metadata.methods()) {
            sb.append(method.name()).append(method.descriptor()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 将 class 文件主版本号转换为对应的 Java 版本号
     * <p>
     * 转换公式：Java 版本 = 主版本号 - 44（JDK 1.0 = 45,JDK 1.1 = 45,...）
     * 对于主版本号小于 45 的情况（理论上不存在）,直接返回原值
     *
     * @param majorVersion class 文件主版本号
     * @return 对应的 Java 主版本号（如 8、11、17 等）
     */
    public static int javaVersion(int majorVersion) {
        return majorVersion >= 45 ? majorVersion - 44 : majorVersion;
    }

    /**
     * 读取常量池,仅保留后续需要用到的 UTF-8 字符串和类引用条目,
     * 其他常量类型直接跳过以节省内存
     *
     * @param in    数据输入流
     * @param count 常量池条目总数（含索引 0 占位）
     * @return 常量池对象数组（索引 0 为 null）
     */
    private static Object[] readConstantPool(DataInputStream in, int count)
            throws IOException, ClassFormatException {
        Object[] cp = new Object[count];
        // 常量池索引从 1 开始
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                // CONSTANT_Utf8: 存储 UTF-8 字符串,后续解析类名、方法名需要
                case 1 -> cp[i] = in.readUTF();
                // CONSTANT_Integer(3), CONSTANT_Float(4): 4 字节数值,直接跳过
                case 3, 4 -> skipFully(in, 4);
                // CONSTANT_Long(5), CONSTANT_Double(6): 8 字节数值,占两个常量池索引
                case 5, 6 -> {
                    skipFully(in, 8);
                    i++; // 跳过占用的第二个索引
                }
                // CONSTANT_Class(7): 类引用,保存名称索引
                case 7 -> cp[i] = new ClassRef(in.readUnsignedShort());
                // CONSTANT_String(8), CONSTANT_MethodType(16),
                // CONSTANT_Module(19), CONSTANT_Package(20): 2 字节索引,跳过
                case 8, 16, 19, 20 -> skipFully(in, 2);
                // CONSTANT_Fieldref(9), CONSTANT_Methodref(10),
                // CONSTANT_InterfaceMethodref(11), CONSTANT_NameAndType(12),
                // CONSTANT_Dynamic(17), CONSTANT_InvokeDynamic(18): 4 字节,跳过
                case 9, 10, 11, 12, 17, 18 -> skipFully(in, 4);
                // CONSTANT_MethodHandle(15): 3 字节（1 字节引用类型 + 2 字节索引）,跳过
                case 15 -> skipFully(in, 3);
                default -> {
                    // 未知常量池标签,记录警告并跳过,保持向前兼容性
                    log.warn("跳过未知常量池标签: {} (索引 {})", tag, i);
                }
            }
        }
        return cp;
    }

    /**
     * 读取字段或方法表,提取访问标志、名称和描述符,然后跳过属性表
     *
     * @param in 数据输入流
     * @param cp 已解析的常量池
     * @return 成员信息列表
     */
    private static List<ClassFileMetadata.MemberInfo> readMembers(DataInputStream in, Object[] cp)
            throws IOException, ClassFormatException {
        int count = in.readUnsignedShort();
        List<ClassFileMetadata.MemberInfo> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int access = in.readUnsignedShort();
            String name = utf(cp, in.readUnsignedShort());
            String descriptor = utf(cp, in.readUnsignedShort());
            members.add(new ClassFileMetadata.MemberInfo(access, name, descriptor));
            skipAttributes(in);
        }
        return members;
    }

    /**
     * 跳过属性表,不解析属性内容
     * 每个属性结构为：2 字节名称索引 + 4 字节长度 + N 字节数据
     *
     * @param in 数据输入流
     */
    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            skipFully(in, 2);
            long length = Integer.toUnsignedLong(in.readInt());
            skipFully(in, length);
        }
    }

    /**
     * 从常量池中解析类名特殊处理 classIndex=0（表示无父类,如 java.lang.Object）
     *
     * @param cp         常量池数组
     * @param classIndex 常量池中类引用的索引
     * @return 类内部名称,或 null（表示无父类）
     */
    private static String className(Object[] cp, int classIndex) throws ClassFormatException {
        if (classIndex == 0) {
            return null;
        }
        Object entry = cpEntry(cp, classIndex);
        if (entry instanceof ClassRef ref) {
            return utf(cp, ref.nameIndex());
        }
        throw new ClassFormatException("期望常量池索引 " + classIndex + " 处为类引用");
    }

    /**
     * 从常量池中读取 UTF-8 字符串条目
     *
     * @param cp    常量池数组
     * @param index 常量池索引
     * @return UTF-8 字符串值
     */
    private static String utf(Object[] cp, int index) throws ClassFormatException {
        Object entry = cpEntry(cp, index);
        if (entry instanceof String value) {
            return value;
        }
        throw new ClassFormatException("期望常量池索引 " + index + " 处为 UTF-8 条目");
    }

    /**
     * 安全获取常量池条目,自动校验索引范围和非空
     *
     * @param cp    常量池数组
     * @param index 常量池索引
     * @return 常量池条目对象
     * @throws ClassFormatException 索引越界或条目为 null
     */
    private static Object cpEntry(Object[] cp, int index) throws ClassFormatException {
        if (index <= 0 || index >= cp.length) {
            throw new ClassFormatException("无效的常量池索引: " + index);
        }
        Object entry = cp[index];
        if (entry == null) {
            throw new ClassFormatException("缺失常量池条目: " + index);
        }
        return entry;
    }

    /**
     * 安全跳过指定长度的字节,处理 {@link DataInputStream#skipBytes(int)} 可能无法一次跳过全部
     * 字节的情况（某些 InputStream 实现返回部分跳过）,使用循环确保完全跳过
     *
     * @param in     数据输入流
     * @param length 需要跳过的字节数
     * @throws IOException 跳过过程中发生 I/O 错误或流意外结束
     */
    private static void skipFully(DataInputStream in, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            int skipped = in.skipBytes((int) Math.min(remaining, Integer.MAX_VALUE));
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException("意外的 class 文件结尾");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    /**
     * 常量池中的类引用条目,仅保留名称索引
     *
     * @param nameIndex 指向 CONSTANT_Utf8 的索引
     */
    private record ClassRef(int nameIndex) {
    }

    /**
     * class 文件格式异常,表示字节数组不是合法的 class 文件结构
     */
    public static final class ClassFormatException extends Exception {

        public ClassFormatException(String message) {
            super(message);
        }
    }
}
