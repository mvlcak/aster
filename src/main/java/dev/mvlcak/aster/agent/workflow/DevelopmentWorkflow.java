package dev.mvlcak.aster.agent.workflow;

import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
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
    private final AppState appState;
    private final AppEventBus appEventBus;
    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpTools;

    public DevelopmentWorkflow(ChatClient chatClient, ChatModel chatModel, FileSystemTools fileSystemTools, GrepTool grepTool, ShellTools shellTools, AppState appState, AppEventBus appEventBus,
                               ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.fileSystemTools = fileSystemTools;
        this.grepTool = grepTool;
        this.shellTools = shellTools;
        this.appState = appState;
        this.appEventBus = appEventBus;
        this.mcpTools = mcpTools;
    }

    public record DevelopmentSummary(String answer, String summary) {
    }

    public record PlanSummary(Map<String, String> fileChanges, String summary) {
    }

    @Override
    public void runWorkflow(String userInput) {
        runDevelopmentWorkflow(userInput);
    }

    public void runDevelopmentWorkflow(String userInput) {

        //plan
        appState.setActivityStatus("planning");

        // The planner gets the MCP tools too, otherwise it plans local file edits for work a
        // connected server already offers (upgrading dependencies in a remote repository, say).
        ChatClient.Builder plannerBuilder = ChatClient
                .builder(chatModel)
                .defaultTools(fileSystemTools, grepTool);
        mcpTools.ifAvailable(plannerBuilder::defaultTools);

        ResponseEntity<ChatResponse, PlanSummary> planResponse = plannerBuilder
                .build()
                .prompt(
                        """
                                Create plan for Coding Agent from user input
                                
                                # USER INPUT START
                                %s
                                # USER INPUT END
                                
                                Use tools to analyse project and its files and create
                                plan for AI coding agent to follow.
                                
                                Don't overcomplicate stuff, if it's simple change make plan simple
                                
                                fill out fileChanges Map<String,String> with key as filePath + fileName
                                and value as plan what to do in this file
                                
                                Give 1 sentence summary to dedicated summary attribute
                                """.formatted(userInput)).call().responseEntity(PlanSummary.class);
        appState.countTokens(planResponse.response());
        appEventBus.dispatch(new AppEvent.AssistantSummary(planResponse.getEntity().summary()));

        //develop
        appState.setActivityStatus("developing");
        ResponseEntity<ChatResponse, DevelopmentSummary> result = chatClient.prompt(planResponse.entity().fileChanges()
                        .entrySet().stream().map(e -> "- **%s**: %s".formatted(e.getKey(), e.getValue())).collect(Collectors.joining("\n"))
                        + "\n Give 1 sentence summary to dedicated attribute")
                .system("""
                        Always give a summary in approximately 3 sentences what you did after prompt of client.
                        Always after doing work(writing to files or creating files) use diff tool to show client what you have done.
                        """)
                .toolContext(Map.of(
                        "workingDirectory", appState.workingDirectory(),
                        "executionMode", "BUILD"))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, 1))
                .call()
                .responseEntity(DevelopmentSummary.class);
        appState.countTokens(result.response());

        //dispatch
        appEventBus.dispatch(new AppEvent.AssistantCompleteText(result.entity().summary()));

    }
}
