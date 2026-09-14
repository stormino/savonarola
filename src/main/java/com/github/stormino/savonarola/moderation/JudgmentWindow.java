package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Buffers messages between judgments. Flushed on whichever limit arrives first: the timer
 * so a quiet chat is still judged, the size cap so a burst cannot build a prompt too large
 * for the model or for the daily token budget.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JudgmentWindow {

    private final SavonarolaProperties props;
    private final ModerationPipeline pipeline;

    private final List<Message> buffer = new ArrayList<>();

    /** Called on the long-polling thread, so the judging itself is handed off async. */
    public void offer(Message msg) {
        List<Message> full;
        synchronized (buffer) {
            buffer.add(msg);
            if (buffer.size() < props.judgmentWindow().maxMessages()) return;
            full = drain();
        }
        log.debug("Window full at {} messages", full.size());
        pipeline.judge(full);
    }

    @Scheduled(fixedDelayString = "${savonarola.judgment-window.seconds}", timeUnit = TimeUnit.SECONDS)
    public void flush() {
        List<Message> due;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            due = drain();
        }
        pipeline.judge(due);
    }

    private List<Message> drain() {
        List<Message> copy = List.copyOf(buffer);
        buffer.clear();
        return copy;
    }
}
