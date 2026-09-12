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
            SavonarolaProperties props, SavonarolaBot bot) throws Exception {
        var app = new TelegramBotsLongPollingApplication();
        app.registerBot(props.telegram().token(), bot);
        return app;
    }
}
