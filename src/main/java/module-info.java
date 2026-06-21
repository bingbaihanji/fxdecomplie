module fxdecomplie {
    // JavaFX 模块
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.web;
    requires javafx.graphics;

    // 日志模块
    requires org.slf4j;
    requires ch.qos.logback.core;
    requires ch.qos.logback.classic;
    requires java.desktop;
    requires java.logging;
    requires java.sql;
    requires com.github.benmanes.caffeine;
    requires org.objectweb.asm;
    requires org.objectweb.asm.util;
    requires com.google.gson;
    requires jfx.incubator.richtext;
    requires jd.core;
    requires vineflower;
    requires procyon.compilertools;
    requires cfr;
    requires com.sun.jna.platform;
    requires com.sun.jna;

    // 导出 decompiler 包供 Gson 访问枚举
    exports com.bingbaihanji.fxdecomplie.decompiler;
    // 导出 JavaFX 原生窗口工具包
    exports com.bingbaihanji.windows.platform;
    // 导出 JavaFX 窗口平台工具包
    exports com.bingbaihanji.windows.jfx;

    // 运行时反射访问
    opens com.bingbaihanji.fxdecomplie to javafx.graphics;
    opens com.bingbaihanji.fxdecomplie.config to com.google.gson;
    opens com.bingbaihanji.fxdecomplie.model to com.google.gson;

    // 开放 decompiler 包给 Gson(用于枚举反序列化)
    opens com.bingbaihanji.fxdecomplie.decompiler to com.google.gson;

    // 开放 native.win32 包给 JNA(用于 Structure 反射读取字段)
    opens com.bingbaihanji.windows.platform.win32 to com.sun.jna;
}
