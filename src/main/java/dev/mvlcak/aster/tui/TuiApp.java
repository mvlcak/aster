package dev.mvlcak.aster.tui;

import dev.mvlcak.aster.mcp.McpServerStatus;
import dev.mvlcak.aster.mcp.McpStatusService;
import dev.mvlcak.aster.mcp.McpTool;
import dev.mvlcak.aster.tui.config.TuiProperties;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Row;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tree.TreeNode;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.tamboui.toolkit.Toolkit.*;

public class TuiApp extends ToolkitApp {

    private static final Logger log = LoggerFactory.getLogger(TuiApp.class);

    private final AppState state;
    private final TuiProperties tuiProperties;
    private final ChatPane chatPane;
    private final McpStatusService mcpStatusService;
    // Written by the probe thread, read by the render thread; null means "still probing".
    private volatile List<McpServerStatus> mcpStatuses;
    // The list keeps its selection inside the element instance, so it must survive across renders.
    private TreeElement<?> mcpTree;
    // The statuses mcpList was built from, so a finished probe replaces a stale list.
    private List<McpServerStatus> renderedMcpStatuses;
    private ScreenMode lastScreen = ScreenMode.CHAT;

    public TuiApp(AppState state, TuiProperties tuiProperties, ChatPane chatPane,
                  McpStatusService mcpStatusService) {
        this.state = state;
        this.tuiProperties = tuiProperties;
        this.chatPane = chatPane;
        this.mcpStatusService = mcpStatusService;
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
        ScreenMode screen = state.currentScreen();
        if (screen != lastScreen) {
            lastScreen = screen;
            if (screen == ScreenMode.MCP) {
                startMcpProbe();
            }
        }
        return switch (screen) {
            case HELP -> renderHelpScreen();
            case CHAT -> renderChatScreen();
            case MCP -> renderMcpScreen();
            case USAGE -> renderUsageScreen();
        };
    }

    /**
     * Contacts the configured MCP servers off the render thread; the next tick picks up the result.
     */
    private void startMcpProbe() {
        mcpStatuses = null;
        Thread.ofVirtual().name("mcp-probe").start(() -> {
            try {
                mcpStatuses = mcpStatusService.probe();
            } catch (RuntimeException e) {
                log.error("Failed to probe MCP servers", e);
                mcpStatuses = List.of();
            }
        });
    }

    private Element renderMcpScreen() {
        List<McpServerStatus> statuses = mcpStatuses;
        if (statuses == null) {
            mcpTree = null;
            return mcpMessage(text("Contacting MCP servers…").fg(Color.YELLOW));
        }
        if (statuses.isEmpty()) {
            mcpTree = null;
            return mcpMessage(
                    text("No MCP servers configured.").fg(Color.YELLOW),
                    text("Add one with: aster mcp add <name> <url>").gray());
        }
        if (mcpTree == null || statuses != renderedMcpStatuses) {
            renderedMcpStatuses = statuses;
            mcpTree = buildMcpTree(statuses);
        }
        return mcpTree;
    }

    private Element mcpMessage(Element... lines) {
        return panel("MCP", column(lines))
                .rounded().fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private TreeElement buildMcpTree(List<McpServerStatus> statuses) {
        TreeElement tree = tree();

        for (McpServerStatus status : statuses) {

            TreeNode node = TreeNode.of(mcpServer(status));
            tree.add(node);

            for (McpTool tool : status.tools()) {
                node.add(
                        TreeNode.of(tool.name())
                                .add(TreeNode.of("description").add(TreeNode.of(tool.description())))
                                .add(schemaNode("inputSchema", tool.inputSchema()))
                                .add(schemaNode("outputSchema", tool.outputSchema())));
            }
        }


        return (TreeElement) tree
                .highlightColor(Color.CYAN)
                .title("MCP  ·  r refresh  ·  q back")
                .rounded()
                .fill()
                .id("root")
                .focusable()
                .onKeyEvent(this::handleRootEvent);
    }

    // A schema is a nested Map, so toString() would render it as one unreadable line.
    // Expanding it into nodes lets the tree collapse and indent it instead.
    private TreeNode schemaNode(String label, Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return TreeNode.of(label + ": empty");
        }
        TreeNode node = TreeNode.of(label);
        schema.forEach((key, value) -> node.add(valueNode(key, value)));
        return node;
    }

    private TreeNode valueNode(String label, Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return TreeNode.of(label + ": {}");
            }
            TreeNode node = TreeNode.of(label);
            map.forEach((key, nested) -> node.add(valueNode(String.valueOf(key), nested)));
            return node;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return TreeNode.of(label + ": []");
            }
            // Scalar-only lists stay on one line; anything nested gets its own node.
            if (list.stream().noneMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>)) {
                return TreeNode.of(label + ": " + list);
            }
            TreeNode node = TreeNode.of(label);
            for (int i = 0; i < list.size(); i++) {
                node.add(valueNode("[" + i + "]", list.get(i)));
            }
            return node;
        }
        return TreeNode.of(label + ": " + value);
    }

    private String mcpServer(McpServerStatus status) {
        return status.connected()
                ? "● %s  %s  %s  %s %s".formatted(status.name(), status.url(), status.serverVersion(), status.tools().size(), (status.tools().size() == 1 ? "tool" : "tools"))
                : "○ %s %s %s".formatted(status.name(), status.url(), "connection failed");
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
                /mcp Show connected mcp servers
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

        // Connections are lazy, so a failed server may just need another try.
        if (state.currentScreen() == ScreenMode.MCP && event.isCharIgnoreCase('r')) {
            startMcpProbe();
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
