package com.bingbaihanji.fxdecomplie.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * 轻量级 class 文件元数据,可在无 ASM 的情况下读取
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

    public ClassFileMetadata {
        Objects.requireNonNull(internalName, "internalName");
        interfaces = List.copyOf(interfaces);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }

    public record MemberInfo(int accessFlags, String name, String descriptor) {

        public MemberInfo {
            Objects.requireNonNull(name, "name");
            descriptor = descriptor == null ? "" : descriptor;
        }
    }
}
