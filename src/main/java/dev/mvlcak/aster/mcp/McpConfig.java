package dev.mvlcak.aster.mcp;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
@RegisterReflectionForBinding({McpSettings.class, McpClientSetting.class})
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public McpSettingsStore mcpSettingsStore() {
        return new McpSettingsStore();
    }

    /**
     * Contributes the servers configured in {@code ~/.aster/mcp.json}.
     */
    @Bean
    public List<NamedClientMcpTransport> asterMcpTransports(McpSettingsStore store,
                                                            ObjectProvider<JsonMapper> jsonMapper) {
        List<McpClientSetting> servers;
        try {
            servers = store.load();
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring {}: {}", store.configFile(), e.getMessage());
            return List.of();
        }

        McpJsonMapper mcpJsonMapper =
                new JacksonMcpJsonMapper(jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));

        return servers.stream()
                .map(setting -> {
                    log.info("MCP server '{}' ({}) at {}",
                            setting.name(), setting.protocolType(), setting.fullUrl());
                    try {
                        return new NamedClientMcpTransport(setting.name(), transport(setting, mcpJsonMapper));
                    } catch (IllegalArgumentException e) {
                        // A header that cannot be resolved would be sent empty; skip the server instead.
                        log.warn("Skipping MCP server '{}': {}", setting.name(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static McpClientTransport transport(McpClientSetting setting, McpJsonMapper jsonMapper) {
        Map<String, String> headers = McpPlaceholders.resolve(setting.headers());
        // Applied per request, so it also covers session resumption and the SSE re-listen.
        McpSyncHttpClientRequestCustomizer customizer =
                (requestBuilder, _, _, _, _) -> headers.forEach(requestBuilder::header);

        return switch (setting.protocolType()) {
            case STREAMABLE_HTTP -> HttpClientStreamableHttpTransport.builder(setting.url())
                    .endpoint(setting.endpoint())
                    .jsonMapper(jsonMapper)
                    .httpRequestCustomizer(customizer)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .openConnectionOnStartup(false)
                    .build();
            case SSE -> HttpClientSseClientTransport.builder(setting.url())
                    .sseEndpoint(setting.endpoint())
                    .jsonMapper(jsonMapper)
                    .httpRequestCustomizer(customizer)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        };
    }
}