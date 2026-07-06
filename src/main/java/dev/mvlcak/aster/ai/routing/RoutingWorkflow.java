package dev.mvlcak.aster.ai.routing;

import dev.mvlcak.aster.ai.workflow.AnswerWorkflow;
import dev.mvlcak.aster.ai.workflow.DevelopmentWorkflow;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RoutingWorkflow {


    public static final Map<String, String> ROUTING_OPTIONS = Map.of(
            "development",
            """
                    This path represents development or coding of software or any scripts in any programming languages
                    """,


            "answer",
            """
                    This path represents casual conversation between user and ai agent
                    """
    );
    private final DevelopmentWorkflow developmentWorkflow;
    private final AnswerWorkflow answerWorkflow;
    private final ChatModel chatModel;

    public RoutingWorkflow(DevelopmentWorkflow developmentWorkflow, AnswerWorkflow answerWorkflow, ChatModel chatModel) {
        this.developmentWorkflow = developmentWorkflow;
        this.answerWorkflow = answerWorkflow;
        this.chatModel = chatModel;
    }

    public record RouteDecision(String operation, String value) {
    }

    public RouteDecision decide(String input) {
        String[] route = determineRoute(input, ROUTING_OPTIONS);
        List<String> requestValues = route[1].lines()
                .toList();

        return new RouteDecision(route[0], requestValues.getFirst());
    }

    public String route(String input) {
        RouteDecision decision = decide(input);

        return switch (decision.operation()) {
            case "development" -> developmentWorkflow.runWorkflow(decision.value());
            case "answer" -> answerWorkflow.runWorkflow(decision.value());
            default -> throw new IllegalStateException("Unexpected value: " + decision.operation());
        };
    }

    private String[] determineRoute(String input, Map<String, String> availableRoutes) {
        String request = String.format("""
                Given this map that provides the ops operation as key and the description for you to build the operation value, as value: %s.
                Analyze the input and select the most appropriate operation.
                Return an array of two strings. First string is the operations decided and second is the value you built based on the operation.
                
                Input: %s""", availableRoutes, input);

        ChatClient.ChatClientRequestSpec requestSpec = ChatClient.builder(chatModel).build().prompt(request);
        ChatClient.CallResponseSpec responseSpec = requestSpec.call();
        String[] routingResponse = responseSpec.entity(String[].class);

        return routingResponse;
    }

}