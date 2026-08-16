package dev.mvlcak.aster.mcp;

import org.springframework.ai.util.JsonHelper;

import java.util.Map;

/**
 * One finished MCP tool call: what was sent, what came back, and the schema the server declared
 * for that result.
 *
 * @param tool         the tool name as the model knows it
 * @param arguments    the JSON arguments the model passed
 * @param result       the JSON result, structured according to {@code outputSchema} when there is one
 * @param outputSchema the tool's declared output schema, empty when the server declares none
 */
public record McpToolCall(String tool, String arguments, String result, Map<String, Object> outputSchema) {

    private static final JsonHelper JSON = new JsonHelper();

    public McpToolCall {
        outputSchema = outputSchema == null ? Map.of() : outputSchema;
    }

    public boolean hasOutputSchema() {
        return !outputSchema.isEmpty();
    }

    /**
     * The output schema as the server sent it, ready to go back to a model next to the result.
     */
    public String outputSchemaJson() {
        return JSON.toJson(outputSchema);
    }
}
