package dev.mvlcak.aster.mcp;

import java.net.URI;
import java.net.URISyntaxException;


public record McpClientSetting(String name, String url, String endpoint, ProtocolType protocolType) {

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
    }

    public static McpClientSetting fromUrl(String name, String rawUrl, ProtocolType protocolType) {
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
        return new McpClientSetting(name, origin, endpoint, protocolType);
    }

    public String fullUrl() {
        return url + endpoint;
    }
}