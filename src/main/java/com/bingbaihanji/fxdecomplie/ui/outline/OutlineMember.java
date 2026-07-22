package com.bingbaihanji.fxdecomplie.ui.outline;

/**
 * 大纲成员记录,从反编译源码或字节码分析中提取的字段、方法、内部类或方法实现
 *
 * @param name                成员名称
 * @param type                成员类型
 * @param modifiers           修饰符(如 "public static")
 * @param lineNumber          行号(1-based)
 * @param targetClassName     目标类全限定名(仅 IMPLEMENTATION 类型使用,表示实现/重写此方法的类)
 * @param targetClassPath     目标类文件路径(仅 IMPLEMENTATION 类型使用)
 * @param methodDescriptor    方法 JVM 描述符(仅 METHOD 和 IMPLEMENTATION 类型使用,用于精确匹配)
 * @author bingbaihanji
 * @date 2026-06-17
 */
public record OutlineMember(
        String name,
        MemberType type,
        String modifiers,
        int lineNumber,
        String targetClassName,
        String targetClassPath,
        String methodDescriptor
) {

    /**
     * 向后兼容的构造器 —— 用于顶层成员(字段、方法、内部类),
     * 不包含实现类相关信息
     */
    public OutlineMember(String name, MemberType type, String modifiers, int lineNumber) {
        this(name, type, modifiers, lineNumber, null, null, null);
    }

    /**
     * 创建方法实现节点(IMPLEMENTATION 类型)
     *
     * @param methodName        方法名称
     * @param implementingClass 实现类的内部名称(如 com/example/UserServiceImpl)
     * @param classPath         实现类的文件路径
     * @param descriptor        方法 JVM 描述符
     * @return 实现节点
     */
    public static OutlineMember implementation(String methodName, String implementingClass,
                                               String classPath, String descriptor) {
        String simpleClassName = simpleName(implementingClass);
        return new OutlineMember(methodName, MemberType.IMPLEMENTATION, "",
                -1, simpleClassName, classPath, descriptor);
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    /**
     * 返回带方法描述符的新成员实例
     *
     * @param descriptor JVM 方法描述符
     * @return 更新后的成员
     */
    public OutlineMember withMethodDescriptor(String descriptor) {
        return new OutlineMember(name, type, modifiers, lineNumber,
                targetClassName, targetClassPath, descriptor);
    }

    public enum MemberType {FIELD, METHOD, INNER_CLASS, IMPLEMENTATION}
}
