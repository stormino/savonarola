package com.github.stormino.savonarola.moderation;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * How often the expensive history lookup fires (SPEC 13). Kept in memory rather than as a
 * row per judged message: the denominator is every message judged, and a table that size
 * would cost more than the figure is worth. Reported as "since startup" accordingly.
 */
@Component
public class PipelineMetrics {

    private final AtomicLong judged = new AtomicLong();
    private final AtomicLong extendedHistoryUsed = new AtomicLong();

    private final AtomicLong windows = new AtomicLong();

    public void recordJudged(boolean usedExtendedHistory, int messagesInWindow) {
        windows.incrementAndGet();
        judged.addAndGet(messagesInWindow);
        if (usedExtendedHistory) extendedHistoryUsed.incrementAndGet();
    }

    public long windows() { return windows.get(); }

    public long judged() { return judged.get(); }
    public long extendedHistoryUsed() { return extendedHistoryUsed.get(); }

    /** Against windows, not messages: the lookup is decided once per window. */
    public double extendedHistoryRate() {
        long total = windows.get();
        return total == 0 ? 0 : (double) extendedHistoryUsed.get() / total;
    }
}
