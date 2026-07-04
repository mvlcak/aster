package dev.mvlcak.aster.tui;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CommandParser {

    public Optional<Command> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim();
        if (!normalized.startsWith("/")) {
            return Optional.empty();
        }

        return switch (normalized) {
            case "/help" -> Optional.of(Command.HELP);
            case "/usage" -> Optional.of(Command.USAGE);
            case "/clear" -> Optional.of(Command.CLEAR);
            default -> Optional.empty();
        };
    }

    public List<String> matchingCommands(String prefix) {
        if (prefix == null || prefix.isBlank()) return List.of();
        String lower = prefix.trim().toLowerCase();
        return Arrays.stream(Command.values())
                .map(cmd -> "/" + cmd.name().toLowerCase())
                .filter(cmd -> cmd.startsWith(lower))
                .toList();
    }}
