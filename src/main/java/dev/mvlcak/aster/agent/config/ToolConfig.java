package dev.mvlcak.aster.agent.config;

import dev.mvlcak.aster.agent.tool.DiffTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class ToolConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolConfig.class);

    @Bean
    public Path workspaceDirectory() {
        Path workspace = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        log.info("Workspace directory: {}", workspace);
        return workspace;
    }

    @Bean
    public GrepTool grepTool(Path workspaceDirectory) {
        return GrepTool.builder()
                .workingDirectory(workspaceDirectory)
                .build();
    }

    @Bean
    public FileSystemTools fileSystemTools(Path workspaceDirectory) {
        return FileSystemTools.builder()
                .allowedDirectory(workspaceDirectory)
                .build();
    }

    @Bean
    public ShellTools shellTools() {
        return ShellTools.builder().build();
    }

    @Bean
    public DiffTool diffTool() {
        return DiffTool.builder().build();
    }

}
