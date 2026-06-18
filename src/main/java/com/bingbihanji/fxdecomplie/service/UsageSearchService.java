package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbihanji.fxdecomplie.model.UsageResult;
import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;
import org.objectweb.asm.*;

import java.util.*;

/**
 * Finds class/member usages by scanning bytecode operands with ASM.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class UsageSearchService {

    private UsageSearchService() {
        throw new AssertionError("utility class");
    }

    public static List<UsageResult> findUsages(WorkspaceIndex index, String query) {
        if (index == null || query == null || query.isBlank()) {
            return List.of();
        }
        Target target = Target.parse(query);
        List<UsageResult> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ClassIndexEntry cls : index.classes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            scanClass(cls, target, results, seen);
        }
        return results;
    }

    private static void scanClass(ClassIndexEntry cls, Target target,
                                  List<UsageResult> results, Set<String> seen) {
        try {
            ClassReader reader = new ClassReader(cls.bytes());
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    if (matchesClass(target, superName)) {
                        add(results, seen, cls.fullPath(), 1,
                                UsageResult.UsageType.CLASS_REFERENCE,
                                "extends " + superName);
                    }
                    if (interfaces != null) {
                        for (String iface : interfaces) {
                            if (matchesClass(target, iface)) {
                                add(results, seen, cls.fullPath(), 1,
                                        UsageResult.UsageType.CLASS_REFERENCE,
                                        "implements " + iface);
                            }
                        }
                    }
                    super.visit(version, access, name, signature, superName, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        private int currentLine = 1;

                        @Override
                        public void visitLineNumber(int line, org.objectweb.asm.Label start) {
                            currentLine = Math.max(1, line);
                            super.visitLineNumber(line, start);
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (matchesClass(target, type)) {
                                add(results, seen, cls.fullPath(), currentLine,
                                        UsageResult.UsageType.CLASS_REFERENCE,
                                        opcodeName(opcode) + " " + type);
                            }
                            super.visitTypeInsn(opcode, type);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner,
                                                   String name, String descriptor) {
                            if (matchesMember(target, owner, name) || matchesClass(target, owner)) {
                                add(results, seen, cls.fullPath(), currentLine,
                                        UsageResult.UsageType.FIELD_ACCESS,
                                        opcodeName(opcode) + " " + owner + "." + name + " " + descriptor);
                            }
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner,
                                                    String name, String descriptor,
                                                    boolean isInterface) {
                            if (matchesMember(target, owner, name) || matchesClass(target, owner)) {
                                add(results, seen, cls.fullPath(), currentLine,
                                        UsageResult.UsageType.METHOD_CALL,
                                        opcodeName(opcode) + " " + owner + "." + name + descriptor);
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Type type) {
                                addIfTypeMatches(cls, target, results, seen, currentLine, type);
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                            Type type = Type.getType(descriptor);
                            addIfTypeMatches(cls, target, results, seen, currentLine, type);
                            super.visitMultiANewArrayInsn(descriptor, numDimensions);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor,
                                                           Handle bootstrapMethodHandle,
                                                           Object... bootstrapMethodArguments) {
                            scanHandle(cls, target, results, seen, currentLine,
                                    bootstrapMethodHandle);
                            for (Object arg : bootstrapMethodArguments) {
                                if (arg instanceof Handle handle) {
                                    scanHandle(cls, target, results, seen, currentLine, handle);
                                } else if (arg instanceof Type type) {
                                    addIfTypeMatches(cls, target, results, seen, currentLine, type);
                                }
                            }
                            super.visitInvokeDynamicInsn(name, descriptor,
                                    bootstrapMethodHandle, bootstrapMethodArguments);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (Exception ignored) {
            // Malformed classes are ignored for usage search; they remain visible in class search.
        }
    }

    private static void scanHandle(ClassIndexEntry cls, Target target,
                                   List<UsageResult> results, Set<String> seen,
                                   int currentLine, Handle handle) {
        if (handle == null) {
            return;
        }
        UsageResult.UsageType type = handle.getTag() >= Opcodes.H_GETFIELD
                && handle.getTag() <= Opcodes.H_PUTSTATIC
                ? UsageResult.UsageType.FIELD_ACCESS
                : UsageResult.UsageType.METHOD_CALL;
        if (matchesMember(target, handle.getOwner(), handle.getName())
                || matchesClass(target, handle.getOwner())) {
            add(results, seen, cls.fullPath(), currentLine, type,
                    "handle " + handle.getOwner() + "." + handle.getName()
                            + handle.getDesc());
        }
    }

    private static void addIfTypeMatches(ClassIndexEntry cls, Target target,
                                         List<UsageResult> results, Set<String> seen,
                                         int currentLine, Type type) {
        String internalName = elementInternalName(type);
        if (matchesClass(target, internalName)) {
            add(results, seen, cls.fullPath(), currentLine,
                    UsageResult.UsageType.CLASS_REFERENCE,
                    "class " + internalName);
        }
    }

    private static String elementInternalName(Type type) {
        Type element = type;
        while (element != null && element.getSort() == Type.ARRAY) {
            element = element.getElementType();
        }
        return element != null && element.getSort() == Type.OBJECT
                ? element.getInternalName()
                : "";
    }

    private static boolean matchesClass(Target target, String owner) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        String normalizedOwner = owner.toLowerCase(Locale.ROOT);
        String simpleOwner = simpleName(normalizedOwner);
        return normalizedOwner.contains(target.classPart)
                || simpleOwner.contains(target.raw)
                || target.raw.contains(simpleOwner);
    }

    private static boolean matchesMember(Target target, String owner, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (target.memberPart != null) {
            return normalizedName.contains(target.memberPart)
                    && matchesClass(target, owner);
        }
        return normalizedName.contains(target.raw);
    }

    private static void add(List<UsageResult> results, Set<String> seen, String sourcePath,
                            int lineNumber, UsageResult.UsageType type, String text) {
        String key = sourcePath + '\n' + lineNumber + '\n' + type + '\n' + text;
        if (seen.add(key)) {
            results.add(new UsageResult(sourcePath, lineNumber, type, text));
        }
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private static String opcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.NEW -> "new";
            case Opcodes.ANEWARRAY -> "anewarray";
            case Opcodes.CHECKCAST -> "checkcast";
            case Opcodes.INSTANCEOF -> "instanceof";
            case Opcodes.GETFIELD -> "getfield";
            case Opcodes.PUTFIELD -> "putfield";
            case Opcodes.GETSTATIC -> "getstatic";
            case Opcodes.PUTSTATIC -> "putstatic";
            case Opcodes.INVOKEVIRTUAL -> "invokevirtual";
            case Opcodes.INVOKESPECIAL -> "invokespecial";
            case Opcodes.INVOKESTATIC -> "invokestatic";
            case Opcodes.INVOKEINTERFACE -> "invokeinterface";
            default -> "opcode-" + opcode;
        };
    }

    private record Target(String raw, String classPart, String memberPart) {
        private static Target parse(String query) {
            String rawQuery = query.trim()
                    .replace(".class", "")
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
            String classQuery = rawQuery.replace('.', '/');
            String memberQuery = null;
            int hash = classQuery.lastIndexOf('#');
            if (hash >= 0) {
                memberQuery = classQuery.substring(hash + 1);
                classQuery = classQuery.substring(0, hash);
            }
            return new Target(rawQuery.replace('.', '/'), classQuery, memberQuery);
        }
    }
}
