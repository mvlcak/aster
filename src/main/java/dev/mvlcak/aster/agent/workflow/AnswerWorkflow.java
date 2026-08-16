package dev.mvlcak.aster.agent.workflow;

import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.mcp.McpToolCall;
import dev.mvlcak.aster.mcp.McpToolCallLog;
import dev.mvlcak.aster.tui.AppState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AnswerWorkflow implements AgentWorkflow {

    private static final int MAX_RESULT_CHARS = 4000;

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final AppEventBus appEventBus;
    private final AppState appState;
    private final McpToolCallLog mcpToolCallLog;

    public AnswerWorkflow(ChatClient chatClient, ChatModel chatModel, AppEventBus appEventBus, AppState appState,
                          McpToolCallLog mcpToolCallLog) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.appEventBus = appEventBus;
        this.appState = appState;
        this.mcpToolCallLog = mcpToolCallLog;
    }

    @Override
    public void runWorkflow(String userInput) {
        ChatClient.CallResponseSpec callResponseSpec = chatClient.prompt(userInput).advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, 1)).call();
        appState.countTokens(callResponseSpec.chatResponse());
        appEventBus.dispatch(new AppEvent.AssistantCompleteText(answerText(userInput, callResponseSpec.content())));
    }

    private String answerText(String userInput, String content) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        List<McpToolCall> mcpCalls = mcpToolCallLog.calls();
        if (!mcpCalls.isEmpty()) {
            return answerFromMcpResults(userInput, mcpCalls);
        }
        List<String> tools = appState.toolCallsInFlight();
        if (tools.isEmpty()) {
            return "No answer was returned.";
        }
        return "Done, ran %s. See the tool output above."
                .formatted(String.join(", ", tools.stream().distinct().toList()));
    }

    /**
     * Turns the results of the MCP calls into the answer the model did not give. The output schema
     * of each tool goes along with its result, so the answer names what the fields actually hold
     * instead of restating that a tool ran.
     */
    private String answerFromMcpResults(String userInput, List<McpToolCall> mcpCalls) {
        appState.setActivityStatus("reading the tool result");
        ChatClient.CallResponseSpec response = ChatClient.builder(chatModel).build()
                .prompt("""
                        Answer the user from the results of the MCP tool calls below.
                        
                        Each result is JSON that follows the output schema shown with it. Read the
                        fields the schema names and report their concrete values. State only what
                        the results contain, and do not mention the schema itself or that a tool ran.
                        Answer in a few sentences of plain prose.
                        
                        # USER INPUT START
                        %s
                        # USER INPUT END
                        
                        # TOOL RESULTS START
                        %s
                        # TOOL RESULTS END
                        """.formatted(userInput, renderCalls(mcpCalls)))
                .call();
        appState.countTokens(response.chatResponse());

        String answer = response.content();
        return answer == null || answer.isBlank() ? plainResults(mcpCalls) : answer;
    }

    private static String renderCalls(List<McpToolCall> mcpCalls) {
        return mcpCalls.stream().map(call -> """
                ## %s
                Arguments: %s
                Output schema: %s
                Result: %s
                """.formatted(call.tool(), call.arguments(),
                call.hasOutputSchema() ? call.outputSchemaJson() : "(none declared)",
                truncate(call.result()))).collect(Collectors.joining("\n"));
    }

    /**
     * Last resort when even the follow-up call says nothing: the results themselves.
     */
    private static String plainResults(List<McpToolCall> mcpCalls) {
        return mcpCalls.stream()
                .map(call -> "**%s** returned:\n%s".formatted(call.tool(), truncate(call.result())))
                .collect(Collectors.joining("\n\n"));
    }

    private static String truncate(String result) {
        return result.length() <= MAX_RESULT_CHARS
                ? result
                : result.substring(0, MAX_RESULT_CHARS) + "… (truncated)";
    }
}
