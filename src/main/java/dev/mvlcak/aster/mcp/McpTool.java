package dev.mvlcak.aster.mcp;

import java.util.Map;

public record McpTool(
        String title,
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema) {
}
