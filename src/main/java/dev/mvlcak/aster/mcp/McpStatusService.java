package dev.mvlcak.aster.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class McpStatusService {

    private static final Logger log = LoggerFactory.getLogger(McpStatusService.class);

    private static final String CLIENT_NAME_SEPARATOR = " - ";

    private final ObjectProvider<List<McpSyncClient>> clients;
    private final McpSettingsStore store;

    public McpStatusService(ObjectProvider<List<McpSyncClient>> clients, McpSettingsStore store) {
        this.clients = clients;
        this.store = store;
    }

    /**
     * This blocks on network I/O (up to the configured connect timeout per server), so call it
     * off the render thread. Servers are probed in parallel.
     */
    public List<McpServerStatus> probe() {
        List<McpSyncClient> mcpClients = clients.getIfAvailable(List::of);
        if (mcpClients.isEmpty()) {
            return List.of();
        }

        Map<String, McpClientSetting> settingsByName = settingsByName();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<McpServerStatus>> probes = mcpClients.stream()
                    .map(client -> executor.submit(() -> probe(client, settingsByName)))
                    .toList();
            return probes.stream().map(McpStatusService::join).toList();
        }
    }

    private McpServerStatus probe(McpSyncClient client, Map<String, McpClientSetting> settingsByName) {
        String name = serverName(client);
        McpClientSetting setting = settingsByName.get(name);
        String url = setting == null ? "" : setting.fullUrl();
        Map<String, String> headers = setting == null ? Map.of() : setting.maskedHeaders();
        try {
            List<McpTool> tools = client.listTools().tools().stream()
                    .map(mcpTool ->
                            new McpTool(
                                    mcpTool.title(),
                                    mcpTool.name(),
                                    mcpTool.description(),
                                    mcpTool.inputSchema(),
                                    mcpTool.outputSchema()))
                    .toList();
            return new McpServerStatus(name, url, headers, true, serverVersion(client), tools, null);
        } catch (Exception e) {
            log.warn("MCP server '{}' at {} is not reachable: {}", name, url, e.toString());
            return new McpServerStatus(name, url, headers, false, null, List.of(), rootMessage(e));
        }
    }

    private Map<String, McpClientSetting> settingsByName() {
        try {
            return store.load().stream()
                    .collect(Collectors.toMap(McpClientSetting::name, setting -> setting,
                            (first, _) -> first));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}: {}", store.configFile(), e.getMessage());
            return Map.of();
        }
    }

    private static String serverName(McpSyncClient client) {
        String clientName = client.getClientInfo().name();
        int separator = clientName.indexOf(CLIENT_NAME_SEPARATOR);
        return separator < 0 ? clientName : clientName.substring(separator + CLIENT_NAME_SEPARATOR.length());
    }

    private static String serverVersion(McpSyncClient client) {
        McpSchema.Implementation info = client.getServerInfo();
        if (info == null) {
            return "";
        }
        return info.version() == null || info.version().isBlank()
                ? info.name()
                : info.name() + " " + info.version();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static McpServerStatus join(Future<McpServerStatus> probe) {
        try {
            return probe.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing MCP servers", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to probe MCP server", e.getCause());
        }
    }
}
