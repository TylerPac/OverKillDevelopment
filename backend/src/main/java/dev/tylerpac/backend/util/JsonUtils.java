package dev.tylerpac.backend.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    /**
     * Return the string value of the node or null if unavailable.
     */
    public static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            String s = MAPPER.convertValue(node, String.class);
            return s;
        } catch (IllegalArgumentException ex) {
            String s = node.toString();
            if (s != null && s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
    }

    /**
     * Return the string value or empty string if absent.
     */
    public static String textOrEmpty(JsonNode node) {
        String s = textOrNull(node);
        return s == null ? "" : s;
    }
}
