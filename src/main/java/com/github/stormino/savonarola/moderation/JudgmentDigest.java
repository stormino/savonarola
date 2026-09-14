package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The verbose stream should show what the bot did, not prove once a minute that it is
 * running. Violations are reported the moment they happen; quiet windows are counted and
 * summarised hourly, and an hour with nothing in it says nothing at all.
 */
@Component
@RequiredArgsConstructor
public class JudgmentDigest {

    private final AdminNotifier notifier;

    private final AtomicInteger windows = new AtomicInteger();
    private final AtomicInteger messages = new AtomicInteger();
    private final AtomicInteger violations = new AtomicInteger();

    public void recordWindow(int messagesJudged, int violationsFound) {
        windows.incrementAndGet();
        messages.addAndGet(messagesJudged);
        violations.addAndGet(violationsFound);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void publish() {
        int w = windows.getAndSet(0);
        int m = messages.getAndSet(0);
        int v = violations.getAndSet(0);
        if (w == 0) return;

        notifier.sendToOwner("🧪 <b>[DRY RUN] ultima ora</b>\n"
                + m + " messaggi valutati in " + w + " finestre"
                + " (media " + String.format("%.1f", (double) m / w) + " per finestra)\n"
                + v + (v == 1 ? " violazione" : " violazioni"));
    }
}
