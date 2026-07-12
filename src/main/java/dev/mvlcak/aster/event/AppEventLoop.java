package dev.mvlcak.aster.event;

import dev.mvlcak.aster.chat.ChatService;
import dev.mvlcak.aster.tui.AppState;

public class AppEventLoop {

    private final AppEventBus bus;
    private final AppState appState;
    private final ChatService chatService;

    public AppEventLoop(AppEventBus bus, AppState appState, ChatService chatService) {
        this.bus = bus;
        this.appState = appState;
        this.chatService = chatService;
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
                chatService.startStream(text);
            }
            case AppEvent.AssistantCompleteText(String text) -> appState.completeAssistantResponseText(text);
            case AppEvent.AssistantDelta(String text) -> appState.appendAssistantDelta(text);
            case AppEvent.AssistantStatus(String text) -> appState.setActivityStatus(text);
            case AppEvent.AssistantSummary(String text) -> appState.completeAssistantSummary(text);
            case AppEvent.AssistantFail(String error) -> appState.abortAssistantResponse(error);
            case AppEvent.ClearSession() -> appState.clearSession();
        }
    }
}
