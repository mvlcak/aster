package dev.mvlcak.aster.tui;

import dev.mvlcak.aster.mcp.McpClientSetting;
import dev.mvlcak.aster.mcp.McpSettingsStore;
import dev.mvlcak.aster.tui.config.TuiProperties;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Row;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.*;

public class TuiApp extends ToolkitApp {

    private final AppState state;
    private final TuiProperties tuiProperties;
    private final ChatPane chatPane;
    // The list keeps its selection inside the element instance, so it must survive across renders.
    private Element mcpList;

    public TuiApp(AppState state, TuiProperties tuiProperties, ChatPane chatPane) {
        this.state = state;
        this.tuiProperties = tuiProperties;
        this.chatPane = chatPane;
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder()
                .tickRate(Duration.ofMillis(tuiProperties.tickRateMs()))
                .resizeGracePeriod(Duration.ofMillis(tuiProperties.resizeGracePeriodMs()))
                .mouseCapture(true)
                .build();
    }

    @Override
    protected Element render() {
        return switch (state.currentScreen()) {
            case HELP -> renderHelpScreen();
            case CHAT -> renderChatScreen();
            case MCP -> renderMcpScreen();
            case USAGE -> renderUsageScreen();
        };
    }

    private Element renderMcpScreen() {
        List<McpClientSetting> mcpSettings;
        try {
            mcpSettings = new McpSettingsStore().load();
        } catch (IOException e) {
            throw new RuntimeException("Mcp client settings from ~/.aster/mcp.json not loaded", e);
        }

        if (mcpList == null) {
            ListElement<?> list = list();
            for (McpClientSetting setting : mcpSettings) {
                list.add(row(text(setting.name()).bold().cyan(), spacer(2), text(setting.fullUrl()), spacer(2), text(setting.protocolType())));
            }
            mcpList = list
                    .highlightColor(Color.CYAN)
                    .title("MCP")
                    .rounded()
                    .fill()
                    .id("root")
                    .focusable()
                    .onKeyEvent(this::handleRootEvent);
        }
        return mcpList;
    }

    private Element renderUsageScreen() {
        String helpText = """
                Esc, q, or Enter returns to chat.
                Ctrl+C quits the application.
                """.formatted(
                        state.getTotalSpentTokens(),
                        state.getLastCompletionTokens(),
                        state.getTotalCompletionTokens(),
                        state.getLastInputTokens(),
                        state.getTotalInputTokens());
        return panel("Usage",
                column(
                        tokenRow("last prompt tokens - ", state.getLastSpentTokens()),
                        tokenRow("total spent tokens - ", state.getTotalSpentTokens()),
                        row(),
                        tokenRow("last output tokens - ", state.getLastCompletionTokens()),
                        tokenRow("total output tokens - ", state.getTotalCompletionTokens()),
                        row(),
                        tokenRow("last input tokens - ", state.getLastInputTokens()),
                        tokenRow("total input tokens - ", state.getTotalInputTokens()),
                        row(),
                        richTextArea(helpText))
        ).rounded().fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private @NonNull Row tokenRow(String text, long number) {
        return row(text(text).fg(Color.CYAN), text(number).fg(Color.YELLOW));
    }

    private Element renderHelpScreen() {
        String helpText = """
                /clear Clear the current session transcript and workflow
                /usage Show usage of tokens
                /help  Show this help screen

                Esc, q, or Enter returns to chat.
                Ctrl+C quits the application.
                """;
        return panel("Help",
                column(richTextArea(helpText))
        ).rounded().fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private EventResult handleRootEvent(KeyEvent event) {
        if (event.isCtrlC()) {
            quit();
            return EventResult.HANDLED;
        }

        if (state.currentScreen() == ScreenMode.HELP || state.currentScreen() == ScreenMode.USAGE || state.currentScreen() == ScreenMode.MCP) {
            if (event.isCancel() || event.isConfirm() || event.isCharIgnoreCase('q')) {
                state.switchScreen(ScreenMode.CHAT);
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        return handleChatScreenEvent(event);
    }

    private EventResult handleChatScreenEvent(KeyEvent event) {
        if (event.isKey(KeyCode.F1)) {
            state.switchScreen(ScreenMode.HELP);
            return EventResult.HANDLED;
        }

        return chatPane.handleKeyEvent(event, true);
    }

    private Element renderFooter() {
        return row(
                text(" F1").bold().fg(Color.CYAN),
                text(" Help  ·  "),
                text("PgUp/Dn").bold().fg(Color.CYAN),
                text(" Scroll  ·  "),
                text("Ctrl+C").bold().fg(Color.CYAN),
                text(" Quit")
        ).length(1);
    }

    private Element renderChatScreen() {
        return column(
                chatPane,
                renderFooter()
        ).fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

}
