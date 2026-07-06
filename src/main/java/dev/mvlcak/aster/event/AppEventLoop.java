package dev.mvlcak.aster.event;

import dev.mvlcak.aster.chat.StreamingChatService;
import dev.mvlcak.aster.tui.AppState;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.concurrent.atomic.AtomicReference;

public class AppEventLoop {

    private final AppEventBus bus;
    private final AppState appState;
    private final StreamingChatService streamingChatService;

    public AppEventLoop(AppEventBus bus, AppState appState, StreamingChatService streamingChatService) {
        this.bus = bus;
        this.appState = appState;
        this.streamingChatService = streamingChatService;
    }

    public void start() {
        Thread.ofVirtual().name("app-event-loop").start(this::run);
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                process(bus.take());
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(AppEvent event) {
        switch (event) {

            case AppEvent.UserInput(String text) -> {
                if (appState.isStreaming()) {
                    appState.appendSystemMessage("A response is already streaming.");
                    return;
                }
                appState.appendUserMessage(text);
                appState.startAssistantResponse();
                streamingChatService.startStream(text);
            }
            case AppEvent.AssistantComplete(AtomicReference<ChatResponse> chatResponse) -> appState.completeAssistantResponse(chatResponse);
            case AppEvent.AssistantCompleteText(String text) -> appState.completeAssistantResponseText(text);
            case AppEvent.AssistantFail(String error) -> appState.abortAssistantResponse(error);
            case AppEvent.SystemMessage(String text) -> appState.appendSystemMessage(text);
        }
    }
}
