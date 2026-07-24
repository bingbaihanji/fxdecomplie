 
package com.bingbaihanji.classgraph.scan;

/**
 * 在扫描过程中发生错误状态或捕获到未处理异常时抛出的非受检异常
 *
 * <p>
 * (继承自 {@link IllegalArgumentException}，而后者又继承自 {@link RuntimeException}，
 * 因此可以捕获这两个更通用的异常中的任意一个)
 */
public class ScanException extends IllegalArgumentException {
    /** serialVersionUID */
    static final long serialVersionUID = 1L;

    /**
     * 构造函数
     *
     * @param message
     *            消息
     */
    ScanException(final String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message
     *            消息
     * @param cause
     *            原因
     */
    ScanException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
