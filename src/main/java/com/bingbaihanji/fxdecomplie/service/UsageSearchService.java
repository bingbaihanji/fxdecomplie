package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.UsageResult;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 用法搜索服务 — 通过 ASM 扫描字节码操作数查找类/成员的引用位置
 *
 * <p>逐类扫描工作区索引中的所有 class 字节码,在以下位置匹配目标：
 * <ul>
 *   <li>类继承/实现关系(superName、interfaces)</li>
 *   <li>字段访问指令(GETFIELD/PUTFIELD/GETSTATIC/PUTSTATIC)</li>
 *   <li>方法调用指令(INVOKEVIRTUAL/INVOKESPECIAL/INVOKESTATIC/INVOKEINTERFACE)</li>
 *   <li>类型操作指令(NEW/ANEWARRAY/CHECKCAST/INSTANCEOF)</li>
 *   <li>LDC 常量池中的 Type 引用</li>
 *   <li>invokedynamic 中的 Handle 参数</li>
 *   <li>字段/方法描述符中的对象类型引用</li>
 * </ul>
 *
 * <p>查询格式支持：
 * <ul>
 *   <li>全限定类名：{@code com.example.MyClass}</li>
 *   <li>简单类名：{@code MyClass}</li>
 *   <li>成员引用：{@code com.example.MyClass#methodName}</li>
 *   <li>内部类用 {@code $} 或 {@code .} 分隔均可</li>
 * </ul>
 *
 * @author bingbaihanji
 * @since 2026-06-18
 */
public final class UsageSearchService {

    private static final Logger log = LoggerFactory.getLogger(UsageSearchService.class);

    private UsageSearchService() {
        throw new AssertionError("utility class");
    }

    /**
     * 在工作区索引中搜索指定类/成员的全部引用位置
     *
     * @param index 工作区字节码索引
     * @param query 用户查询字符串,支持类名和 {@code 类名#成员名} 格式
     * @return 匹配的用法结果列表,未找到或查询无效时返回空列表
     */
    public static List<UsageResult> findUsages(WorkspaceIndex index, String query) {
        if (index == null || query == null || query.isBlank()) {
            return List.of();
        }
        Target target = Target.parse(query);
        if (!target.isValid()) {
            return List.of();
        }
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

    /**
     * 对单个类进行 ASM 字节码扫描,查找目标引用
     *
     * @param cls     工作区索引中的类条目
     * @param target  解析后的搜索目标(类名 + 可选成员名)
     * @param results 累积的匹配结果列表
     * @param seen    已见键集合,用于去重
     */
    private static void scanClass(ClassIndexEntry cls, Target target,
                                  List<UsageResult> results, Set<String> seen) {
        byte[] bytes = cls.bytes();
        if (bytes == null) {
            return;
        }
        addClassHeaderUsages(cls, target, results, seen, bytes);
        try {
            ClassReader reader = new ClassReader(bytes);
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
        } catch (Exception e) {
            log.debug("ASM 扫描类体失败: {}", cls.fullPath(), e);
        }
    }

    /**
     * 扫描类文件头信息(超类、接口、字段/方法描述符),补充 ASM visitor 无法覆盖的引用
     *
     * @param cls     工作区索引中的类条目
     * @param target  解析后的搜索目标
     * @param results 累积的匹配结果列表
     * @param seen    已见键集合,用于去重
     * @param bytes   类文件的原始字节数组
     */
    private static void addClassHeaderUsages(ClassIndexEntry cls, Target target,
                                             List<UsageResult> results, Set<String> seen,
                                             byte[] bytes) {
        ClassFileParser.tryParse(bytes).ifPresent(metadata -> {
            if (matchesClass(target, metadata.superName())) {
                add(results, seen, cls.fullPath(), 1,
                        UsageResult.UsageType.CLASS_REFERENCE,
                        "extends " + metadata.superName());
            }
            for (String iface : metadata.interfaces()) {
                if (matchesClass(target, iface)) {
                    add(results, seen, cls.fullPath(), 1,
                            UsageResult.UsageType.CLASS_REFERENCE,
                            "implements " + iface);
                }
            }
            for (ClassFileMetadata.MemberInfo field : metadata.fields()) {
                if (matchesDescriptor(target, field.descriptor())) {
                    add(results, seen, cls.fullPath(), 1,
                            UsageResult.UsageType.FIELD_ACCESS,
                            "field " + field.name() + " " + field.descriptor());
                }
            }
            for (ClassFileMetadata.MemberInfo method : metadata.methods()) {
                if (matchesDescriptor(target, method.descriptor())) {
                    add(results, seen, cls.fullPath(), 1,
                            UsageResult.UsageType.METHOD_CALL,
                            "method " + method.name() + method.descriptor());
                }
            }
        });
    }

    /**
     * 扫描 invokedynamic 中的 Bootstrap Method Handle,检查其所有者类/成员是否匹配目标
     *
     * @param cls         当前扫描的类条目
     * @param target      解析后的搜索目标
     * @param results     累积的匹配结果列表
     * @param seen        已见键集合,用于去重
     * @param currentLine 当前字节码行号
     * @param handle      invokedynamic 的 Bootstrap Method Handle
     */
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

    /**
     * 检查 ASM Type 是否匹配目标类,若匹配则添加到结果列表
     *
     * @param cls         当前扫描的类条目
     * @param target      解析后的搜索目标
     * @param results     累积的匹配结果列表
     * @param seen        已见键集合,用于去重
     * @param currentLine 当前字节码行号
     * @param type        ASM Type 实例(来自 LDC 或 multianewarray 指令)
     */
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

    /**
     * 从 ASM Type 中提取数组元素类型的内部名(internal name)
     * 例如 {@code String[][]} → {@code java/lang/String},非对象类型返回空字符串
     */
    private static String elementInternalName(Type type) {
        Type element = type;
        while (element != null && element.getSort() == Type.ARRAY) {
            element = element.getElementType();
        }
        return element != null && element.getSort() == Type.OBJECT
                ? element.getInternalName()
                : "";
    }

    /**
     * 判断字节码中的类内部名是否匹配搜索目标
     * 匹配策略：全限定名包含匹配 + 简单名双向包含匹配(支持混淆类名自动匹配)
     */
    private static boolean matchesClass(Target target, String owner) {
        if (owner == null || owner.isBlank()) {
            return false;
        }
        String normalizedOwner = owner.toLowerCase(Locale.ROOT).replace('$', '/');
        String simpleOwner = simpleName(normalizedOwner);
        return normalizedOwner.contains(target.classPart)
                || simpleOwner.contains(target.raw)
                || target.raw.contains(simpleOwner);
    }

    /**
     * 判断字节码中的成员(字段/方法)是否匹配搜索目标
     * 若查询包含 {@code #} 成员部分,则同时校验所有者类和成员名 否则只按简单名模糊匹配
     */
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

    /**
     * 检查 ASM 字段/方法描述符是否引用了目标类
     *
     * JVM 描述符中对象类型以 {@code L} 开头、{@code ;} 结尾,例如 {@code Ljava/lang/String;}
     * 本方法先将描述符整体转小写,使前缀 {@code L} 变为 {@code 'l'},再检查类路径或简单名是否匹配
     *
     * @param target     用户查询解析结果,{@code classPart} 已统一为小写内部路径(如 {@code java/lang/string})
     * @param descriptor ASM 描述符字符串(如 {@code (Ljava/lang/String;)V})
     * @return 描述符中任意对象类型引用是否匹配目标类
     */
    private static boolean matchesDescriptor(Target target, String descriptor) {
        if (descriptor == null || descriptor.isBlank() || target.classPart == null || target.classPart.isBlank()) {
            return false;
        }
        // ---- 统一小写以匹配 JVM L-prefix 对象描述符 ----
        // 'L' 是 JVM 对象类型前缀,小写后 'l' 与查询 classPart 同大小写,直接拼接匹配
        String lower = descriptor.toLowerCase(Locale.ROOT);

        // ---- 全路径匹配：描述符含 "ljava/lang/String;" 则匹配 classPart="java/lang/string" ----
        if (lower.contains('l' + target.classPart + ';')) {
            return true;
        }

        // ---- 简单名兼容匹配：查询 "String" 也能匹配 method(String) 的参数类型 ----
        // 从 classPart 提取最后一段作为简单名(如 "java/lang/string" → "string"),
        // 遍历描述符中每个 "l...;" 对象类型块,将其类的简单名与查询简单名比较
        int lastSlash = target.classPart.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? target.classPart.substring(lastSlash + 1) : target.classPart;
        if (!simpleName.isEmpty()) {
            int idx = 0;
            while ((idx = lower.indexOf('l', idx)) >= 0) {
                int semi = lower.indexOf(';', idx + 1);
                if (semi > 0) {
                    String descClass = lower.substring(idx + 1, semi);
                    int descSlash = descClass.lastIndexOf('/');
                    String descSimple = descSlash >= 0 ? descClass.substring(descSlash + 1) : descClass;
                    if (descSimple.equals(simpleName)) {
                        return true;
                    }
                }
                idx++;
            }
        }
        return false;
    }

    /**
     * 向结果列表中添加一条用法记录,自动去重
     *
     * @param results    累积的匹配结果列表
     * @param seen       已见键集合({@code path\nlineNumber\ntype\ntext} 格式)
     * @param sourcePath 源类全路径
     * @param lineNumber 行号
     * @param type       用法类型(类引用/字段访问/方法调用)
     * @param text       匹配文本描述
     */
    private static void add(List<UsageResult> results, Set<String> seen, String sourcePath,
                            int lineNumber, UsageResult.UsageType type, String text) {
        String key = sourcePath + '\n' + lineNumber + '\n' + type + '\n' + text;
        if (seen.add(key)) {
            results.add(new UsageResult(sourcePath, lineNumber, type, text));
        }
    }

    /** 从 JVM 内部名(如 {@code java/lang/String})提取简单类名(如 {@code String}) */
    private static String simpleName(String internalName) {
        return DecompilerContext.simpleName(internalName);
    }

    /** 将 ASM 操作码转为可读的 JVM 指令助记符 */
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

    /**
     * 用户查询解析结果 — 将原始输入拆分为类名部分和可选的成员名部分
     *
     * @param raw        原始查询字符串(已规范化：小写、分隔符统一为 {@code /})
     * @param classPart  类名部分(JVM 内部名格式,如 {@code com/example/myclass})
     * @param memberPart 成员名部分({@code #} 之后的内容),无成员时为空字符串
     */
    private record Target(String raw, String classPart, String memberPart) {

        /**
         * 解析用户查询字符串为 {@link Target}
         *
         * <p>支持的格式：
         * <ul>
         *   <li>{@code com.example.MyClass} — 全限定类名</li>
         *   <li>{@code MyClass} — 简单类名</li>
         *   <li>{@code com.example.MyClass#methodName} — 指定成员</li>
         *   <li>{@code MyClass#fieldName} — 简单名 + 成员</li>
         * </ul>
         *
         * @param query 用户原始输入
         * @return 解析后的 Target,无法解析时 classPart 为哨兵 {@code \0}
         */
        private static Target parse(String query) {
            String rawQuery = query.trim()
                    .replaceAll("\\.class$", "")
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
            String classQuery = rawQuery.replace('.', '/').replace('$', '/');
            String memberQuery = null;
            int hash = classQuery.lastIndexOf('#');
            if (hash >= 0) {
                memberQuery = classQuery.substring(hash + 1);
                classQuery = classQuery.substring(0, hash);
                if (classQuery.isBlank()) {
                    // 拒绝 "#" 和 "#member";无所有者的成员搜索会匹配所有类
                    classQuery = "\0"; // 哨兵值,不匹配任何类
                }
            }
            return new Target(rawQuery.replace('.', '/').replace('$', '/'), classQuery, memberQuery);
        }

        private boolean isValid() {
            return classPart != null && !classPart.isBlank() && !"\0".equals(classPart);
        }
    }
}
