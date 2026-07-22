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
package com.bingbaihanji.classgraph.utils;

import com.bingbaihanji.classgraph.core.ClassGraph;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** 查找 ClassGraph 的版本号和 JDK 的版本 */
public final class VersionFinder {

    /** 操作系统类型 */
    public static final OperatingSystem OS;
    /** Java 版本字符串 */
    public static final String JAVA_VERSION = getProperty("java.version");
    /** Java 主版本号 -- 7 表示 "1.7"，8 表示 "1.8.0_244"，9 表示 "9"，11 表示 "11-ea" 等 */
    public static final int JAVA_MAJOR_VERSION;
    /** Java 次版本号 -- "11.0.4" 则为 0 */
    public static final int JAVA_MINOR_VERSION;
    /** Java 子版本号 -- "11.0.4" 则为 4 */
    public static final int JAVA_SUB_VERSION;
    /** Java 是否为 EA 版本 -- "11-ea" 等则为 true */
    public static final boolean JAVA_IS_EA_VERSION;
    /** ClassGraph 的 Maven 包 */
    private static final String MAVEN_PACKAGE = "com.bingbaihanji.classgraph.core";
    /** ClassGraph 的 Maven 构件 */
    private static final String MAVEN_ARTIFACT = "classgraph";

    static {
        int javaMajorVersion = 0;
        int javaMinorVersion = 0;
        int javaSubVersion = 0;
        final List<Integer> versionParts = new ArrayList<>();
        if (JAVA_VERSION != null) {
            for (final String versionPart : JAVA_VERSION.split("[^0-9]+")) {
                try {
                    versionParts.add(Integer.parseInt(versionPart));
                } catch (final NumberFormatException e) {
                    // 跳过
                }
            }
            if (!versionParts.isEmpty() && versionParts.get(0) == 1) {
                // 1.7 或 1.8 -> 7 或 8
                versionParts.remove(0);
            }
            if (versionParts.isEmpty()) {
                throw new RuntimeException("Could not determine Java version: " + JAVA_VERSION);
            }
            javaMajorVersion = versionParts.get(0);
            if (versionParts.size() > 1) {
                javaMinorVersion = versionParts.get(1);
            }
            if (versionParts.size() > 2) {
                javaSubVersion = versionParts.get(2);
            }
        }
        JAVA_MAJOR_VERSION = javaMajorVersion;
        JAVA_MINOR_VERSION = javaMinorVersion;
        JAVA_SUB_VERSION = javaSubVersion;
        JAVA_IS_EA_VERSION = JAVA_VERSION != null && JAVA_VERSION.endsWith("-ea");
    }

    static {
        final String osName = getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
        if (File.separatorChar == '\\') {
            OS = OperatingSystem.Windows;
        } else if (osName == null) {
            OS = OperatingSystem.Unknown;
        } else if (osName.contains("win")) {
            OS = OperatingSystem.Windows;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            OS = OperatingSystem.MacOSX;
        } else if (osName.contains("nux")) {
            OS = OperatingSystem.Linux;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            OS = OperatingSystem.Solaris;
        } else if (osName.contains("bsd")) {
            OS = OperatingSystem.Unix;
        } else if (osName.contains("nix") || osName.contains("aix")) {
            OS = OperatingSystem.Unix;
        } else {
            OS = OperatingSystem.Unknown;
        }
    }

    /**
     * 构造方法
     */
    private VersionFinder() {
        // 不可构造
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取系统属性(如果抛出 SecurityException 则返回 null)
     *
     * @param propName
     *            属性名称
     * @return 属性值
     */
    public static String getProperty(final String propName) {
        try {
            return System.getProperty(propName);
        } catch (final SecurityException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取系统属性(如果抛出 SecurityException 则返回 null)
     *
     * @param propName
     *            属性名称
     * @param defaultVal
     *            属性的默认值
     * @return 属性值，如果属性未定义则返回默认值
     */
    public static String getProperty(final String propName, final String defaultVal) {
        try {
            return System.getProperty(propName, defaultVal);
        } catch (final SecurityException e) {
            return defaultVal;
        }
    }

    /**
     * 获取 ClassGraph 的版本号
     *
     * @return ClassGraph 的版本号
     */
    public static synchronized String getVersion() {
        // 尝试从 pom.xml 获取版本号(在 Eclipse 中运行时可用)
        final Class<?> cls = ClassGraph.class;
        try {
            final String className = cls.getName();
            final URL classpathResource = cls.getResource("/" + JarUtils.classNameToClassfilePath(className));
            if (classpathResource != null) {
                final Path absolutePackagePath = Paths.get(classpathResource.toURI()).getParent();
                final int packagePathSegments = className.length() - className.replace(".", "").length();
                // 从路径中移除包段
                Path path = absolutePackagePath;
                for (int i = 0; i < packagePathSegments && path != null; i++) {
                    path = path.getParent();
                }
                // 再向上移除两到三个级别，以处理 "bin" 或 "target/classes"
                for (int i = 0; i < 3 && path != null; i++, path = path.getParent()) {
                    final Path pom = path.resolve("pom.xml");
                    try (InputStream is = Files.newInputStream(pom)) {
                        final Document doc = getSecureDocumentBuilderFactory().newDocumentBuilder().parse(is);
                        doc.getDocumentElement().normalize();
                        String version = (String) getSecureXPathFactory().newXPath().compile("/project/version")
                                .evaluate(doc, XPathConstants.STRING);
                        if (version != null) {
                            version = version.trim();
                            if (!version.isEmpty()) {
                                return version;
                            }
                        }
                    } catch (final IOException e) {
                        // 未找到
                    }
                }
            }
        } catch (final Exception e) {
            // 忽略
        }

        // 尝试从 JAR 的 META-INF 目录中的 Maven 属性获取版本号
        try (InputStream is = cls.getResourceAsStream(
                "/META-INF/maven/" + MAVEN_PACKAGE + "/" + MAVEN_ARTIFACT + "/pom.properties")) {
            if (is != null) {
                final Properties p = new Properties();
                p.load(is);
                final String version = p.getProperty("version", "").trim();
                if (!version.isEmpty()) {
                    return version;
                }
            }
        } catch (final IOException e) {
            // 忽略
        }

        // 回退到使用 Java API(版本号从 MANIFEST.MF 获取)
        final Package pkg = cls.getPackage();
        if (pkg != null) {
            String version = pkg.getImplementationVersion();
            if (version == null) {
                version = "";
            }
            version = version.trim();
            if (version.isEmpty()) {
                version = pkg.getSpecificationVersion();
                if (version == null) {
                    version = "";
                }
                version = version.trim();
            }
            if (!version.isEmpty()) {
                return version;
            }
        }
        return "unknown";
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 提供 XXE 安全的 DocumentBuilder Factory 的辅助方法
     *
     * 参考 - https://gist.github.com/AlainODea/1779a7c6a26a5c135280bc9b3b71868f
     *
     * 参考 - https://rules.sonarsource.com/java/tag/owasp/RSPEC-2755
     *
     * @return DocumentBuilderFactory
     * @throws ParserConfigurationException
     */
    private static DocumentBuilderFactory getSecureDocumentBuilderFactory() throws ParserConfigurationException {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setXIncludeAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return dbf;
    }

    /**
     * 提供 XXE 安全的 XPathFactory Factory 的辅助方法
     *
     * 参考 - https://rules.sonarsource.com/java/tag/owasp/RSPEC-2755
     *
     * @return XPathFactory
     * @throws XPathFactoryConfigurationException
     */
    private static XPathFactory getSecureXPathFactory() throws XPathFactoryConfigurationException {
        final XPathFactory xPathFactory = XPathFactory.newInstance();
        xPathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return xPathFactory;
    }

    /** 操作系统类型 */
    public enum OperatingSystem {
        /** Windows */
        Windows,

        /** Mac OS X */
        MacOSX,

        /** Linux */
        Linux,

        /** Solaris */
        Solaris,

        /** BSD */
        BSD,

        /** Unix 或 AIX */
        Unix,

        /** 未知 */
        Unknown
    }
}
