package dev.mvlcak.aster.tui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a subset of Markdown to tamboui BBCode-style markup.
 */
public final class MarkdownToMarkup {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    private MarkdownToMarkup() {}

    public static String convert(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        boolean inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Escape literal brackets so tamboui doesn't interpret them as tags
            line = line.replace("[", "[[").replace("]", "]]");

            if (line.stripLeading().startsWith("```")) {
                String stripped = line.stripLeading();
                String indent = line.substring(0, line.length() - stripped.length());
                String afterFence = stripped.substring(3);

                int langEnd = 0;
                while (langEnd < afterFence.length()) {
                    char c = afterFence.charAt(langEnd);
                    if (!Character.isLowerCase(c) && !Character.isDigit(c)) break;
                    langEnd++;
                }
                String lang = afterFence.substring(0, langEnd);
                String trailingCode = afterFence.substring(langEnd);

                boolean opening = !inCodeBlock;
                inCodeBlock = !inCodeBlock;

                out.append("[dim]").append(indent).append("```").append(lang).append("[/dim]");
                if (opening && !trailingCode.isEmpty()) {
                    out.append("\n[green]").append(trailingCode).append("[/green]");
                }
            } else if (inCodeBlock) {
                out.append("[green]").append(line).append("[/green]");
            } else {
                out.append(convertLine(line));
            }

            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString();
    }

    private static String convertLine(String line) {
        String trimmed = line.stripLeading();

        // Headers
        if (trimmed.startsWith("### ")) {
            return "[bold][cyan]" + trimmed.substring(4) + "[/cyan][/bold]";
        }
        if (trimmed.startsWith("## ")) {
            return "[bold][cyan]" + trimmed.substring(3) + "[/cyan][/bold]";
        }
        if (trimmed.startsWith("# ")) {
            return "[bold][cyan]" + trimmed.substring(2) + "[/cyan][/bold]";
        }

        // Bullet lists
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            String indent = line.substring(0, line.length() - trimmed.length());
            String content = trimmed.substring(2);
            return indent + "[yellow]•[/yellow] " + convertInline(content);
        }

        // Numbered lists
        if (trimmed.matches("^\\d+\\.\\s.*")) {
            int dotPos = trimmed.indexOf('.');
            String number = trimmed.substring(0, dotPos + 1);
            String content = trimmed.substring(dotPos + 2);
            String indent = line.substring(0, line.length() - trimmed.length());
            return indent + "[yellow]" + number + "[/yellow] " + convertInline(content);
        }

        return convertInline(line);
    }

    private static String convertInline(String text) {
        // Order matters: bold before italic (** before *)
        text = replacePattern(text, INLINE_CODE, "[green]", "[/green]");
        text = replacePattern(text, BOLD, "[bold]", "[/bold]");
        text = replacePattern(text, ITALIC, "[italic]", "[/italic]");
        return text;
    }

    private static String replacePattern(String text, Pattern pattern, String openTag, String closeTag) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(openTag + matcher.group(1) + closeTag));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}