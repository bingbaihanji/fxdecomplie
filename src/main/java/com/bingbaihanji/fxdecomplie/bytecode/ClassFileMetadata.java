package com.bingbaihanji.fxdecomplie.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight class file metadata that can be read without ASM.
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
