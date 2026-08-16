package dev.mvlcak.aster.mcp;

import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.tui.AppState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the MCP calls of the response that is currently running, so a workflow can answer from
 * their results even when the model itself returns no text, and shows each call in the transcript
 * while it happens.
 */
@Component
public class McpToolCallLog {

    private final AppEventBus appEventBus;
    private final AppState appState;
    private final List<McpToolCall> calls = new CopyOnWriteArrayList<>();

    public McpToolCallLog(AppEventBus appEventBus, AppState appState) {
        this.appEventBus = appEventBus;
        this.appState = appState;
    }

    public void clear() {
        calls.clear();
    }

    public void record(McpToolCall call) {
        calls.add(call);
        appState.recordToolCall(call.tool());
    }

    /**
     * The calls made since the last {@link #clear()}, in call order.
     */
    public List<McpToolCall> calls() {
        return List.copyOf(calls);
    }
}
