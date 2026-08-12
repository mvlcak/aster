package dev.mvlcak.aster.tui;

import dev.mvlcak.aster.event.AppEvent;
import dev.mvlcak.aster.event.AppEventBus;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.input.TextInputState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.tamboui.toolkit.Toolkit.*;

public class ChatPane implements Element {

    private static final int OVERHEAD_ROWS = 8;
    private static final int SCROLL_STEP = 5;
    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final AppState appState;
    private final AppEventBus bus;
    private final CommandParser commandParser;
    private final TextInputState inputState = new TextInputState();
    private int scrollLinesUp = 0;
    private int tabCycleIndex = -1;

    public ChatPane(AppState appState, AppEventBus bus, CommandParser commandParser) {
        this.appState = appState;
        this.bus = bus;
        this.commandParser = commandParser;
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        int transcriptLines = Math.max(1, area.height() - OVERHEAD_ROWS);
        int maxWidth = Math.max(10, area.width() - 2);
        String transcript = windowedTranscript(appState.messages(), transcriptLines, maxWidth);
        String input = inputState.text() == null ? "" : inputState.text();
        String suggestions = input.startsWith("/")
                ? String.join("  ", commandParser.matchingCommands(input))
                : "";
        String placeholder = "Type a prompt or /help";

        var chatPanel = column(
                        markupTextArea(transcript)
                                .wrapWord()
                                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                                .focusable(false)
                                .fill(),
                        text(scrollLinesUp > 0 ? "\u2191 scrolled — PgDn to return" : "")
                                .fg(Color.YELLOW),
                        text(suggestions.isBlank() ? "" : suggestions)
                                .fg(Color.CYAN),
                        row(
                                text(appState.isStreaming()
                                        ? "  " + SPINNER[(int) ((System.currentTimeMillis() / 100) % SPINNER.length)] + " " + statusLabel()
                                        : "")
                                        .bold()
                                        .fg(Color.YELLOW), text( appState.isStreaming() ? " " + appState.thinkingElapsedSeconds() +" s": "")
                        ).length(1),
                        row(text("─".repeat(500)).fg(Color.WHITE)).length(1),
                        textInput(inputState)
                                .placeholder(placeholder)
                                .cursorRequiresFocus(false)
                                .focusable(false)
                                .showCursor(true),
                        row(text("─".repeat(500)).fg(Color.WHITE)).length(1)
        ).fill();
        chatPanel.render(frame, area, context);
    }

    private String statusLabel() {
        String status = appState.currentActivityStatus();
        return status == null || status.isBlank() ? "thinking…" : status;
    }

    @Override
    public Constraint constraint() {
        return Constraint.fill(3);
    }

    @Override
    public Size preferredSize(int w, int h, RenderContext ctx) {
        return Size.UNKNOWN;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (event.isKey(KeyCode.PAGE_UP)) {
            scrollLinesUp += SCROLL_STEP;
            return EventResult.HANDLED;
        }
        if (event.isKey(KeyCode.PAGE_DOWN)) {
            scrollLinesUp = Math.max(0, scrollLinesUp - SCROLL_STEP);
            return EventResult.HANDLED;
        }
        if (event.isKey(KeyCode.TAB)) {
            String current = inputState.text() == null ? "" : inputState.text();
            if (current.startsWith("/")) {
                List<String> matches = commandParser.matchingCommands(current);
                if (!matches.isEmpty()) {
                    tabCycleIndex = (tabCycleIndex + 1) % matches.size();
                    inputState.setText(matches.get(tabCycleIndex));
                }
            }
            return EventResult.HANDLED;
        }
        tabCycleIndex = -1;
        if (event.isConfirm()) {
            scrollLinesUp = 0;
            submitInput();
            return EventResult.HANDLED;
        }
        return Toolkit.handleTextInputKey(inputState, event) ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    @Override
    public EventResult handleMouseEvent(MouseEvent event) {
        if (event.isPress()) {
            return EventResult.HANDLED;
        }
        if (event.isScroll()) {
            if (event.kind() == MouseEventKind.SCROLL_UP) {
                scrollLinesUp += SCROLL_STEP;
            } else if (event.kind() == MouseEventKind.SCROLL_DOWN) {
                scrollLinesUp = Math.max(0, scrollLinesUp - SCROLL_STEP);
            }
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }


    private void submitInput() {
        String input = inputState.text() == null ? "" : inputState.text().trim();
        inputState.clear();
        if (input.isBlank()) return;

        Optional<Command> cmd = commandParser.parse(input);
        if (cmd.isPresent()) {
            handleSlashCommand(cmd.get());
            return;
        }
        bus.dispatch(new AppEvent.UserInput(input));
    }

    private void handleSlashCommand(Command slashCommand) {
        switch (slashCommand) {

            case HELP -> appState.switchScreen(ScreenMode.HELP);
            case USAGE -> appState.switchScreen(ScreenMode.USAGE);
            case MCP -> appState.switchScreen(ScreenMode.MCP);
            case CLEAR -> bus.dispatch(new AppEvent.ClearSession());

        }
    }

    private String windowedTranscript(List<ChatTranscriptEntry> entries, int visibleLines, int maxWidth) {
        if (entries.isEmpty()) return "No conversation yet.";
        StringBuilder out = new StringBuilder();
        for (ChatTranscriptEntry entry : entries) {
            if (!out.isEmpty()) out.append("\n\n");
            String roleLabel = switch (entry.role()) {
                case USER -> "[bold][cyan]You[/cyan][/bold]";
                case ASSISTANT -> "[bold][green]Aster[/green][/bold]";
                case SYSTEM -> "[bold][yellow]System[/yellow][/bold]";
            };
            String body = MarkdownToMarkup.convert(entry.text());
            out.append(roleLabel).append(":\n").append(body);
        }
        String[] rawLines = out.toString().split("\n", -1);
        List<String> wrapped = new ArrayList<>();
        for (String line : rawLines) {
            if (line.length() <= maxWidth) {
                wrapped.add(line);
            } else {
                wrapLine(line, maxWidth, wrapped);
            }
        }
        int end = Math.max(0, wrapped.size() - scrollLinesUp);
        int start = Math.max(0, end - visibleLines);
        return String.join("\n", wrapped.subList(start, end));
    }

    private void wrapLine(String line, int maxWidth, List<String> out) {
        int pos = 0;
        while (pos < line.length()) {
            if (pos + maxWidth >= line.length()) {
                out.add(line.substring(pos));
                break;
            }
            int breakAt = line.lastIndexOf(' ', pos + maxWidth);
            if (breakAt <= pos) {
                breakAt = pos + maxWidth;
            }
            out.add(line.substring(pos, breakAt));
            pos = breakAt;
            if (pos < line.length() && line.charAt(pos) == ' ') {
                pos++;
            }
        }
    }
}
