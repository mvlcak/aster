package dev.mvlcak.aster.mcp;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands the placeholders allowed in MCP header values, so that {@code ~/.aster/mcp.json} stays
 * portable across projects and free of credentials.
 */
public final class McpPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");
    private static final String ENV_PREFIX = "env:";

    private McpPlaceholders() {
    }

    public static Map<String, String> resolve(Map<String, String> headers) {
        if (headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((key, value) -> resolved.put(key, resolve(value)));
        return resolved;
    }

    public static String resolve(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replacement(String placeholder) {
        if ("workspace".equals(placeholder)) {
            return workspace().toString();
        }
        if (placeholder.startsWith(ENV_PREFIX)) {
            String variable = placeholder.substring(ENV_PREFIX.length()).trim();
            String value = System.getenv(variable);
            if (value == null) {
                throw new IllegalArgumentException("Environment variable '" + variable + "' is not set");
            }
            return value;
        }
        throw new IllegalArgumentException(
                "Unknown placeholder '${" + placeholder + "}' (supported: ${workspace}, ${env:NAME})");
    }

    private static Path workspace() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
