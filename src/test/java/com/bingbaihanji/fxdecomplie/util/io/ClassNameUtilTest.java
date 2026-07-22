package com.bingbaihanji.fxdecomplie.util.io;

import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassNameUtilTest {

    @Test
    void stripsSpringBootFatJarClassPrefixAfterArchiveSeparator() {
        assertEquals("com/example/FileService",
                ClassNameUtil.stripContainerClassPrefix(
                        "boot.jar:BOOT-INF/classes/com/example/FileService.class"));
    }

    @Test
    void stripsNestedDependencyJarPrefix() {
        assertEquals("ch/qos/logback/classic/Logger",
                ClassNameUtil.stripContainerClassPrefix(
                        "boot.jar:BOOT-INF/lib/logback-classic.jar:ch/qos/logback/classic/Logger.class"));
    }
}
