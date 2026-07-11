package dev.mvlcak.aster.ai.workflow;

import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class AnswerWorkflow implements AgentWorkflow {

    private final ChatClient chatClient;
    private final AppEventBus appEventBus;
    private final AppState appState;

    public AnswerWorkflow(ChatClient chatClient, AppEventBus appEventBus, AppState appState) {
        this.chatClient = chatClient;
        this.appEventBus = appEventBus;
        this.appState = appState;
    }

    @Override
    public void runWorkflow(String userInput) {
        ChatClient.CallResponseSpec callResponseSpec = chatClient.prompt(userInput).call();
        appState.countTokens(callResponseSpec.chatResponse());
        appEventBus.dispatch(new AppEvent.AssistantCompleteText(callResponseSpec.content()));
    }
}
