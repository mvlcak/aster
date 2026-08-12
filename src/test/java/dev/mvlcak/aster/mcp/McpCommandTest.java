package dev.mvlcak.aster.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpCommandTest {

    @TempDir
    Path home;

    private McpSettingsStore store;
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private McpCommand command;

    @BeforeEach
    void setUp() {
        store = new McpSettingsStore(home.resolve(".aster").resolve("mcp.json"));
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        command = new McpCommand(store, new PrintStream(out), new PrintStream(err));
    }

    @Test
    void addWritesStreamableHttpServer() throws IOException {
        int exitCode = command.run(new String[]{
                "mcp", "add", "--transport", "http", "dependency-upgrader", "http://localhost:8080/mcp"});

        assertThat(exitCode).isZero();
        assertThat(store.load()).containsExactly(new McpClientSetting(
                "dependency-upgrader", "http://localhost:8080", "/mcp", ProtocolType.STREAMABLE_HTTP));
        assertThat(Files.readString(store.configFile())).contains("\"servers\"", "\"STREAMABLE_HTTP\"");
    }

    @Test
    void addDefaultsEndpointPerTransportWhenUrlHasNoPath() throws IOException {
        command.run(new String[]{"mcp", "add", "--transport", "sse", "docs", "http://localhost:8080"});

        assertThat(store.load()).containsExactly(
                new McpClientSetting("docs", "http://localhost:8080", "/sse", ProtocolType.SSE));
    }

    @Test
    void addReplacesServerWithSameName() throws IOException {
        command.run(new String[]{"mcp", "add", "upgrader", "http://localhost:8080/mcp"});
        int exitCode = command.run(new String[]{"mcp", "add", "upgrader", "http://localhost:9090/mcp"});

        assertThat(exitCode).isZero();
        assertThat(store.load()).singleElement()
                .extracting(McpClientSetting::url).isEqualTo("http://localhost:9090");
        assertThat(out.toString()).contains("Updated MCP server 'upgrader'");
    }

    @Test
    void addRejectsUnknownTransport() throws IOException {
        int exitCode = command.run(new String[]{"mcp", "add", "--transport", "stdio", "x", "http://localhost:8080"});

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("Unsupported transport 'stdio'");
        assertThat(store.load()).isEmpty();
    }

    @Test
    void addRejectsMissingUrl() throws IOException {
        int exitCode = command.run(new String[]{"mcp", "add", "dependency-upgrader"});

        assertThat(exitCode).isEqualTo(2);
        assertThat(store.load()).isEmpty();
    }

    @Test
    void listPrintsConfiguredServers() {
        command.run(new String[]{"mcp", "add", "upgrader", "http://localhost:8080/mcp"});
        out.reset();

        int exitCode = command.run(new String[]{"mcp", "list"});

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("upgrader", "STREAMABLE_HTTP", "http://localhost:8080/mcp");
    }

    @Test
    void removeDeletesServer() throws IOException {
        command.run(new String[]{"mcp", "add", "upgrader", "http://localhost:8080/mcp"});

        assertThat(command.run(new String[]{"mcp", "remove", "upgrader"})).isZero();
        assertThat(store.load()).isEmpty();
        assertThat(command.run(new String[]{"mcp", "remove", "upgrader"})).isEqualTo(1);
    }

    @Test
    void loadReturnsEmptyListWhenConfigMissing() throws IOException {
        assertThat(store.load()).isEmpty();
    }

    @Test
    void settingsRoundTripThroughFile() throws IOException {
        store.save(List.of(
                new McpClientSetting("a", "http://localhost:1", "/mcp", ProtocolType.STREAMABLE_HTTP),
                new McpClientSetting("b", "http://localhost:2", "/sse", ProtocolType.SSE)));

        assertThat(store.load()).hasSize(2)
                .extracting(McpClientSetting::name).containsExactly("a", "b");
    }
}