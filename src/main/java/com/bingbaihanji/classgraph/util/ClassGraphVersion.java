package com.bingbaihanji.classgraph.util;

/**
 * ClassGraph 版本信息常量。
 *
 * <p>集中管理版本号，避免在多个文件中硬编码。</p>
 */
public final class ClassGraphVersion {

    private ClassGraphVersion() {}

    /** ClassGraph 库版本 */
    public static final String VERSION = "4.8.184";

    /** 兼容的 JDK 最低版本 */
    public static final int MIN_JDK_VERSION = 7;

    /** 兼容的 JDK 最高版本 */
    public static final int MAX_JDK_VERSION = 25;

    /** 获取版本字符串 */
    public static String versionString() {
        return "ClassGraph v" + VERSION + " (JDK " + MIN_JDK_VERSION + "-"
            + MAX_JDK_VERSION + ")";
    }
}
