package com.github.stormino.savonarola.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationHealthIndicatorTest {

    private final HealthMonitor monitor = mock(HealthMonitor.class);
    private final ModerationHealthIndicator indicator = new ModerationHealthIndicator(monitor);

    @Test
    void reportsUpWhileJudgmentsAreLanding() {
        when(monitor.isDegraded()).thenReturn(false);
        when(monitor.getLastSuccess()).thenReturn(Instant.now());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenTheBotIsAliveButNoLongerModerating() {
        when(monitor.isDegraded()).thenReturn(true);
        when(monitor.getConsecutiveFailures()).thenReturn(7);
        when(monitor.getLastSuccess()).thenReturn(Instant.now().minus(3, ChronoUnit.HOURS));
        when(monitor.getLastError()).thenReturn("429 rate limited");

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("consecutiveFailures", 7)
                .containsEntry("minutesSinceLastSuccess", 180L)
                .containsEntry("lastError", "429 rate limited");
    }

    @Test
    void omitsTheErrorDetailWhenNothingHasFailedYet() {
        when(monitor.getLastSuccess()).thenReturn(Instant.now());
        when(monitor.getLastError()).thenReturn(null);

        assertThat(indicator.health().getDetails()).doesNotContainKey("lastError");
    }
}
