package dev.mvlcak.aster.mcp;

import java.util.Locale;

public enum ProtocolType {

    STREAMABLE_HTTP("/mcp"),
    SSE("/sse");

    private final String defaultEndpoint;

    ProtocolType(String defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    public String defaultEndpoint() {
        return defaultEndpoint;
    }

    public static ProtocolType fromTransport(String transport) {
        if (transport == null || transport.isBlank()) {
            throw new IllegalArgumentException("Missing transport value");
        }
        return switch (transport.trim().toLowerCase(Locale.ROOT)) {
            case "http", "streamable-http", "streamable_http", "streamablehttp" -> STREAMABLE_HTTP;
            case "sse" -> SSE;
            default -> throw new IllegalArgumentException(
                    "Unsupported transport '" + transport + "' (supported: http, sse)");
        };
    }
}