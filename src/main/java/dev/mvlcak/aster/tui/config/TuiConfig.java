package dev.mvlcak.aster.tui.config;

import dev.mvlcak.aster.chat.ChatService;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.event.AppEventLoop;
import dev.mvlcak.aster.mcp.McpStatusService;
import dev.mvlcak.aster.tui.AppState;
import dev.mvlcak.aster.tui.ChatPane;
import dev.mvlcak.aster.tui.CommandParser;
import dev.mvlcak.aster.tui.TuiApp;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TuiConfig {


    @Bean
    public TuiApp tuiApp(AppState appState, TuiProperties tuiProperties, ChatPane chatPane,
                         McpStatusService mcpStatusService) {
        return new TuiApp(appState, tuiProperties, chatPane, mcpStatusService);
    }

    @Bean
    public ChatPane chatPane(AppState appState, CommandParser commandParser, AppEventBus appEventBus) {
        return new ChatPane(appState, appEventBus, commandParser);
    }

    @Bean
    public AppEventBus appEventBus() {
        return new AppEventBus();
    }


    @Bean
    public AppEventLoop appEventLoop(AppEventBus appEventBus, AppState appState,
                                     ChatService chatService) {
        return new AppEventLoop(appEventBus, appState, chatService);
    }

    @Bean
    public CommandParser commandParser(){
        return new CommandParser();
    }

    @Bean
    public ApplicationRunner tuiApplicationRunner(TuiApp tuiApp,
                                                  AppEventBus appEventBus,
                                                  AppEventLoop appEventLoop) {
        return args -> {
            appEventLoop.start();
            try {
                tuiApp.run();
            } catch (Exception _) {
                System.exit(1);
            }
        };
    }
}
