package dev.mvlcak.aster.chat;

import dev.mvlcak.aster.agent.routing.RoutingWorkflow;
import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RoutingWorkflow routingWorkflow;
    private final AppEventBus appEventBus;

    public ChatService(
            RoutingWorkflow routingWorkflow,
            AppEventBus appEventBus) {
        this.routingWorkflow = routingWorkflow;
        this.appEventBus = appEventBus;
    }

    public void startStream(String text) {
        Thread.ofVirtual().name("chat-stream").start(() -> {
            try {
                routingWorkflow.route(text);
            } catch (Exception e) {
                log.error("Workflow failed for input: {}", text, e);
                appEventBus.dispatch(AppEvent.AssistantFail.of(e));
            }
        });
    }
}