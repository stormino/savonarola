package com.github.stormino.savonarola.conversation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.ModerationPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Watches the stream and decides when the bot should think.
 *
 * Every message updates state for free. A model is consulted only when the structure of
 * the conversation says something is happening: two people going back and forth quickly,
 * which is what a fight looks like from the outside. Ordinary conversation — the vast
 * majority — never reaches an LLM at all.
 *
 * Replaces the fixed judgment window, which judged arbitrary slices of the clock whether
 * or not anything was going on in them.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConversationTracker {

    private final SavonarolaProperties props;
    private final ModerationPipeline pipeline;

    private final Map<String, PairExchange> exchanges = new HashMap<>();
    private final Map<Long, Episode> episodes = new HashMap<>();
    private final List<Message> unjudged = new ArrayList<>();

    /** Called on the long-polling thread; deliberation is handed off async. */
    public void observe(Message msg) {
        long senderId = msg.getFrom().getId();
        Long targetId = targetOf(msg);

        Episode toDeliberate = null;
        synchronized (this) {
            Episode live = liveEpisodeFor(senderId, targetId);
            if (live != null) {
                live.add(msg, senderId, targetId);
                if (live.deservesDeliberation(props.conflict().redeliberateEvery())) {
                    toDeliberate = live;
                }
            } else if (targetId != null && heatingUp(senderId, targetId)) {
                Episode opened = open(senderId, targetId);
                opened.add(msg, senderId, targetId);
                toDeliberate = opened;
            } else {
                // A message belongs to an episode or to the sweep, never to both:
                // judging the same message twice could sanction it twice.
                unjudged.add(msg);
            }
        }

        if (toDeliberate != null) deliberate(toDeliberate);
    }

    /**
     * Episodes catch arguments. They do not catch a single insult nobody answers, so
     * whatever has gone unjudged is swept periodically — rarely, because this is the
     * expensive path and most of what it sees will be ordinary talk.
     */
    @Scheduled(fixedDelayString = "${savonarola.conflict.sweep-minutes}",
               timeUnit = TimeUnit.MINUTES)
    public void sweepUnjudged() {
        List<Message> batch;
        synchronized (this) {
            if (unjudged.isEmpty()) return;
            int cap = props.conflict().sweepMaxMessages();
            int from = Math.max(0, unjudged.size() - cap);
            batch = List.copyOf(unjudged.subList(from, unjudged.size()));
            unjudged.clear();
        }
        log.info("Sweep: judging {} messages nothing replied to", batch.size());
        pipeline.judge(batch, List.of());
    }

    @Scheduled(fixedDelayString = "${savonarola.conflict.tick-seconds}",
               timeUnit = TimeUnit.SECONDS)
    public synchronized void closeIdleEpisodes() {
        Iterator<Map.Entry<Long, Episode>> it = episodes.entrySet().iterator();
        while (it.hasNext()) {
            Episode episode = it.next().getValue();
            if (episode.isIdle(props.conflict().idleSeconds())) {
                log.info("Episode closed: {} messages, {} deliberations, participants {}",
                        episode.size(), episode.deliberations(), episode.participants());
                it.remove();
            }
        }
        exchanges.values().removeIf(e -> e.countWithin(props.conflict().pairWindowSeconds()) == 0);
    }

    private void deliberate(Episode episode) {
        int cap = props.conflict().maxEpisodeMessages();
        List<Message> pending = episode.pendingJudgment();
        List<Message> context = episode.priorContext(cap);
        episode.markDeliberated();

        log.info("Episode deliberation #{}: {} new messages between {} ({} for context)",
                episode.deliberations(), pending.size(), episode.participants(), context.size());
        pipeline.judge(pending, context);
    }

    private boolean heatingUp(long senderId, long targetId) {
        var conflict = props.conflict();
        int exchanged = exchanges
                .computeIfAbsent(PairExchange.key(senderId, targetId), k -> new PairExchange())
                .record(Instant.now(), conflict.pairWindowSeconds());
        return exchanged >= conflict.pairExchanges();
    }

    private Episode liveEpisodeFor(long senderId, Long targetId) {
        for (Episode episode : episodes.values()) {
            if (episode.involves(senderId) || (targetId != null && episode.involves(targetId))) {
                return episode;
            }
        }
        return null;
    }

    private Episode open(long senderId, long targetId) {
        Episode episode = new Episode();
        episodes.put(senderId, episode);
        log.info("Episode opened between {} and {}", senderId, targetId);
        return episode;
    }

    private static Long targetOf(Message msg) {
        if (msg.getReplyToMessage() == null || msg.getReplyToMessage().getFrom() == null) {
            return null;
        }
        Long targetId = msg.getReplyToMessage().getFrom().getId();
        return targetId.equals(msg.getFrom().getId()) ? null : targetId;
    }
}
