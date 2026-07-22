/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.classloaderhandler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** ClassLoaderHandler 类的注册表 */
public class ClassLoaderHandlerRegistry {
    /**
     * 默认的 ClassLoaderHandler 列表如果向 ClassGraph 添加了 ClassLoaderHandler，则应将其添加到此列表中
     */
    public static final List<ClassLoaderHandlerRegistryEntry> CLASS_LOADER_HANDLERS = //
            Collections.unmodifiableList(Arrays.asList(
                    // ClassGraph 处理的其他 ClassLoader 的 ClassLoaderHandler
                    new ClassLoaderHandlerRegistryEntry(new AntClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new EquinoxClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new EquinoxContextFinderClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new FelixClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new JBossClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new WeblogicClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new WebsphereLibertyClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new WebsphereTraditionalClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new OSGiDefaultClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new SpringBootRestartClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new TomcatWebappClassLoaderBaseHandler()),
                    new ClassLoaderHandlerRegistryEntry(new CxfContainerClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new PlexusClassWorldsClassRealmClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new QuarkusClassLoaderHandler()),
                    new ClassLoaderHandlerRegistryEntry(new UnoOneJarClassLoaderHandler()),

                    // 用于 PARENT_LAST 委托顺序的单元测试
                    new ClassLoaderHandlerRegistryEntry(new ParentLastDelegationOrderTestClassLoaderHandler()),

                    // JPMS 支持(此处理器不执行任何操作，因为模块是单独处理的)
                    new ClassLoaderHandlerRegistryEntry(new JPMSClassLoaderHandler()),

                    // Java 7/8 URLClassLoader 支持(应位于倒数第二位，以便 URLClassLoader 的子类由上面更具体的处理器处理)
                    new ClassLoaderHandlerRegistryEntry(new URLClassLoaderHandler()),

                    // 用于从外部嵌套扫描委托给 ClassGraphClassLoader 实例的占位符
                    new ClassLoaderHandlerRegistryEntry(new ClassGraphClassLoaderHandler())

                    // FallbackClassLoaderHandler.class 在下面单独注册
            ));

    /** 回退类加载器处理器 */
    public static final ClassLoaderHandlerRegistryEntry FALLBACK_HANDLER = new ClassLoaderHandlerRegistryEntry(
            new FallbackClassLoaderHandler());

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 应自动将其 JAR 文件添加到类路径的库目录(用于补偿某些类加载器未将这些 JAR 文件显式列为类路径元素的情况)
     */
    public static final String[] AUTOMATIC_LIB_DIR_PREFIXES = {
            // Spring-Boot
            // https://docs.spring.io/spring-boot/docs/2.3.0.RELEASE/reference/html/appendix-executable-jar-format.html
            "BOOT-INF/lib/",
            // Tomcat
            "WEB-INF/lib/", "WEB-INF/lib-provided/",
            // OSGi
            "META-INF/lib/",
            // Tomcat 及其他
            "lib/",
            // 扩展目录
            "lib/ext/",
            // UnoJar and One-Jar
            "main/" //
    };

    /**
     * 自动类文件前缀(用于补偿某些类加载器未将这些前缀显式列为类路径元素 URL 或路径的情况)
     */
    public static final String[] AUTOMATIC_PACKAGE_ROOT_PREFIXES = {
            // Ant、Tomcat 及其他
            "classes/",
            // Ant
            "test-classes/",
            // Spring-Boot
            "BOOT-INF/classes/",
            // Tomcat
            "WEB-INF/classes/",};

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     */
    private ClassLoaderHandlerRegistry() {
        // 不可构造
    }

    /**
     * 完全限定类加载器类名与可处理它们的 ClassLoaderHandler 的配对列表
     */
    public static class ClassLoaderHandlerRegistryEntry {
        /** ClassLoaderHandler 实例 */
        public final ClassLoaderHandler classLoaderHandler;

        /**
         * 构造函数
         *
         * @param classLoaderHandler
         *            ClassLoaderHandler 实例
         */
        private ClassLoaderHandlerRegistryEntry(final ClassLoaderHandler classLoaderHandler) {
            this.classLoaderHandler = classLoaderHandler;
        }

        /**
         * 调用关联的 {@link ClassLoaderHandler} 的 canHandle 方法
         *
         * @param classLoader {@link ClassLoader}
         * @param log  日志
         * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true
         */
        public boolean canHandle(final Class<?> classLoader, final LogNode log) {
            return classLoaderHandler.canHandle(classLoader, log);
        }

        /**
         * 调用关联的 {@link ClassLoaderHandler} 的 findClassLoaderOrder 方法
         *
         * @param classLoader {@link ClassLoader}
         * @param classLoaderOrder  {@link ClassLoaderOrder} 对象
         * @param log 日志
         */
        public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                         final LogNode log) {
            classLoaderHandler.findClassLoaderOrder(classLoader, classLoaderOrder, log);
        }

        /**
         * 调用关联的 {@link ClassLoaderHandler} 的 findClasspathOrder 方法
         *
         * @param classLoader  {@link ClassLoader}
         * @param classpathOrder  {@link ClasspathOrder} 对象
         * @param scanSpec {@link ScanSpec}
         * @param log 日志
         */
        public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                       final ScanSpec scanSpec, final LogNode log) {
            classLoaderHandler.findClasspathOrder(classLoader, classpathOrder, scanSpec, log);
        }
    }
}
