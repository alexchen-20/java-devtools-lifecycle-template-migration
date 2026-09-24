package learning.mail.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(encode(String.valueOf(key)) + ":" + encode(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> values = new ArrayList<>();
            items.forEach(item -> values.add(encode(item)));
            return "[" + String.join(",", values) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    static Object decode(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (parser.index != source.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Object value() {
            space();
            if (index >= source.length()) throw new IllegalArgumentException("Expected JSON value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            index++;
            List<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char current = source.charAt(index++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                char escaped = source.charAt(index++);
                if (escaped == 'u') {
                    result.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                    index += 4;
                } else {
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Invalid JSON escape");
                    });
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String token = source.substring(start, index);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid JSON value", exception);
            }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw new IllegalArgumentException("Invalid JSON literal");
            index += token.length();
            return value;
        }

        private void space() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
        private boolean take(char expected) {
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }
        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
