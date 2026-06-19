package com.bingbaihanji.fxdecomplie.bytecode;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal class-file parser for metadata paths that must keep working when ASM
 * does not yet understand the latest class file major version.
 */
public final class ClassFileParser {

    private static final int CLASS_MAGIC = 0xCAFEBABE;

    private ClassFileParser() {
        throw new AssertionError("utility class");
    }

    public static Optional<ClassFileMetadata> tryParse(byte[] bytes) {
        try {
            return Optional.of(parse(bytes));
        } catch (IOException | ClassFormatException ex) {
            return Optional.empty();
        }
    }

    public static ClassFileMetadata parse(byte[] bytes) throws IOException, ClassFormatException {
        if (bytes == null || bytes.length < 10) {
            throw new ClassFormatException("Class file is empty or truncated");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int magic = in.readInt();
            if (magic != CLASS_MAGIC) {
                throw new ClassFormatException("Invalid class file magic");
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

            return new ClassFileMetadata(minor, major, accessFlags, internalName, superName,
                    interfaces, constantPoolCount, fields, methods);
        }
    }

    public static String summary(byte[] bytes) {
        return tryParse(bytes)
                .map(ClassFileParser::summary)
                .orElse("// Unable to parse class file metadata");
    }

    public static String summary(ClassFileMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Class file metadata\n");
        sb.append("// Version: ").append(metadata.majorVersion())
                .append('.').append(metadata.minorVersion())
                .append(" (Java ").append(javaVersion(metadata.majorVersion())).append(")\n");
        sb.append("// Class: ").append(metadata.internalName()).append('\n');
        sb.append("// Super: ").append(metadata.superName() == null ? "(none)" : metadata.superName()).append('\n');
        if (metadata.interfaces().isEmpty()) {
            sb.append("// Interfaces: (none)\n");
        } else {
            sb.append("// Interfaces: ").append(String.join(", ", metadata.interfaces())).append('\n');
        }
        sb.append('\n');
        sb.append("// Fields\n");
        for (ClassFileMetadata.MemberInfo field : metadata.fields()) {
            sb.append(field.descriptor()).append(' ').append(field.name()).append('\n');
        }
        sb.append('\n');
        sb.append("// Methods\n");
        for (ClassFileMetadata.MemberInfo method : metadata.methods()) {
            sb.append(method.name()).append(method.descriptor()).append('\n');
        }
        return sb.toString();
    }

    public static int javaVersion(int majorVersion) {
        return majorVersion >= 45 ? majorVersion - 44 : majorVersion;
    }

    private static Object[] readConstantPool(DataInputStream in, int count)
            throws IOException, ClassFormatException {
        Object[] cp = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> cp[i] = in.readUTF();
                case 3, 4 -> skipFully(in, 4);
                case 5, 6 -> {
                    skipFully(in, 8);
                    i++;
                }
                case 7 -> cp[i] = new ClassRef(in.readUnsignedShort());
                case 8, 16, 19, 20 -> skipFully(in, 2);
                case 9, 10, 11, 12, 17, 18 -> skipFully(in, 4);
                case 15 -> skipFully(in, 3);
                default -> throw new ClassFormatException("Unsupported constant pool tag: " + tag);
            }
        }
        return cp;
    }

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

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            skipFully(in, 2);
            long length = Integer.toUnsignedLong(in.readInt());
            skipFully(in, length);
        }
    }

    private static String className(Object[] cp, int classIndex) throws ClassFormatException {
        if (classIndex == 0) {
            return null;
        }
        Object entry = cpEntry(cp, classIndex);
        if (entry instanceof ClassRef ref) {
            return utf(cp, ref.nameIndex());
        }
        throw new ClassFormatException("Expected class reference at constant pool index " + classIndex);
    }

    private static String utf(Object[] cp, int index) throws ClassFormatException {
        Object entry = cpEntry(cp, index);
        if (entry instanceof String value) {
            return value;
        }
        throw new ClassFormatException("Expected UTF-8 entry at constant pool index " + index);
    }

    private static Object cpEntry(Object[] cp, int index) throws ClassFormatException {
        if (index <= 0 || index >= cp.length) {
            throw new ClassFormatException("Invalid constant pool index: " + index);
        }
        Object entry = cp[index];
        if (entry == null) {
            throw new ClassFormatException("Missing constant pool entry: " + index);
        }
        return entry;
    }

    private static void skipFully(DataInputStream in, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            int skipped = in.skipBytes((int) Math.min(remaining, Integer.MAX_VALUE));
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException("Unexpected end of class file");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private record ClassRef(int nameIndex) {
    }

    public static final class ClassFormatException extends Exception {

        public ClassFormatException(String message) {
            super(message);
        }
    }
}
