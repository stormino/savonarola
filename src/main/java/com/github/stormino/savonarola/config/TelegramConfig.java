package com.github.stormino.savonarola.config;

import com.github.stormino.savonarola.telegram.SavonarolaBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@EnableConfigurationProperties(SavonarolaProperties.class)
public class TelegramConfig {

    @Bean
    public TelegramClient telegramClient(SavonarolaProperties props) {
        return new OkHttpTelegramClient(props.telegram().token());
    }

    @Bean(destroyMethod = "close")
    public TelegramBotsLongPollingApplication botsApplication(
            SavonarolaProperties props, SavonarolaBot bot) {
        var app = new TelegramBotsLongPollingApplication();
        try {
            app.registerBot(props.telegram().token(), bot);
        } catch (Exception e) {
            // Telegram reports a bad token as an empty-message exception, which tells a
            // first-time deployer nothing. This is the most likely first-run mistake.
            throw new IllegalStateException(
                    "Could not register the bot with Telegram. Check that SAV_BOT_TOKEN is the "
                    + "token from @BotFather and that this host can reach api.telegram.org. "
                    + "Telegram said: " + describe(e), e);
        }
        return app;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }
}
