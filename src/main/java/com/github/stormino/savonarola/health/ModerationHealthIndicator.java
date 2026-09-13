package com.github.stormino.savonarola.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * A bot whose process is alive but whose judgments all fail is not healthy — it is
 * silently not moderating, which SPEC 15 treats as worse than being down. Reporting DOWN
 * lets the container restart it instead of leaving it looking fine.
 */
@Component("moderation")
@RequiredArgsConstructor
public class ModerationHealthIndicator implements HealthIndicator {

    private final HealthMonitor monitor;

    @Override
    public Health health() {
        var builder = monitor.isDegraded() ? Health.down() : Health.up();
        builder.withDetail("consecutiveFailures", monitor.getConsecutiveFailures())
               .withDetail("fallbacks", monitor.getFallbacks())
               .withDetail("minutesSinceLastSuccess",
                       Duration.between(monitor.getLastSuccess(), Instant.now()).toMinutes());
        if (monitor.getLastError() != null) {
            builder.withDetail("lastError", monitor.getLastError());
        }
        return builder.build();
    }
}
