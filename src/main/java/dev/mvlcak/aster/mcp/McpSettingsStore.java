package dev.mvlcak.aster.mcp;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the MCP server configuration stored in {@code ~/.aster/mcp.json}.
 *
 * <p>The location can be overridden with the {@code ASTER_HOME} environment variable.
 */
public class McpSettingsStore {

    public static final String CONFIG_DIR_NAME = ".aster";
    public static final String CONFIG_FILE_NAME = "mcp.json";

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path configFile;

    public McpSettingsStore() {
        this(defaultConfigFile());
    }

    public McpSettingsStore(Path configFile) {
        this.configFile = configFile.toAbsolutePath().normalize();
    }

    public static Path defaultConfigFile() {
        String asterHome = System.getenv("ASTER_HOME");
        Path base = asterHome == null || asterHome.isBlank()
                ? Path.of(System.getProperty("user.home"), CONFIG_DIR_NAME)
                : Path.of(asterHome);
        return base.resolve(CONFIG_FILE_NAME);
    }

    public Path configFile() {
        return configFile;
    }

    /**
     * Returns the configured servers, or an empty list when no config file exists yet.
     */
    public List<McpClientSetting> load() throws IOException {
        if (!Files.exists(configFile)) {
            return List.of();
        }
        String json = Files.readString(configFile);
        if (json.isBlank()) {
            return List.of();
        }
        return mapper.readValue(json, McpSettings.class).servers();
    }

    public Optional<McpClientSetting> find(String name) throws IOException {
        return load().stream().filter(setting -> setting.name().equals(name)).findFirst();
    }

    /**
     * Stores the given server, replacing any existing entry with the same name.
     */
    public boolean addOrReplace(McpClientSetting setting) throws IOException {
        List<McpClientSetting> servers = new ArrayList<>(load());
        boolean replaced = servers.removeIf(existing -> existing.name().equals(setting.name()));
        servers.add(setting);
        save(servers);
        return replaced;
    }

    /**
     * Removes the server with the given name.
     */
    public boolean remove(String name) throws IOException {
        List<McpClientSetting> servers = new ArrayList<>(load());
        if (!servers.removeIf(existing -> existing.name().equals(name))) {
            return false;
        }
        save(servers);
        return true;
    }

    public void save(List<McpClientSetting> servers) throws IOException {
        Path parent = configFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = mapper.writeValueAsString(new McpSettings(servers));
        Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
    }
}