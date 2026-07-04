package dev.mvlcak.aster.tui.config;

import dev.mvlcak.aster.tui.AppState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public AppState appState() {
        return new AppState();
    }

}
