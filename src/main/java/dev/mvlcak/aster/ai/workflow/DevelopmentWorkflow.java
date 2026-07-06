package dev.mvlcak.aster.ai.workflow;

import dev.mvlcak.aster.tui.AppState;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DevelopmentWorkflow implements AgentWorkflow {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final FileSystemTools fileSystemTools;
    private final GrepTool grepTool;
    private final ShellTools shellTools;
    private final CodingEvaluatorOptimizerWorkflow codingEvaluatorOptimizerWorkflow;
    private final AppState appState;

    public DevelopmentWorkflow(ChatClient chatClient, ChatModel chatModel, FileSystemTools fileSystemTools, GrepTool grepTool, ShellTools shellTools, CodingEvaluatorOptimizerWorkflow codingEvaluatorOptimizerWorkflow, AppState appState) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.fileSystemTools = fileSystemTools;
        this.grepTool = grepTool;
        this.shellTools = shellTools;
        this.codingEvaluatorOptimizerWorkflow = codingEvaluatorOptimizerWorkflow;
        this.appState = appState;
    }

    @Override
    public String runWorkflow(String userInput) {

        //plan
        String plan = ChatClient
                .builder(chatModel)
                .defaultTools(fileSystemTools, grepTool, shellTools)
                .build()
                .prompt(
                        """
                                You are Planning coding agent that creates planned prompt
                                for coding agent.
                                
                                Here is user input for coding agent
                                # USER INPUT START
                                %s
                                # USER INPUT END
                                
                                Use tools to analyse project and its files and create detailed
                                plan for AI coding agent to follow
                                """.formatted(userInput)).call().content();

        //EvaluationOptimizer
        //develop
        //evaluate - compile
        Map<String, String> suggestions = codingEvaluatorOptimizerWorkflow.evaluate(plan);

        String finalSuggestions = suggestions.entrySet().stream()
                .map(e -> "- **%s**: %s".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
        return chatClient.prompt(finalSuggestions).toolContext(Map.of(
                "workingDirectory", appState.workingDirectory(),
                "executionMode", "BUILD"
        )).advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, 1)).call().content();
    }
}
