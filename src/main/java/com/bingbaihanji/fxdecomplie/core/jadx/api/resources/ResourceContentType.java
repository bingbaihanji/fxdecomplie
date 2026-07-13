package com.bingbaihanji.fxdecomplie.core.jadx.api.resources;

/**
 * 资源内容类型枚举
 * <p>
 * 用于标识反编译过程中资源文件所承载内容的类型，以决定其展示或处理方式
 */
public enum ResourceContentType {
    /** 文本内容，可作为纯文本读取展示 */
    CONTENT_TEXT,
    /** 二进制内容，如图片 字节码等非文本数据 */
    CONTENT_BINARY,
    /** 无内容 */
    CONTENT_NONE,
    /** 内容类型未知，尚未判定 */
    CONTENT_UNKNOWN,
}
