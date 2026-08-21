package com.bingbaihanji.fxdecomplie.ui.uml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UmlTypeNameUtilTest {

    @Test
    void getElementType_extractsArrayAndGenericElement() {
        assertEquals("Order", UmlTypeNameUtil.getElementType("Order[]"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("java.util.List<Order>"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("java.util.Map<String, List<Order>>"));
        assertEquals("X", UmlTypeNameUtil.getElementType("java.util.List<? extends X>"));
        assertEquals("Y", UmlTypeNameUtil.getElementType("java.util.List<? super Y>"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("Order"));
    }

    @Test
    void isCollectionOrArray_detectsArrayAndGenerics() {
        assertTrue(UmlTypeNameUtil.isCollectionOrArray("Order[]"));
        assertTrue(UmlTypeNameUtil.isCollectionOrArray("java.util.List<Order>"));
        assertFalse(UmlTypeNameUtil.isCollectionOrArray("Order"));
    }

    @Test
    void isUMLClassType_filtersJdkAndCoreTypes() {
        assertTrue(UmlTypeNameUtil.isUMLClassType("com.example.Order"));
        assertTrue(UmlTypeNameUtil.isUMLClassType("java.util.List<com.example.Order>"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("String"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("java.util.List"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("java.lang.String"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("int"));
    }

    @Test
    void isJdk_detectsInternalAndDottedJdkNames() {
        assertTrue(UmlTypeNameUtil.isJdk("java/lang/Object"));
        assertTrue(UmlTypeNameUtil.isJdk("java.util.ArrayList"));
        assertTrue(UmlTypeNameUtil.isJdk("javax.swing.JPanel"));
        assertTrue(UmlTypeNameUtil.isJdk("com/sun/Proxy"));
        assertFalse(UmlTypeNameUtil.isJdk("com/example/Order"));
    }
}
