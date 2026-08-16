package dev.mvlcak.aster.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.StringUtils;

/**
 * Calls a single MCP tool and hands the model the result the tool's output schema describes.
 * <p>
 * Spring AI's own {@code SyncMcpToolCallback} returns the content blocks of the response and drops
 * {@code structuredContent} along with the tool's output schema, so the model never learns the
 * shape of what it got back. This callback keeps both: the schema goes into the tool description,
 * and a structured result is returned as-is.
 */
class McpToolCallback implements ToolCallback {

    private static final JsonHelper JSON = new JsonHelper();

    private static final String NO_ARGUMENTS = "{}";

    private final McpSyncClient client;
    private final McpSchema.Tool tool;
    private final String toolName;
    private final ToolContextToMcpMetaConverter metaConverter;
    private final McpToolCallLog callLog;

    McpToolCallback(McpSyncClient client, McpSchema.Tool tool, String toolName,
                    ToolContextToMcpMetaConverter metaConverter, McpToolCallLog callLog) {
        this.client = client;
        this.tool = tool;
        this.toolName = toolName;
        this.metaConverter = metaConverter;
        this.callLog = callLog;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        ToolDefinition definition = McpToolUtils.createToolDefinition(toolName, tool);
        if (!hasOutputSchema()) {
            return definition;
        }
        // A chat request has no field for the output schema of a tool, so the description is the
        // only place the model can read it before deciding what to answer.
        return DefaultToolDefinition.builder()
                .name(definition.name())
                .description("""
                        %s
                        
                        The result of this tool is JSON matching this output schema, answer from its fields:
                        %s""".formatted(definition.description(), JSON.toJson(tool.outputSchema())))
                .inputSchema(definition.inputSchema())
                .build();
    }

    @Override
    public String call(String toolCallInput) {
        return call(toolCallInput, null);
    }

    @Override
    public String call(String toolCallInput, ToolContext toolContext) {
        // Streaming responses can leave the arguments empty for a tool that takes none.
        String arguments = StringUtils.hasText(toolCallInput) ? toolCallInput : NO_ARGUMENTS;

        McpSchema.CallToolResult response;
        try {
            response = client.callTool(McpSchema.CallToolRequest.builder()
                    .name(tool.name())
                    .arguments(JSON.fromJsonToMap(arguments))
                    .meta(toolContext == null ? null : metaConverter.convert(toolContext))
                    .build());
        } catch (Exception e) {
            throw new ToolExecutionException(getToolDefinition(), e);
        }

        if (Boolean.TRUE.equals(response.isError())) {
            throw new ToolExecutionException(getToolDefinition(),
                    new IllegalStateException("Error calling tool: " + response.content()));
        }

        String result = resultJson(response);
        callLog.record(new McpToolCall(toolName, arguments, result, tool.outputSchema()));
        return result;
    }

    /**
     * A server that declares an output schema answers with the data under {@code structuredContent}
     * and repeats it in the content blocks only for clients that cannot read it, so the structured
     * form is the one to pass on.
     */
    private static String resultJson(McpSchema.CallToolResult response) {
        Object structured = response.structuredContent();
        return structured == null ? JSON.toJson(response.content()) : JSON.toJson(structured);
    }

    private boolean hasOutputSchema() {
        return tool.outputSchema() != null && !tool.outputSchema().isEmpty();
    }
}
