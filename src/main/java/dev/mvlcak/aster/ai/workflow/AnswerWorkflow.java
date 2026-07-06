package dev.mvlcak.aster.ai.workflow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class AnswerWorkflow implements AgentWorkflow {

    private final ChatClient chatClient;

    public AnswerWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String runWorkflow(String userInput) {
        return chatClient.prompt(userInput).call().content();
    }
}
