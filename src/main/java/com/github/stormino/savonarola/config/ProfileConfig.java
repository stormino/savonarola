package com.github.stormino.savonarola.config;

import com.github.stormino.savonarola.moderation.NoopProfileProvider;
import com.github.stormino.savonarola.moderation.ProfileProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileConfig {

    @Bean
    @ConditionalOnMissingBean(ProfileProvider.class)
    public ProfileProvider noopProfileProvider() {
        return new NoopProfileProvider();
    }
}
