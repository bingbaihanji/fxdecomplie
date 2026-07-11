package com.bingbaihanji.fxdecomplie.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * 轻量级 class 文件元数据,可在无 ASM 的情况下读取
 * <p>
 * 该记录包含了 class 文件的基本结构信息：版本号、访问标志、类名、父类名、接口列表、
 * 常量池计数、字段列表和方法列表用于快速获取类文件摘要信息,无需完整解析
 *
 * @author bingbaihanji
 */
public record ClassFileMetadata(
        int minorVersion,
        int majorVersion,
        int accessFlags,
        String internalName,
        String superName,
        List<String> interfaces,
        int constantPoolCount,
        List<MemberInfo> fields,
        List<MemberInfo> methods
) {

    /**
     * 紧凑构造函数,对传入的集合类型组件进行防御性拷贝,
     * 确保元数据对象的不可变性
     */
    public ClassFileMetadata {
        Objects.requireNonNull(internalName, "internalName");
        interfaces = List.copyOf(interfaces);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }

    /**
     * 类成员的元数据信息(字段或方法)
     *
     * @param accessFlags 访问标志(public/private/static 等)
     * @param name        成员名称
     * @param descriptor  类型描述符(如方法签名字符串)
     */
    public record MemberInfo(int accessFlags, String name, String descriptor) {

        /**
         * 紧凑构造函数,对描述符做空值保护,确保不会为 null
         */
        public MemberInfo {
            Objects.requireNonNull(name, "name");
            descriptor = descriptor == null ? "" : descriptor;
        }
    }
}
