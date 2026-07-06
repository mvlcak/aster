package dev.mvlcak.aster.chat;

import dev.mvlcak.aster.ai.routing.RoutingWorkflow;
import dev.mvlcak.aster.ai.workflow.DevelopmentWorkflow;
import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);
    private final ChatClient chatClient;
    private final AppState appState;
    private final AppEventBus bus;
    private final RoutingWorkflow routingWorkflow;
    private final DevelopmentWorkflow developmentWorkflow;

    public StreamingChatService(ChatClient chatClient, AppState appState, AppEventBus bus,
                                RoutingWorkflow routingWorkflow, DevelopmentWorkflow developmentWorkflow) {
        this.chatClient = chatClient;
        this.appState = appState;
        this.bus = bus;
        this.routingWorkflow = routingWorkflow;
        this.developmentWorkflow = developmentWorkflow;
    }

    public void startStream(String text) {
        Thread.ofVirtual().name("chat-stream").start(() -> streamConversation(text));
    }

    private void streamConversation(String text) {
        try {
            RoutingWorkflow.RouteDecision decision = routingWorkflow.decide(text);

            if ("development".equals(decision.operation())) {
                String result = developmentWorkflow.runWorkflow(decision.value());
                bus.dispatch(new AppEvent.AssistantCompleteText(result));
                return;
            }

            streamAnswer(decision.value());
        } catch (Exception e) {
            bus.dispatch(new AppEvent.AssistantFail("Chat failed: " + rootCauseMessage(e)));
        }
    }

    private void streamAnswer(String text) {
        AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
        MessageAggregator aggregator = new MessageAggregator();

        Flux<ChatResponse> responseFlux = chatClient
                .prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, 1))
                .user(text)
                .toolContext(Map.of(
                        "workingDirectory", appState.workingDirectory(),
                        "executionMode", "BUILD"
                ))
                .stream()
                .chatResponse();

        aggregator.aggregate(responseFlux, aggregatedResponse::set)
                .blockLast();

        bus.dispatch(new AppEvent.AssistantComplete(aggregatedResponse));
    }

    private String rootCauseMessage(Throwable throwable) {
        log.error("Chat stream failed", throwable);
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}