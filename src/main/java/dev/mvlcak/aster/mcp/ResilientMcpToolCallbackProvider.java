package dev.mvlcak.aster.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lists the MCP tools one server at a time.
 */
@Component
public class ResilientMcpToolCallbackProvider
        implements ToolCallbackProvider, ApplicationListener<McpToolsChangedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ResilientMcpToolCallbackProvider.class);

    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private static final ToolCallback[] NO_TOOLS = new ToolCallback[0];

    private final ObjectProvider<List<McpSyncClient>> mcpClients;
    private final McpToolFilter toolFilter;
    private final McpToolNamePrefixGenerator toolNamePrefixGenerator;
    private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;
    private final McpToolCallLog callLog;
    private final Map<McpSyncClient, Server> servers = new ConcurrentHashMap<>();

    public ResilientMcpToolCallbackProvider(ObjectProvider<List<McpSyncClient>> mcpClients,
                                            ObjectProvider<McpToolFilter> toolFilter,
                                            ObjectProvider<McpToolNamePrefixGenerator> toolNamePrefixGenerator,
                                            ObjectProvider<ToolContextToMcpMetaConverter> toolContextToMcpMetaConverter,
                                            McpToolCallLog callLog) {
        this.mcpClients = mcpClients;
        // The defaults McpToolCallbackAutoConfiguration uses, so tools keep the names the
        // auto-configured provider would have given them.
        this.toolFilter = toolFilter.getIfUnique(() -> (_, _) -> true);
        this.toolNamePrefixGenerator = toolNamePrefixGenerator.getIfUnique(McpToolNamePrefixGenerator::noPrefix);
        this.toolContextToMcpMetaConverter =
                toolContextToMcpMetaConverter.getIfUnique(ToolContextToMcpMetaConverter::defaultConverter);
        this.callLog = callLog;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return mcpClients.stream()
                .flatMap(List::stream)
                .flatMap(client -> Arrays.stream(toolCallbacks(client)))
                .toArray(ToolCallback[]::new);
    }

    public void refresh() {
        servers.values().forEach(server -> {
            server.tools = null;
            server.unreachableUntil = null;
        });
    }

    @Override
    public void onApplicationEvent(McpToolsChangedEvent event) {
        refresh();
    }

    private ToolCallback[] toolCallbacks(McpSyncClient client) {
        Server server = servers.computeIfAbsent(client, _ -> new Server());

        Instant unreachableUntil = server.unreachableUntil;
        if (unreachableUntil != null && Instant.now().isBefore(unreachableUntil)) {
            return NO_TOOLS;
        }

        ToolCallback[] cached = server.tools;
        if (cached != null) {
            return cached;
        }

        try {
            ToolCallback[] tools = listTools(client);
            server.tools = tools;
            server.unreachableUntil = null;
            return tools;
        } catch (Exception e) {
            server.unreachableUntil = Instant.now().plus(RETRY_AFTER_FAILURE);
            log.warn("Could not list the tools of MCP server '{}', skipping it for the next {}s: {}",
                    client.getClientInfo().name(), RETRY_AFTER_FAILURE.toSeconds(), e.toString());
            return NO_TOOLS;
        }
    }

    /**
     * Builds the callbacks the way {@code SyncMcpToolCallbackProvider} would, except that they keep
     * the structured result and the output schema of every tool.
     */
    private ToolCallback[] listTools(McpSyncClient client) {
        McpConnectionInfo connection = McpConnectionInfo.builder()
                .clientCapabilities(client.getClientCapabilities())
                .clientInfo(client.getClientInfo())
                .initializeResult(client.getCurrentInitializationResult())
                .build();

        return client.listTools().tools().stream()
                .filter(tool -> toolFilter.test(connection, tool))
                .<ToolCallback>map(tool -> new McpToolCallback(client, tool,
                        toolNamePrefixGenerator.prefixedToolName(connection, tool),
                        toolContextToMcpMetaConverter, callLog))
                .toArray(ToolCallback[]::new);
    }

    private static final class Server {

        private volatile ToolCallback[] tools;

        private volatile Instant unreachableUntil;
    }
}
