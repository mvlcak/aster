package dev.mvlcak.aster.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;


public record McpClientSetting(String name, String url, String endpoint, ProtocolType protocolType,
                               Map<String, String> headers) {

    /**
     * Header names whose value is hidden when a server is listed.
     */
    private static final String SECRET_HEADER_PATTERN = ".*(authorization|token|key|secret|password).*";

    public McpClientSetting {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MCP server name must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("MCP server url must not be blank");
        }
        if (protocolType == null) {
            throw new IllegalArgumentException("MCP server protocolType must not be null");
        }
        headers = headers == null || headers.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * A server without custom headers, as written by earlier versions of {@code mcp.json}.
     */
    public McpClientSetting(String name, String url, String endpoint, ProtocolType protocolType) {
        this(name, url, endpoint, protocolType, Map.of());
    }

    public static McpClientSetting fromUrl(String name, String rawUrl, ProtocolType protocolType) {
        return fromUrl(name, rawUrl, protocolType, Map.of());
    }

    public static McpClientSetting fromUrl(String name, String rawUrl, ProtocolType protocolType,
                                           Map<String, String> headers) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("MCP server url must not be blank");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid url '" + rawUrl + "': " + e.getReason());
        }
        if (uri.getScheme() == null || uri.getAuthority() == null) {
            throw new IllegalArgumentException(
                    "Invalid url '" + rawUrl + "' (expected something like http://localhost:8080/mcp)");
        }

        String origin = uri.getScheme() + "://" + uri.getAuthority();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }
        String endpoint = path.isBlank() || "/".equals(path) ? protocolType.defaultEndpoint() : path;
        return new McpClientSetting(name, origin, endpoint, protocolType, headers);
    }

    public String fullUrl() {
        return url + endpoint;
    }

    /**
     * The headers as they may be shown on screen: values of credential-carrying headers are
     * replaced with {@code ****}, everything else (a project path, an api version) is shown as is.
     * Placeholders are left unresolved, which is what makes this safe to render.
     */
    public Map<String, String> maskedHeaders() {
        if (headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((key, value) ->
                masked.put(key, key.toLowerCase(Locale.ROOT).matches(SECRET_HEADER_PATTERN) ? "****" : value));
        return Collections.unmodifiableMap(masked);
    }
}
