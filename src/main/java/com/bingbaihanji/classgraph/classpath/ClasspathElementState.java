package com.bingbaihanji.classgraph.classpath;

/**
 * 类路径元素状态 — 表示类路径元素在其生命周期中所处的阶段。
 *
 * <p>用于替代 {@code Classpath} 中分散的 boolean 标志，
 * 提供明确的类型化状态机。</p>
 *
 * <p>状态转换：
 * <pre>
 *   CREATED → INITIALIZED → PATHS_SCANNED → CLASSES_LINKED → CLOSED
 *                        ↘            ↘
 *                         FAILED       FAILED
 * </pre>
 */
public enum ClasspathElementState {

    /** 已创建但尚未初始化 */
    CREATED,

    /** 已初始化，可以扫描路径 */
    INITIALIZED,

    /** 路径已扫描，class 文件已发现 */
    PATHS_SCANNED,

    /** 类文件已链接到类图中 */
    CLASSES_LINKED,

    /** 处理失败 */
    FAILED,

    /** 已关闭/释放资源 */
    CLOSED;

    /** 是否已到达或超过指定状态 */
    public boolean isAtLeast(ClasspathElementState other) {
        return this.ordinal() >= other.ordinal();
    }

    /** 是否为终止状态（不可再转换） */
    public boolean isTerminal() {
        return this == FAILED || this == CLOSED;
    }

    /** 是否可以进行路径扫描 */
    public boolean canScanPaths() {
        return this == INITIALIZED || this == PATHS_SCANNED || this == CLASSES_LINKED;
    }

    /** 是否可以进行类链接 */
    public boolean canLinkClasses() {
        return this == PATHS_SCANNED;
    }
}
