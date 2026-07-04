package dev.mvlcak.aster.tui.config;

import dev.mvlcak.aster.chat.StreamingChatService;
import dev.mvlcak.aster.tui.CommandParser;
import dev.mvlcak.aster.event.AppEventBus;
import dev.mvlcak.aster.event.AppEventLoop;
import dev.mvlcak.aster.tui.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TuiConfig {


    @Bean
    public TuiApp tuiApp(AppState appState, TuiProperties tuiProperties, ChatPane chatPane){
        return new TuiApp(appState, tuiProperties, chatPane);
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
                                     StreamingChatService streamingChatService) {
        return new AppEventLoop(appEventBus, appState, streamingChatService);
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
            }
            catch (Exception e) {
                System.out.println("Terminal not supported");
                System.exit(1);
            }
        };
    }
}
