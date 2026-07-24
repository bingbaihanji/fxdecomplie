package com.bingbaihanji.classgraph.type;

/**
 * 解析异常
 */
public class ParseException extends Exception {
    /** 序列化版本UID */
    static final long serialVersionUID = 1L;

    /**
     * 解析异常
     *
     * @param TypeParser
     *            解析器
     * @param msg
     *            异常消息
     */
    public ParseException(final TypeParser TypeParser, final String msg) {
        super(TypeParser == null ? msg : msg + " (" + TypeParser.getPositionInfo() + ")");
    }
}