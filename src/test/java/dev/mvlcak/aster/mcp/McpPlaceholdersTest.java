package dev.mvlcak.aster.mcp;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpPlaceholdersTest {

    @Test
    void resolvesWorkspaceToTheCurrentDirectory() {
        Path workspace = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

        assertThat(McpPlaceholders.resolve("${workspace}")).isEqualTo(workspace.toString());
    }

    @Test
    void resolvesEnvironmentVariableInsideALargerValue() {
        String variable = System.getenv().keySet().stream().findFirst().orElseThrow();

        assertThat(McpPlaceholders.resolve("Bearer ${env:" + variable + "}"))
                .isEqualTo("Bearer " + System.getenv(variable));
    }

    @Test
    void leavesValuesWithoutPlaceholdersAlone() {
        assertThat(McpPlaceholders.resolve("/Users/martin/projects/aster"))
                .isEqualTo("/Users/martin/projects/aster");
    }

    @Test
    void resolvesEveryHeaderValue() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("IJ_MCP_SERVER_PROJECT_PATH", "${workspace}");
        headers.put("X-Api-Version", "2");

        assertThat(McpPlaceholders.resolve(headers))
                .containsEntry("IJ_MCP_SERVER_PROJECT_PATH", System.getProperty("user.dir"))
                .containsEntry("X-Api-Version", "2");
    }

    @Test
    void failsOnUnsetEnvironmentVariable() {
        assertThatThrownBy(() -> McpPlaceholders.resolve("${env:ASTER_DEFINITELY_NOT_SET}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASTER_DEFINITELY_NOT_SET");
    }

    @Test
    void failsOnUnknownPlaceholder() {
        assertThatThrownBy(() -> McpPlaceholders.resolve("${home}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${workspace}");
    }
}
