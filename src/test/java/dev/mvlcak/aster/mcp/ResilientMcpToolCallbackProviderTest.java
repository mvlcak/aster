package dev.mvlcak.aster.mcp;

import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResilientMcpToolCallbackProviderTest {

    @Test
    void skipsTheUnreachableServerAndKeepsTheToolsOfTheOthers() {
        McpSyncClient reachable = client("github", "search_issues");
        McpSyncClient unreachable = unreachableClient("jira");

        ResilientMcpToolCallbackProvider provider = provider(reachable, unreachable);

        assertThat(toolNames(provider)).containsExactly("search_issues");
    }

    @Test
    void leavesTheUnreachableServerAloneOnTheNextListing() {
        McpSyncClient unreachable = unreachableClient("jira");

        ResilientMcpToolCallbackProvider provider = provider(unreachable);
        provider.getToolCallbacks();
        provider.getToolCallbacks();

        verify(unreachable, times(1)).listTools();
    }

    @Test
    void refreshDialsTheUnreachableServerAgain() {
        McpSyncClient unreachable = unreachableClient("jira");

        ResilientMcpToolCallbackProvider provider = provider(unreachable);
        provider.getToolCallbacks();
        provider.refresh();
        provider.getToolCallbacks();

        verify(unreachable, times(2)).listTools();
    }

    @Test
    void listsAServerOnlyOnceWhileItAnswers() {
        McpSyncClient reachable = client("github", "search_issues");

        ResilientMcpToolCallbackProvider provider = provider(reachable);
        provider.getToolCallbacks();
        provider.getToolCallbacks();

        verify(reachable, times(1)).listTools();
    }

    @Test
    void tellsTheModelTheOutputSchemaOfTheTool() {
        McpSyncClient client = client("weather", toolWithOutputSchema());

        ToolCallback callback = provider(client).getToolCallbacks()[0];

        assertThat(callback.getToolDefinition().description())
                .contains("Looks up the forecast")
                .contains("\"temperature\"")
                .contains("\"summary\"");
    }

    @Test
    void answersWithTheStructuredResultRatherThanTheContentBlocks() {
        McpSyncClient client = client("weather", toolWithOutputSchema());
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("{\"temperature\":21,\"summary\":\"clear\"}")))
                .structuredContent(Map.of("temperature", 21, "summary", "clear"))
                .build());

        String result = provider(client).getToolCallbacks()[0].call("{\"city\":\"Brno\"}");

        // The content blocks would arrive as a JSON array of {"type":"text",...} envelopes.
        assertThat(result).startsWith("{").contains("\"temperature\":21", "\"summary\":\"clear\"");
    }

    @Test
    void fallsBackToTheContentBlocksWhenTheServerSendsNoStructuredResult() {
        McpSyncClient client = client("weather", "forecast");
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("21 degrees")))
                .build());

        String result = provider(client).getToolCallbacks()[0].call("{}");

        assertThat(result).contains("21 degrees");
    }

    @Test
    void logsTheCallWithTheOutputSchemaItAnsweredBy() {
        McpSyncClient client = client("weather", toolWithOutputSchema());
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of())
                .structuredContent(Map.of("temperature", 21, "summary", "clear"))
                .build());
        McpToolCallLog callLog = new McpToolCallLog(new AppEventBus(), new AppState());

        provider(callLog, client).getToolCallbacks()[0].call("{\"city\":\"Brno\"}");

        assertThat(callLog.calls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("forecast");
            assertThat(call.arguments()).isEqualTo("{\"city\":\"Brno\"}");
            assertThat(call.result()).contains("\"temperature\":21");
            assertThat(call.hasOutputSchema()).isTrue();
        });
    }

    private static McpSchema.Tool toolWithOutputSchema() {
        return McpSchema.Tool.builder()
                .name("forecast")
                .description("Looks up the forecast")
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "temperature", Map.of("type", "integer"),
                                "summary", Map.of("type", "string"))))
                .build();
    }

    private static ResilientMcpToolCallbackProvider provider(McpSyncClient... clients) {
        return provider(new McpToolCallLog(new AppEventBus(), new AppState()), clients);
    }

    private static ResilientMcpToolCallbackProvider provider(McpToolCallLog callLog, McpSyncClient... clients) {
        return new ResilientMcpToolCallbackProvider(
                objectProvider(List.of(clients)), objectProvider(), objectProvider(), objectProvider(), callLog);
    }

    private static List<String> toolNames(ResilientMcpToolCallbackProvider provider) {
        return Arrays.stream(provider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name())
                .toList();
    }

    private static McpSyncClient client(String name, String toolName) {
        return client(name, McpSchema.Tool.builder()
                .name(toolName)
                .description(toolName)
                .inputSchema(Map.of("type", "object"))
                .build());
    }

    private static McpSyncClient client(String name, McpSchema.Tool tool) {
        McpSyncClient client = clientNamed(name);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));
        return client;
    }

    private static McpSyncClient unreachableClient(String name) {
        McpSyncClient client = clientNamed(name);
        when(client.listTools()).thenThrow(new RuntimeException("Client failed to initialize listing tools"));
        return client;
    }

    private static McpSyncClient clientNamed(String name) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation("aster - " + name, name, "0.0.1"));
        when(client.getClientCapabilities()).thenReturn(McpSchema.ClientCapabilities.builder().build());
        return client;
    }

    @SafeVarargs
    private static <T> ObjectProvider<T> objectProvider(T... elements) {
        return new ObjectProvider<>() {
            @Override
            public Stream<T> stream() {
                return Stream.of(elements);
            }
        };
    }
}
