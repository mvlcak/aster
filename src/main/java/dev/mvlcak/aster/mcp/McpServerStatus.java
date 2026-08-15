package dev.mvlcak.aster.mcp;

import java.util.List;
import java.util.Map;

public record McpServerStatus(String name, String url, Map<String, String> headers, boolean connected,
                              String serverVersion, List<McpTool> tools, String error) {
}
