package com.github.stormino.savonarola;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class SavonarolaApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(SavonarolaApplication.class, args);
        // Which build is actually running is the first question any incident starts with.
        context.getBeanProvider(BuildProperties.class).ifAvailable(build ->
                log.info("Savonarola {} (built {})", build.getVersion(), build.getTime()));
    }
}
