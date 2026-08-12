package dev.mvlcak.aster;

import dev.mvlcak.aster.mcp.McpCommand;
import dev.mvlcak.aster.mcp.McpSettingsStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AsterApplication {

	public static void main(String[] args) {
        // `aster mcp ...` only touches ~/.aster/mcp.json, so it runs without booting the TUI.
        if (McpCommand.matches(args)) {
            System.exit(new McpCommand(new McpSettingsStore(), System.out, System.err).run(args));
        }

		SpringApplication.run(AsterApplication.class, args);
	}

}