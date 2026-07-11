package dev.mvlcak.aster.chat;

import dev.mvlcak.aster.ai.routing.RoutingWorkflow;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final RoutingWorkflow routingWorkflow;

    public ChatService(
            RoutingWorkflow routingWorkflow) {
        this.routingWorkflow = routingWorkflow;
    }

    public void startStream(String text) {
        Thread.ofVirtual().name("chat-stream").start(() -> routingWorkflow.route(text));
    }


}