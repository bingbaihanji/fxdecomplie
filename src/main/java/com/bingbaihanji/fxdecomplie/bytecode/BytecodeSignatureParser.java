package com.bingbaihanji.fxdecomplie.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从 class 字节码中提取泛型签名信息,用于增强反编译源码的 Ctrl+Click 导航
 *
 * <p>当反编译器输出 {@code ServiceImpl<AjglMapper, a>} 时,简单名 {@code a} 可能对应
 * 多个同名类(如 {@code com.pig4cloud.domain.a} 和 {@code com.pig4cloud.service.a}) 
 * 本解析器从字节码的 Signature 属性中提取实际的泛型类型参数,为每个简单名提供
 * 精确的全限定类名映射 </p>
 *
 * <h3>原理</h3>
 * <p>字节码中的类签名存储了完整的泛型信息,例如：</p>
 * <pre>
 * Lcom/baomidou/.mybatisplus/extension/service/impl/ServiceImpl&lt;Lcom/pig4cloud/domain/AjglMapper;Lcom/pig4cloud/domain/a;&gt;;
 * </pre>
 * <p>通过解析此签名,可以确定 {@code a} 实际指向 {@code com/pig4cloud/domain/a} </p>
 *
 * @author bingbaihanji
 * @date 2026-07-08
 */
public final class BytecodeSignatureParser {

    private static final Logger log = LoggerFactory.getLogger(BytecodeSignatureParser.class);

    private BytecodeSignatureParser() {
        throw new AssertionError("utility class");
    }

    /**
     * 从 class 字节码中提取所有泛型类型参数的全限定名列表
     *
     * <p>返回一个扁平列表,包含按源码中出现顺序排列的所有类型参数 
     * 例如对于 {@code ServiceImpl<AjglMapper, a>},返回
     * ["com/pig4cloud/domain/AjglMapper", "com/pig4cloud/domain/a"] </p>
     *
     * <p>同时返回父类类型参数列表和每个接口的类型参数列表,调用方按顺序
     * 与反编译源码中的 extends/implements 子句匹配 </p>
     *
     * @param classBytes class 文件原始字节
     * @return 类型参数列表(内部名格式,如 "com/pig4cloud/domain/a"),
     *         解析失败或无泛型信息时返回空列表
     */

    /**
     * 从 class 字节码中提取父类和接口的泛型类型参数,按 extends/implements 顺序排列
     *
     * <p>返回列表的结构：第一个元素对应父类(superclass)的类型参数列表,
     * 后续元素对应每个接口的类型参数列表 </p>
     *
     * @param classBytes class 文件原始字节
     * @return 每个父类型(extends 或 implements)的类型参数列表,
     *         解析失败时返回空列表
     */
    public static List<List<String>> extractTypeArgumentsByOwner(byte[] classBytes) {
        if (classBytes == null || classBytes.length < 10) {
            return List.of();
        }
        try {
            TypeArgumentExtractor extractor = new TypeArgumentExtractor();
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES
                    | ClassReader.SKIP_DEBUG);
            return extractor.getTypeArgumentsByOwner();
        } catch (Exception e) {
            log.debug("字节码签名解析失败", e);
            return List.of();
        }
    }

    /**
     * ASM ClassVisitor 实现,提取类签名中的泛型类型参数
     */
    private static final class TypeArgumentExtractor extends ClassVisitor {

        /** 按父类型分组的类型参数列表([0]=superclass, [1..]=interfaces) */
        private final List<List<String>> typeArgumentsByOwner = new ArrayList<>();
        private String classSignature;
        private String superName;
        private List<String> interfaceNames;

        TypeArgumentExtractor() {
            super(Opcodes.ASM9);
        }


        public String getSuperName() {
            return superName;
        }

        public List<String> getInterfaceNames() {
            return interfaceNames;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.classSignature = signature;
            this.superName = superName;
            this.interfaceNames = interfaces != null ? List.of(interfaces) : List.of();
            // 若无泛型签名,直接用非泛型父类/接口
            if (signature == null) {
                if (superName != null) {
                    typeArgumentsByOwner.add(List.of());
                }
                if (interfaces != null) {
                    for (String iface : interfaces) {
                        typeArgumentsByOwner.add(List.of());
                    }
                }
            }
        }

        @Override
        public void visitEnd() {
            if (classSignature != null) {
                parseClassSignature(classSignature);
            }
        }

        List<List<String>> getTypeArgumentsByOwner() {
            return Collections.unmodifiableList(typeArgumentsByOwner);
        }

        /**
         * 解析类签名,提取父类和接口的类型参数
         *
         * <p>类签名格式示例：</p>
         * <pre>
         * Lcom/baomidou/.../ServiceImpl&lt;Lcom/pig4cloud/domain/AjglMapper;Lcom/pig4cloud/domain/a;&gt;;
         * Ljava/lang/Object;
         * Lcom/pig4cloud/service/a;
         * </pre>
         * <p>即：{@code <superclass_signature> <interface_signatures...>}</p>
         */
        private void parseClassSignature(String sig) {
            int pos = 0;

            // 解析父类签名
            List<String> superArgs = parseParameterizedClassRef(sig, pos);
            typeArgumentsByOwner.add(superArgs);

            // 跳过父类签名到下一个类引用
            pos = skipClassRef(sig, 0);
            if (pos < 0) {
                return;
            }

            // 解析接口签名
            while (pos < sig.length()) {
                char c = sig.charAt(pos);
                if (c == 'L') {
                    List<String> ifaceArgs = parseParameterizedClassRef(sig, pos);
                    typeArgumentsByOwner.add(ifaceArgs);
                    pos = skipClassRef(sig, pos);
                    if (pos < 0) {
                        break;
                    }
                } else {
                    pos++;
                }
            }
        }

        /**
         * 从指定位置解析一个带泛型参数的类引用,返回其类型参数列表
         *
         * <p>格式：{@code Lpkg/ClassName<T1;T2;>;} 或 {@code Lpkg/ClassName;}</p>
         */
        private List<String> parseParameterizedClassRef(String sig, int start) {
            List<String> args = new ArrayList<>();
            if (start >= sig.length() || sig.charAt(start) != 'L') {
                return args;
            }

            // 找到类名结束位置('<' 或 ';')
            int pos = start + 1;
            while (pos < sig.length()) {
                char c = sig.charAt(pos);
                if (c == '<' || c == ';') {
                    break;
                }
                if (c == '/') {
                    pos++; // 跳过包分隔符
                }
                pos++;
            }

            if (pos >= sig.length()) {
                return args;
            }

            // 检查是否有泛型参数
            if (sig.charAt(pos) == '<') {
                pos++; // 跳过 '<'
                args = parseTypeArguments(sig, pos);
            }
            return args;
        }

        /**
         * 解析尖括号内的类型参数列表
         *
         * <p>格式：{@code Lcom/example/Foo;Lcom/example/Bar;T}</p>
         */
        private List<String> parseTypeArguments(String sig, int start) {
            List<String> args = new ArrayList<>();
            int pos = start;
            while (pos < sig.length()) {
                char c = sig.charAt(pos);
                if (c == '>') {
                    break; // 类型参数列表结束
                }

                if (c == 'L') {
                    // 提取类引用的内部名(L 前缀表示真实类引用,类型变量使用 T 前缀已在上方处理)
                    String internalName = extractClassInternalName(sig, pos);
                    if (internalName != null) {
                        args.add(internalName);
                    }
                    // 跳过这个类引用(含泛型参数)
                    pos = skipClassRef(sig, pos);
                    if (pos < 0) {
                        break;
                    }
                } else if (c == 'T') {
                    // 类型变量(如 T, E),跳过
                    int semi = sig.indexOf(';', pos);
                    pos = semi >= 0 ? semi + 1 : sig.length();
                } else if (c == '*' || c == '+' || c == '-') {
                    // 通配符
                    pos++;
                } else {
                    // 基本类型(I, J, Z 等)
                    pos++;
                }
            }
            return args;
        }

        /**
         * 从 'L' 开始提取类引用的内部名(不含泛型参数)
         *
         * <p>例如从 {@code Lcom/pig4cloud/domain/a;} 提取 {@code com/pig4cloud/domain/a}</p>
         */
        private String extractClassInternalName(String sig, int start) {
            if (start >= sig.length() || sig.charAt(start) != 'L') {
                return null;
            }
            int end = start + 1;
            while (end < sig.length()) {
                char c = sig.charAt(end);
                if (c == ';' || c == '<') {
                    break;
                }
                end++;
            }
            return sig.substring(start + 1, end).replace('/', '.');
        }

        /**
         * 跳过一个完整的类引用(含泛型参数和结尾分号)
         *
         * @return 跳过后的下一个位置,-1 表示格式错误
         */
        private int skipClassRef(String sig, int start) {
            if (start >= sig.length() || sig.charAt(start) != 'L') {
                return -1;
            }
            int pos = start + 1;
            int depth = 0; // 泛型尖括号深度
            while (pos < sig.length()) {
                char c = sig.charAt(pos);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    depth--;
                } else if (c == ';' && depth == 0) {
                    return pos + 1;
                }
                pos++;
            }
            return pos;
        }

    }
}
