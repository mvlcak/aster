package dev.mvlcak.aster.mcp;

import java.util.List;

/**
 * Root document of {@code ~/.aster/mcp.json}.
 */
public record McpSettings(List<McpClientSetting> servers) {

    public McpSettings {
        servers = servers == null ? List.of() : List.copyOf(servers);
    }

    public static McpSettings empty() {
        return new McpSettings(List.of());
    }
}