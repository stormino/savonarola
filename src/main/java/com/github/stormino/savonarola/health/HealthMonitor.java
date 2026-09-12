package com.github.stormino.savonarola.health;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A bot that silently stops moderating is worse than no bot — so failures are loud. */
@Component
@RequiredArgsConstructor
public class HealthMonitor {

    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean degraded = new AtomicBoolean();
    private volatile Instant lastSuccess = Instant.now();
    private volatile String lastError;

    public void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccess = Instant.now();
        if (degraded.compareAndSet(true, false)) {
            notifier.sendSystem("LLM calls recovered.");
        }
    }

    public void recordFailure(String reason) {
        lastError = reason;
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= props.health().consecutiveFailureThreshold()
                && degraded.compareAndSet(false, true)) {
            notifier.sendSystem("LLM calls failing (" + failures
                    + " consecutive). Moderation is currently degraded. Last error: " + reason);
        }
    }

    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public boolean isDegraded() { return degraded.get(); }
    public Instant getLastSuccess() { return lastSuccess; }
    public String getLastError() { return lastError; }
}
