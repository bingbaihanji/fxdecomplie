package com.bingbaihanji.utils.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON utility class — Jackson ObjectMapper wrapper providing common JSON operations.
 *
 * <p>Features a thread-safe shared {@link ObjectMapper} supporting:</p>
 * <ul>
 *   <li>Object ↔ JSON string serialization / deserialization</li>
 *   <li>Generic type deserialization (via {@link TypeReference})</li>
 *   <li>JSON tree model ({@link JsonNode}) parsing</li>
 *   <li>Pretty-printing</li>
 *   <li>JSON string escaping (used internally by ClassGraph)</li>
 * </ul>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>Unknown properties are ignored (forward compatibility)</li>
 *   <li>Dates serialized as ISO-8601 strings (not timestamps)</li>
 * </ul>
 */
public final class JSONUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);

    private static final ObjectMapper PRETTY_MAPPER = MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);

    private JSONUtils() {
        throw new AssertionError("utility class");
    }

    // ── ObjectMapper access ──

    /** @return shared default {@link ObjectMapper} (non-pretty, thread-safe) */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /** @return shared pretty-printing {@link ObjectMapper} */
    public static ObjectMapper getPrettyMapper() {
        return PRETTY_MAPPER;
    }

    // ── Serialization ──

    /**
     * Serialize an object to a JSON string.
     *
     * @param obj any Java object (POJO, Map, List, etc.)
     * @return JSON string, or {@code "null"} if obj is null
     * @throws RuntimeException wrapping {@link JsonProcessingException} on failure
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * Serialize an object to a pretty-printed JSON string.
     *
     * @param obj any Java object
     * @return formatted JSON string
     * @throws RuntimeException on serialization failure
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return PRETTY_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    // ── Deserialization ──

    /**
     * Deserialize a JSON string to the given type.
     *
     * @param json  JSON string
     * @param clazz target class
     * @param <T>   target type
     * @return deserialized object, or null if json is blank
     * @throws RuntimeException on invalid JSON or type mismatch
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed: " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Deserialize a JSON string to a generic type (e.g. {@code List<Foo>}, {@code Map<String, Bar>}).
     *
     * <p>Usage:</p>
     * <pre>{@code
     * List<User> users = JSONUtils.fromJson(json, new TypeReference<List<User>>() {});
     * Map<String, Map<String, String>> m = JSONUtils.fromJson(json, new TypeReference<>() {});
     * }</pre>
     *
     * @param json    JSON string
     * @param typeRef generic type reference
     * @param <T>     target type
     * @return deserialized object, or null if json is blank
     * @throws RuntimeException on invalid JSON or type mismatch
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed: " + typeRef.getType(), e);
        }
    }

    // ── JSON tree model ──

    /**
     * Parse a JSON string into a {@link JsonNode} tree.
     *
     * @param json JSON string
     * @return root JsonNode, or null if json is blank
     * @throws RuntimeException on parse failure
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parse failed", e);
        }
    }

    // ── JSON string escaping (ClassGraph internal) ──

    /**
     * Escape a string for use in JSON.
     *
     * @param unsafeStr the string to escape
     * @return the escaped string
     */
    public static String escapeJSONString(final String unsafeStr) {
        if (unsafeStr == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder(unsafeStr.length() * 2);
        escapeJSONString(unsafeStr, buf);
        return buf.toString();
    }

    /**
     * Escape a string for use in JSON, appending to a StringBuilder.
     *
     * @param unsafeStr the string to escape
     * @param buf       the StringBuilder to append to
     */
    static void escapeJSONString(final String unsafeStr, final StringBuilder buf) {
        if (unsafeStr == null) {
            return;
        }
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            switch (c) {
                case '"':
                    buf.append("\\\"");
                    break;
                case '\\':
                    buf.append("\\\\");
                    break;
                case '\b':
                    buf.append("\\b");
                    break;
                case '\f':
                    buf.append("\\f");
                    break;
                case '\n':
                    buf.append("\\n");
                    break;
                case '\r':
                    buf.append("\\r");
                    break;
                case '\t':
                    buf.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        buf.append("\\u");
                        buf.append(String.format("%04x", (int) c));
                    } else {
                        buf.append(c);
                    }
                    break;
            }
        }
    }
}
