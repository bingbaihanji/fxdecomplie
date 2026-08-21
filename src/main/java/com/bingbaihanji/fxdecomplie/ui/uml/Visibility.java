package com.bingbaihanji.fxdecomplie.ui.uml;

/** UML 可见性符号 */
public enum Visibility {
    PUBLIC('+'), PROTECTED('#'), PACKAGE('~'), PRIVATE('-');

    private final char symbol;

    private Visibility(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }
}
