package dev.mvlcak.aster.tui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aster.tui")
public record TuiProperties(
        String appName,
        long tickRateMs,
        long resizeGracePeriodMs) {

    public TuiProperties {
        appName = hasText(appName) ? appName : "Aster";
        tickRateMs = tickRateMs > 0 ? tickRateMs : 75L;
        resizeGracePeriodMs = resizeGracePeriodMs > 0 ? resizeGracePeriodMs : 50L;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
