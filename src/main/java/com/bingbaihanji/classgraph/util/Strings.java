package com.bingbaihanji.classgraph.util;

/** String utility methods for ClassGraph. Pure JDK — no external dependencies. */
public final class Strings {
    private Strings() {
        throw new AssertionError("utility class");
    }

    /**
     * Escape a string for safe inclusion in JSON output.
     * Replaces special characters with JSON escape sequences.
     *
     * @param s the string to escape, may be null
     * @return the escaped string, or null if input was null
     */
    public static String escapeJson(String s) {
        if (s == null) {
            return null;
        }
        var buf = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> buf.append("\\\"");
                case '\\' -> buf.append("\\\\");
                case '\b' -> buf.append("\\b");
                case '\f' -> buf.append("\\f");
                case '\n' -> buf.append("\\n");
                case '\r' -> buf.append("\\r");
                case '\t' -> buf.append("\\t");
                default -> {
                    if (c < 0x20) {
                        buf.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        buf.append(c);
                    }
                }
            }
        }
        return buf.toString();
    }
}
