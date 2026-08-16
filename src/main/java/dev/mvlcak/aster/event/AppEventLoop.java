package dev.mvlcak.aster.event;

import dev.mvlcak.aster.chat.ChatService;
import dev.mvlcak.aster.mcp.McpToolCallLog;
import dev.mvlcak.aster.tui.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppEventLoop {

    private static final Logger log = LoggerFactory.getLogger(AppEventLoop.class);

    private final AppEventBus bus;
    private final AppState appState;
    private final ChatService chatService;
    private final McpToolCallLog mcpToolCallLog;

    public AppEventLoop(AppEventBus bus, AppState appState, ChatService chatService,
                        McpToolCallLog mcpToolCallLog) {
        this.bus = bus;
        this.appState = appState;
        this.chatService = chatService;
        this.mcpToolCallLog = mcpToolCallLog;
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
            } catch (RuntimeException e) {
                // Letting this escape would kill the loop and freeze the TUI for good.
                log.error("Failed to process event", e);
                appState.abortAssistantResponse(AppEvent.AssistantFail.of(e).error());
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
                mcpToolCallLog.clear();
                chatService.startStream(text);
            }
            case AppEvent.AssistantCompleteText(String text) -> appState.completeAssistantResponseText(text);
            case AppEvent.AssistantDelta(String text) -> appState.appendAssistantDelta(text);
            case AppEvent.AssistantStatus(String text) -> appState.setActivityStatus(text);
            case AppEvent.AssistantSummary(String text) -> appState.completeAssistantSummary(text);
            case AppEvent.AssistantFail(String error) -> appState.abortAssistantResponse(error);
            case AppEvent.ClearSession() -> {
                appState.clearSession();
                mcpToolCallLog.clear();
            }
        }
    }

    private static final int MAX_TOOL_RESULT_CHARS = 800;

    private static String renderToolCall(String tool, String arguments, String result) {
        StringBuilder out = new StringBuilder("**").append(tool).append("**");
        if (arguments != null && !arguments.isBlank() && !"{}".equals(arguments.trim())) {
            out.append(" ").append(oneLine(arguments, 160));
        }
        if (result != null && !result.isBlank()) {
            out.append("\n").append(truncate(result.strip(), MAX_TOOL_RESULT_CHARS));
        }
        return out.toString();
    }

    private static String oneLine(String text, int limit) {
        return truncate(text.replaceAll("\\s+", " ").strip(), limit);
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "… (truncated)";
    }
}
