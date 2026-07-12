package com.bingbaihanji.fxdecomplie.util.value;

/**
 * 当从某个输入创建 {@link ReValue} 失败时抛出的异常
 *
 * @author Matt Coley
 */
public class IllegalValueException extends Exception {
    /**
     * @param message
     * 		无法创建该值的原因
     */
    public IllegalValueException(String message) {
        super(message);
    }
}
