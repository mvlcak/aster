package dev.mvlcak.aster.ai.routing;

import dev.mvlcak.aster.ai.workflow.AnswerWorkflow;
import dev.mvlcak.aster.ai.workflow.DevelopmentWorkflow;
import dev.mvlcak.aster.tui.AppState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RoutingWorkflow {

    public static final Map<String, String> ROUTING_OPTIONS = Map.of(
            Route.DEVELOPMENT.name(),
            """
                        This path represents writing and editing code or any scripts in any programming languages
                    """,

            Route.ANSWER.name(),
            """
                        This path represents tasks not related to writing code, but it can be related to software
                        or calling mcp server, or analyzing files in filesystem
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

    public RoutingWorkflow(DevelopmentWorkflow developmentWorkflow, AnswerWorkflow answerWorkflow, ChatModel chatModel, AppState appState) {
        this.developmentWorkflow = developmentWorkflow;
        this.answerWorkflow = answerWorkflow;
        this.chatModel = chatModel;
        this.appState = appState;
    }

    public record RouteDecision(Route operation, String value) {
    }

    public RouteDecision decide(String input) {
        String[] route = determineRoute(input, ROUTING_OPTIONS);
        List<String> requestValues = route[1].lines()
                .toList();

        return new RouteDecision(Route.valueOf(route[0]), requestValues.getFirst());
    }

    public void route(String input) {
        RouteDecision decision = decide(input);

        switch (decision.operation()) {
            case DEVELOPMENT -> developmentWorkflow.runWorkflow(decision.value());
            case ANSWER -> answerWorkflow.runWorkflow(decision.value());
        }
    }

    private String[] determineRoute(String input, Map<String, String> availableRoutes) {
        String request = String.format(
                """
                Given this map that provides the ops operation as key and the description for you to build the operation value, as value: %s.
                Analyze the input and select the most appropriate operation.
                Return an array of two strings. First string is the operations decided and second is the value you built based on the operation.
                        
                                    Input: %s
                        """, availableRoutes, input);

        ChatClient.ChatClientRequestSpec requestSpec = ChatClient.builder(chatModel).build().prompt(request);
        ChatClient.CallResponseSpec responseSpec = requestSpec.call();
        appState.countTokens(responseSpec.chatResponse());
        String[] routingResponse = responseSpec.entity(String[].class);

        return routingResponse;
    }

}