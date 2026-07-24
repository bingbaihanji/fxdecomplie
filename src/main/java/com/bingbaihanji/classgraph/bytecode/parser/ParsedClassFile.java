package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.Collections;
import java.util.List;

/**
 * 解析后的 class 文件 DTO — 包含从 class 文件提取的所有结构化信息。
 *
 * <p>不可变。由 {@code ClassFileParser} 编排器通过各 Section Reader 构建。
 * 与 {@code ClassParser} 类的区别在于：这是纯数据对象，不包含任何解析逻辑。</p>
 *
 * @param minorVersion     次版本号
 * @param majorVersion     主版本号
 * @param accessFlags      类访问标志
 * @param internalName     内部名称（如 "java/lang/String"）
 * @param superName        父类内部名称，Object 为 null
 * @param interfaceNames   直接实现的接口列表
 * @param fields           解析后的字段列表
 * @param methods          解析后的方法列表
 * @param annotations      类级别注解
 * @param signature        泛型签名（可为 null）
 * @param sourceFile       源文件名（可为 null）
 * @param referencedClasses 引用的外部类名集合
 * @param constantPoolCount 常量池条目数
 */
public record ParsedClassFile(
    int minorVersion,
    int majorVersion,
    int accessFlags,
    String internalName,
    String superName,
    List<String> interfaceNames,
    List<ParsedField> fields,
    List<ParsedMethod> methods,
    List<ParsedAnnotation> annotations,
    String signature,
    String sourceFile,
    List<String> referencedClasses,
    int constantPoolCount
) {
    public ParsedClassFile {
        interfaceNames = interfaceNames == null ? List.of()
            : Collections.unmodifiableList(interfaceNames);
        fields = fields == null ? List.of()
            : Collections.unmodifiableList(fields);
        methods = methods == null ? List.of()
            : Collections.unmodifiableList(methods);
        annotations = annotations == null ? List.of()
            : Collections.unmodifiableList(annotations);
        referencedClasses = referencedClasses == null ? List.of()
            : Collections.unmodifiableList(referencedClasses);
    }

    /** 是否接口 */
    public boolean isInterface() { return (accessFlags & 0x0200) != 0; }
    /** 是否注解 */
    public boolean isAnnotation() { return (accessFlags & 0x2000) != 0; }
    /** 是否枚举 */
    public boolean isEnum() { return (accessFlags & 0x4000) != 0; }
    /** 是否为 JDK 模块描述符 */
    public boolean isModuleInfo() {
        return "module-info".equals(internalName)
            || internalName != null && internalName.endsWith("/module-info");
    }
}
