package com.bingbaihanji.fxdecomplie.ui.uml;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * 用 ASM 从 class 字节码提取 {@link UmlClass}。
 * 类型名优先取泛型签名（signature），否则回退描述符。
 */
public final class UmlClassExtractor {

    private static final Logger log = LoggerFactory.getLogger(UmlClassExtractor.class);

    private UmlClassExtractor() {
        throw new AssertionError("utility class");
    }

    /** 解析 class 字节为 UML 类；输入为空或解析失败返回 null */
    public static UmlClass extract(String internalName, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            UmlClassVisitor visitor = new UmlClassVisitor(internalName);
            reader.accept(visitor, 0);
            return visitor.build();
        } catch (RuntimeException e) {
            log.debug("UML 类提取失败: {}", internalName, e);
            return null;
        }
    }

    // ======================== 访问器 ========================

    private static final class UmlClassVisitor extends ClassVisitor {

        private final String expectedName;
        private String internalName;
        private String signature;
        private int access;
        private String superName;
        private String[] interfaces;
        private final List<UmlField> fields = new ArrayList<>();
        private final List<UmlMethod> methods = new ArrayList<>();
        private final List<String> annotations = new ArrayList<>();

        UmlClassVisitor(String expectedName) {
            super(Opcodes.ASM9);
            this.expectedName = expectedName;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.access = access;
            this.signature = signature;
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces;
            this.internalName = name != null ? name : expectedName;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor != null && descriptor.length() > 2
                    && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                annotations.add(descriptor.substring(1, descriptor.length() - 1));
            }
            return null;
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                         String descriptor, String signature, Object value) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                return null;
            }
            fields.add(new UmlField(
                    name,
                    readableType(signature != null ? signature : descriptor),
                    visibility(access),
                    (access & Opcodes.ACC_STATIC) != 0));
            return null;
        }

        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                           String descriptor, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0 || "<clinit>".equals(name)) {
                return null;
            }
            // javac 生成的枚举 values()/valueOf(String) 并非 SYNTHETIC，需显式过滤（枚举内这两个名字被保留，无法用户重载）
            boolean isEnumClass = (this.access & Opcodes.ACC_ENUM) != 0;
            if (isEnumClass && ("values".equals(name) || "valueOf".equals(name))) {
                return null;
            }
            boolean isCtor = "<init>".equals(name);
            methods.add(new UmlMethod(
                    isCtor ? simpleName(internalName) : name,
                    methodParams(signature, descriptor),
                    isCtor ? "" : methodReturn(signature, descriptor),
                    visibility(access),
                    (access & Opcodes.ACC_STATIC) != 0,
                    (access & Opcodes.ACC_ABSTRACT) != 0,
                    isCtor));
            return null;
        }

        UmlClass build() {
            boolean isEnum = (access & Opcodes.ACC_ENUM) != 0;
            boolean isAnnotation = (access & Opcodes.ACC_ANNOTATION) != 0;
            boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            ClassKind kind = isAnnotation ? ClassKind.ANNOTATION
                    : isEnum ? ClassKind.ENUM
                    : isInterface ? ClassKind.INTERFACE
                    : ClassKind.CLASS;
            String simple = internalName == null ? "" : simpleName(internalName);
            String pkg = internalName == null ? "" : packageName(internalName);
            return new UmlClass(internalName, pkg, simple, kind,
                    (access & Opcodes.ACC_ABSTRACT) != 0, true,
                    List.copyOf(fields), List.copyOf(methods),
                    superName, List.of(interfaces), List.copyOf(annotations));
        }
    }

    // ======================== 可见性 ========================

    private static Visibility visibility(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return Visibility.PUBLIC;
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            return Visibility.PROTECTED;
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE;
    }

    // ======================== 类型名渲染 ========================

    /** 字段类型：有泛型签名走签名渲染，否则描述符 */
    private static String readableType(String sigOrDesc) {
        try {
            if (sigOrDesc != null) {
                String rendered = renderTypeSignature(sigOrDesc);
                if (rendered != null && !rendered.isEmpty()) {
                    return rendered;
                }
            }
            if (sigOrDesc == null || sigOrDesc.isEmpty()) {
                return "";
            }
            return Type.getType(sigOrDesc).getClassName();
        } catch (RuntimeException e) {
            return sigOrDesc == null ? "" : sigOrDesc;
        }
    }

    private static List<String> methodParams(String signature, String descriptor) {
        List<String> fromDesc = new ArrayList<>();
        try {
            for (Type t : Type.getArgumentTypes(descriptor)) {
                fromDesc.add(t.getClassName());
            }
        } catch (RuntimeException e) {
            fromDesc.clear();
        }
        if (signature != null) {
            try {
                MethodSigParser parser = new MethodSigParser();
                new SignatureReader(signature).accept(parser);
                if (!parser.params().isEmpty()) {
                    return List.copyOf(parser.params());
                }
            } catch (RuntimeException ignored) {
                // 签名解析失败，回退描述符
            }
        }
        return List.copyOf(fromDesc);
    }

    private static String methodReturn(String signature, String descriptor) {
        try {
            if (signature != null) {
                MethodSigParser parser = new MethodSigParser();
                new SignatureReader(signature).accept(parser);
                String fromSig = parser.returnType();
                if (fromSig != null && !fromSig.isEmpty()) {
                    return fromSig;
                }
            }
            return Type.getReturnType(descriptor).getClassName();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 渲染纯类型签名（如 {@code Ljava/util/List<Lcom/Order;>;}）为可读点分文本 */
    private static String renderTypeSignature(String signature) {
        SigBuilder builder = new SigBuilder();
        new SignatureReader(signature).acceptType(builder);
        return builder.toString();
    }

    // ======================== 泛型签名渲染 ========================

    /** 无操作访问器，用于吞掉类级/方法级泛型声明（类型参数绑定） */
    private static final SignatureVisitor NO_OP = new SignatureVisitor(Opcodes.ASM9) {
    };

    /** 将一段类型签名渲染为 Java 风格文本；数组元素由独立子构建器负责并在 visitEnd 补 "[]" */
    private static class SigBuilder extends SignatureVisitor {

        private static final class Frame {
            boolean angleOpened;
            int args;
        }

        private final StringBuilder sb = new StringBuilder();
        private final Deque<Frame> frames = new ArrayDeque<>();

        SigBuilder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            // 忽略类型参数声明
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return NO_OP;
        }

        @Override
        public void visitBaseType(char descriptor) {
            sb.append(baseTypeName(descriptor));
        }

        @Override
        public void visitTypeVariable(String name) {
            sb.append(name);
        }

        @Override
        public void visitClassType(String name) {
            sb.append(name.replace('/', '.'));
            frames.push(new Frame());
        }

        @Override
        public void visitInnerClassType(String name) {
            sb.append('$').append(name);
        }

        @Override
        public void visitTypeArgument() {
            typeArgStart();
            sb.append('?');
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            typeArgStart();
            if (wildcard == SignatureVisitor.EXTENDS) {
                sb.append("? extends ");
            } else if (wildcard == SignatureVisitor.SUPER) {
                sb.append("? super ");
            }
            return this;
        }

        private void typeArgStart() {
            Frame f = frames.peek();
            if (f == null) {
                return;
            }
            if (f.args > 0) {
                sb.append(", ");
            } else {
                sb.append('<');
                f.angleOpened = true;
            }
            f.args++;
        }

        @Override
        public void visitEnd() {
            Frame f = frames.peek();
            if (f != null) {
                if (f.angleOpened) {
                    sb.append('>');
                }
                frames.pop();
            }
        }

        @Override
        public SignatureVisitor visitArrayType() {
            return new SignatureVisitor(Opcodes.ASM9) {
                private final SigBuilder element = new SigBuilder();

                @Override
                public void visitBaseType(char d) {
                    element.visitBaseType(d);
                }

                @Override
                public void visitTypeVariable(String n) {
                    element.visitTypeVariable(n);
                }

                @Override
                public SignatureVisitor visitArrayType() {
                    return new SignatureVisitor(Opcodes.ASM9) {
                        private final SigBuilder inner = new SigBuilder();

                        @Override
                        public void visitBaseType(char d) {
                            inner.visitBaseType(d);
                        }

                        @Override
                        public void visitTypeVariable(String n) {
                            inner.visitTypeVariable(n);
                        }

                        @Override
                        public SignatureVisitor visitArrayType() {
                            return new SignatureVisitor(Opcodes.ASM9) {
                                private final SigBuilder inner2 = new SigBuilder();

                                @Override
                                public void visitBaseType(char d) {
                                    inner2.visitBaseType(d);
                                }

                                @Override
                                public void visitTypeVariable(String n) {
                                    inner2.visitTypeVariable(n);
                                }

                                @Override
                                public SignatureVisitor visitArrayType() {
                                    return this;
                                }

                                @Override
                                public void visitClassType(String n) {
                                    inner2.visitClassType(n);
                                }

                                @Override
                                public void visitInnerClassType(String n) {
                                    inner2.visitInnerClassType(n);
                                }

                                @Override
                                public void visitTypeArgument() {
                                    inner2.visitTypeArgument();
                                }

                                @Override
                                public SignatureVisitor visitTypeArgument(char w) {
                                    return inner2.visitTypeArgument(w);
                                }

                                @Override
                                public void visitEnd() {
                                    sb.append(inner2).append("[]");
                                }
                            };
                        }

                        @Override
                        public void visitClassType(String n) {
                            inner.visitClassType(n);
                        }

                        @Override
                        public void visitInnerClassType(String n) {
                            inner.visitInnerClassType(n);
                        }

                        @Override
                        public void visitTypeArgument() {
                            inner.visitTypeArgument();
                        }

                        @Override
                        public SignatureVisitor visitTypeArgument(char w) {
                            return inner.visitTypeArgument(w);
                        }

                        @Override
                        public void visitEnd() {
                            sb.append(inner).append("[]");
                        }
                    };
                }

                @Override
                public void visitClassType(String n) {
                    element.visitClassType(n);
                }

                @Override
                public void visitInnerClassType(String n) {
                    element.visitInnerClassType(n);
                }

                @Override
                public void visitTypeArgument() {
                    element.visitTypeArgument();
                }

                @Override
                public SignatureVisitor visitTypeArgument(char w) {
                    return element.visitTypeArgument(w);
                }

                @Override
                public void visitEnd() {
                    sb.append(element).append("[]");
                }
            };
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    /**
     * 解析方法签名，逐个收集参数类型与返回类型。
     * 注意：ASM 对类型变量（{@code TT;}）与基元类型不会回调 visitEnd，
     * 因此依赖 {@link CaptureBuilder} 在「顶层类型完成」时收集。
     */
    private static final class MethodSigParser extends SignatureVisitor {

        private final List<String> params = new ArrayList<>();
        private String returnType = "";

        MethodSigParser() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            // 忽略类型参数声明
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitParameterType() {
            return new CaptureBuilder(params::add);
        }

        @Override
        public SignatureVisitor visitReturnType() {
            return new CaptureBuilder(s -> returnType = s);
        }

        List<String> params() {
            return List.copyOf(params);
        }

        String returnType() {
            return returnType;
        }
    }

    /** 类型渲染构建器：在顶层类型完成时（含无 visitEnd 的类型变量/基元类型/数组）向 sink 回调渲染文本 */
    private static final class CaptureBuilder extends SigBuilder {

        private final Consumer<String> sink;
        private int depth;

        CaptureBuilder(Consumer<String> sink) {
            super();
            this.sink = sink;
        }

        @Override
        public void visitBaseType(char descriptor) {
            super.visitBaseType(descriptor);
            if (depth == 0) {
                sink.accept(toString());
            }
        }

        @Override
        public void visitTypeVariable(String name) {
            super.visitTypeVariable(name);
            if (depth == 0) {
                sink.accept(toString());
            }
        }

        @Override
        public void visitClassType(String name) {
            depth++;
            super.visitClassType(name);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            if (depth > 0) {
                depth--;
            }
            if (depth == 0) {
                sink.accept(toString());
            }
        }

        @Override
        public SignatureVisitor visitArrayType() {
            // 注意：基元/类型变量的数组元素 ASM 不回调 visitEnd，将回退到描述符路径
            SignatureVisitor element = super.visitArrayType();
            return new DelegatingSignatureVisitor(element) {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    if (depth == 0) {
                        sink.accept(toString());
                    }
                }
            };
        }
    }

    /** 通用转发访问器：将全部签名事件原样转发给委托访问器，用于在数组子访问器外再包一层钩子 */
    private static class DelegatingSignatureVisitor extends SignatureVisitor {

        private final SignatureVisitor delegate;

        DelegatingSignatureVisitor(SignatureVisitor delegate) {
            super(Opcodes.ASM9);
            this.delegate = delegate;
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            delegate.visitFormalTypeParameter(name);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return delegate.visitClassBound();
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return delegate.visitInterfaceBound();
        }

        @Override
        public SignatureVisitor visitSuperclass() {
            return delegate.visitSuperclass();
        }

        @Override
        public SignatureVisitor visitInterface() {
            return delegate.visitInterface();
        }

        @Override
        public SignatureVisitor visitParameterType() {
            return delegate.visitParameterType();
        }

        @Override
        public SignatureVisitor visitReturnType() {
            return delegate.visitReturnType();
        }

        @Override
        public SignatureVisitor visitExceptionType() {
            return delegate.visitExceptionType();
        }

        @Override
        public void visitBaseType(char descriptor) {
            delegate.visitBaseType(descriptor);
        }

        @Override
        public void visitTypeVariable(String name) {
            delegate.visitTypeVariable(name);
        }

        @Override
        public SignatureVisitor visitArrayType() {
            return new DelegatingSignatureVisitor(delegate.visitArrayType());
        }

        @Override
        public void visitClassType(String name) {
            delegate.visitClassType(name);
        }

        @Override
        public void visitInnerClassType(String name) {
            delegate.visitInnerClassType(name);
        }

        @Override
        public void visitTypeArgument() {
            delegate.visitTypeArgument();
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            return delegate.visitTypeArgument(wildcard);
        }

        @Override
        public void visitEnd() {
            delegate.visitEnd();
        }
    }

    private static String baseTypeName(char d) {
        return switch (d) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'Z' -> "boolean";
            case 'V' -> "void";
            default -> String.valueOf(d);
        };
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private static String packageName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx < 0 ? "" : internalName.substring(0, idx).replace('/', '.');
    }
}
