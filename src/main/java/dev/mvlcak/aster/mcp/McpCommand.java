package dev.mvlcak.aster.mcp;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * command line, handled before the TUI (and the Spring context) starts.
 *
 * aster mcp add [--transport http|sse] [--header name=value]... &lt;name&gt; &lt;url&gt;
 * aster mcp list
 * aster mcp remove &lt;name&gt;
 */
public final class McpCommand {

    public static final String NAME = "mcp";

    private static final String USAGE = """
            Usage:
              aster mcp add [--transport http|sse] [--header <name>=<value>]... <name> <url>
              aster mcp list
              aster mcp remove <name>
            
            Header values may use ${workspace} (the directory aster runs in) and ${env:NAME}.
            
            Examples:
              aster mcp add --transport http dependency-upgrader http://localhost:8080/mcp
              aster mcp add --transport sse docs http://localhost:8080/sse
              aster mcp add -H 'IJ_MCP_SERVER_PROJECT_PATH=${workspace}' idea http://127.0.0.1:64342/stream
              aster mcp add -H 'Authorization=Bearer ${env:DOCS_TOKEN}' docs https://docs.example.com/mcp
            """;

    private final McpSettingsStore store;
    private final PrintStream out;
    private final PrintStream err;

    public McpCommand(McpSettingsStore store, PrintStream out, PrintStream err) {
        this.store = store;
        this.out = out;
        this.err = err;
    }

    public static boolean matches(String[] args) {
        return args.length > 0 && NAME.equals(args[0]);
    }

    public int run(String[] args) {
        if (args.length < 2) {
            err.print(USAGE);
            return 2;
        }
        try {
            return switch (args[1]) {
                case "add" -> add(rest(args));
                case "list" -> list();
                case "remove", "rm" -> remove(rest(args));
                case "help", "--help", "-h" -> {
                    out.print(USAGE);
                    yield 0;
                }
                default -> {
                    err.println("Unknown subcommand 'mcp " + args[1] + "'");
                    err.print(USAGE);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("Failed to update " + store.configFile() + ": " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("Failed to read " + store.configFile() + ": " + e.getMessage());
            return 1;
        }
    }

    private int add(List<String> args) throws IOException {
        ProtocolType protocolType = ProtocolType.STREAMABLE_HTTP;
        Map<String, String> headers = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--transport".equals(arg) || "-t".equals(arg)) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                protocolType = ProtocolType.fromTransport(args.get(++i));
            } else if (arg.startsWith("--transport=")) {
                protocolType = ProtocolType.fromTransport(arg.substring("--transport=".length()));
            } else if ("--header".equals(arg) || "-H".equals(arg)) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException("Missing value for " + arg);
                }
                putHeader(headers, args.get(++i));
            } else if (arg.startsWith("--header=")) {
                putHeader(headers, arg.substring("--header=".length()));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option '" + arg + "'\n" + USAGE);
            } else {
                positional.add(arg);
            }
        }

        if (positional.size() != 2) {
            throw new IllegalArgumentException(
                    "Expected <name> and <url>\n" + USAGE);
        }

        McpClientSetting setting =
                McpClientSetting.fromUrl(positional.get(0), positional.get(1), protocolType, headers);
        boolean replaced = store.addOrReplace(setting);
        out.printf("%s MCP server '%s' (%s) %s in %s%n",
                replaced ? "Updated" : "Added",
                setting.name(),
                setting.protocolType(),
                setting.fullUrl(),
                store.configFile());
        setting.maskedHeaders().forEach((header, value) -> out.printf("  %s: %s%n", header, value));
        return 0;
    }

    private static void putHeader(Map<String, String> headers, String header) {
        int separator = header.indexOf('=');
        if (separator < 1) {
            throw new IllegalArgumentException(
                    "Expected a header as <name>=<value>, got '" + header + "'");
        }
        headers.put(header.substring(0, separator).trim(), header.substring(separator + 1));
    }

    private int list() throws IOException {
        List<McpClientSetting> servers = store.load();
        if (servers.isEmpty()) {
            out.println("No MCP servers configured (" + store.configFile() + ")");
            return 0;
        }
        for (McpClientSetting setting : servers) {
            out.printf("%-24s %-16s %s%n", setting.name(), setting.protocolType(), setting.fullUrl());
            setting.maskedHeaders().forEach((header, value) -> out.printf("  %s: %s%n", header, value));
        }
        return 0;
    }

    private int remove(List<String> args) throws IOException {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected <name>\n" + USAGE);
        }
        String name = args.getFirst();
        if (!store.remove(name)) {
            err.println("No MCP server named '" + name + "'");
            return 1;
        }
        out.println("Removed MCP server '" + name + "'");
        return 0;
    }

    private static List<String> rest(String[] args) {
        return List.of(args).subList(2, args.length);
    }
}