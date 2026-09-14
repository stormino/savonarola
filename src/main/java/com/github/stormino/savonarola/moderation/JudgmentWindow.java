package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Buffers messages between judgments.
 *
 * The rulebook costs the same tokens whether the window holds one message or twenty-five,
 * so a window of one is the worst case: full price, none of the benefit. The buffer
 * therefore waits for company — but never longer than maxWaitSeconds, so a quiet chat is
 * still judged rather than silently ignored.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JudgmentWindow {

    private final SavonarolaProperties props;
    private final ModerationPipeline pipeline;

    private final List<Message> buffer = new ArrayList<>();
    private Instant oldestQueuedAt;

    /** Called on the long-polling thread, so judging is handed off async. */
    public void offer(Message msg) {
        List<Message> full;
        synchronized (buffer) {
            if (buffer.isEmpty()) oldestQueuedAt = Instant.now();
            buffer.add(msg);
            if (buffer.size() < props.judgmentWindow().maxMessages()) return;
            full = drain();
        }
        log.debug("Window full at {} messages", full.size());
        pipeline.judge(full);
    }

    @Scheduled(fixedDelayString = "${savonarola.judgment-window.tick-seconds}",
               timeUnit = TimeUnit.SECONDS)
    public void flushIfReady() {
        List<Message> due;
        synchronized (buffer) {
            if (!ready()) return;
            due = drain();
        }
        log.debug("Judging a window of {} messages", due.size());
        pipeline.judge(due);
    }

    private boolean ready() {
        if (buffer.isEmpty()) return false;

        var window = props.judgmentWindow();
        if (buffer.size() >= window.minMessages()) return true;

        // Too few to be worth a call on its own, so hold — unless it has waited too long.
        Duration waited = Duration.between(oldestQueuedAt, Instant.now());
        return waited.getSeconds() >= window.maxWaitSeconds();
    }

    private List<Message> drain() {
        List<Message> copy = List.copyOf(buffer);
        buffer.clear();
        oldestQueuedAt = null;
        return copy;
    }
}
