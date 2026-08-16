package dev.mvlcak.aster.agent.routing;

import dev.mvlcak.aster.agent.workflow.AnswerWorkflow;
import dev.mvlcak.aster.agent.workflow.DevelopmentWorkflow;
import dev.mvlcak.aster.mcp.ResilientMcpToolCallbackProvider;
import dev.mvlcak.aster.tui.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RoutingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(RoutingWorkflow.class);

    public static final Map<String, String> ROUTING_OPTIONS = Map.of(
            Route.DEVELOPMENT.name(),
            """
                        This path represents writing and editing code or any scripts in any programming languages
                    """,

            Route.ANSWER.name(),
            """
                        This path represents tasks not related to writing code, but it can be related to software
                        or calling mcp server, or analyzing files in filesystem or general questions
                    """
    );
    private final AppState appState;

    public enum Route {
        ANSWER,
        DEVELOPMENT;
    }

    private final DevelopmentWorkflow developmentWorkflow;
    private final AnswerWorkflow answerWorkflow;
    private final ChatModel chatModel;
    private final ResilientMcpToolCallbackProvider mcpTools;

    public RoutingWorkflow(DevelopmentWorkflow developmentWorkflow, AnswerWorkflow answerWorkflow, ChatModel chatModel, AppState appState,
                           ResilientMcpToolCallbackProvider mcpTools) {
        this.developmentWorkflow = developmentWorkflow;
        this.answerWorkflow = answerWorkflow;
        this.chatModel = chatModel;
        this.appState = appState;
        this.mcpTools = mcpTools;
    }

    public record RouteDecision(Route operation) {
    }

    public RouteDecision decide(String input) {
        String route = determineRoute(input, ROUTING_OPTIONS);

        return new RouteDecision(Route.valueOf(route.trim().toUpperCase(Locale.ROOT)));
    }

    public void route(String input) {
        RouteDecision decision = decide(input);

        switch (decision.operation()) {
            case DEVELOPMENT -> developmentWorkflow.runWorkflow(input);
            case ANSWER -> answerWorkflow.runWorkflow(input);
        }
    }

    private String determineRoute(String input, Map<String, String> availableRoutes) {
        String request = String.format(
                """
                        Given this map that provides the ops operation as key and the description for you to build the operation value, as value: %s.
                        
                        Analyze the input and select the most appropriate operation.
                        Return one String. Operation for which you decided.
                        
                        Input: %s
                        ### AVAILABLE MCPS ###
                        %s
                        """, availableRoutes, input, mcpToolsSection());

        ChatClient.ChatClientRequestSpec requestSpec = ChatClient.builder(chatModel).build().prompt(request);
        ChatClient.CallResponseSpec responseSpec = requestSpec.call();
        appState.countTokens(responseSpec.chatResponse());
        String routingResponse = responseSpec.entity(String.class);

        return routingResponse;
    }

    /**
     * Lists the tools the connected MCP servers expose
     */
    private String mcpToolsSection() {
        String tools = Arrays.stream(mcpTools.getToolCallbacks())
                .map(toolCallback -> toolCallback.getToolDefinition().name())
                .collect(Collectors.joining(","));
        if (tools.isBlank()) {
            return "";
        }
        return """
                These MCP tools are available on the %s path, prefer that path when the input matches one of them:
                %s
                """.formatted(Route.ANSWER.name(), tools);
    }

}