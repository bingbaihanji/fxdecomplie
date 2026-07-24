package com.bingbaihanji.classgraph.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassGraphVersion")
class ClassGraphVersionTest {

    @Test
    @DisplayName("version is non-null and non-empty")
    void versionIsValid() {
        assertNotNull(ClassGraphVersion.VERSION);
        assertFalse(ClassGraphVersion.VERSION.isBlank());
    }

    @Test
    @DisplayName("JDK range is reasonable")
    void jdkRangeIsReasonable() {
        assertTrue(ClassGraphVersion.MIN_JDK_VERSION >= 7);
        assertTrue(ClassGraphVersion.MAX_JDK_VERSION >= ClassGraphVersion.MIN_JDK_VERSION);
    }

    @Test
    @DisplayName("versionString contains version number")
    void versionString() {
        String str = ClassGraphVersion.versionString();
        assertTrue(str.contains(ClassGraphVersion.VERSION));
        assertTrue(str.contains("ClassGraph"));
        assertTrue(str.contains("JDK"));
    }

    @Test
    @DisplayName("class is a utility class")
    void isUtilityClass() {
        // 验证构造函数是 private 的
        var ctors = ClassGraphVersion.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertFalse(ctors[0].canAccess(null));
    }
}
