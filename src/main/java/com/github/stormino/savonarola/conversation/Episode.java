package com.github.stormino.savonarola.conversation;

import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A live disagreement between two or more people (SPEC 18). The unit of moderation is the
 * episode rather than the message: what a human reacts to is a fight developing, not a
 * single sharp word.
 *
 * Held in memory. Episodes last minutes, so a restart losing one costs a re-deliberation,
 * not a lost sanction — everything that actually happened is persisted as a Decision.
 */
public class Episode {

    private final Set<Long> participants = new LinkedHashSet<>();
    private final List<Message> messages = new ArrayList<>();
    private final Instant startedAt = Instant.now();

    private Instant lastActivityAt = Instant.now();
    private int deliberatedUpTo;
    private int deliberations;

    public void add(Message msg, long senderId, Long targetId) {
        messages.add(msg);
        participants.add(senderId);
        if (targetId != null) participants.add(targetId);
        lastActivityAt = Instant.now();
    }

    public boolean involves(long userId) {
        return participants.contains(userId);
    }

    public boolean isIdle(int idleSeconds) {
        return Duration.between(lastActivityAt, Instant.now()).getSeconds() >= idleSeconds;
    }

    /** First look is immediate; after that only once the argument has actually moved on. */
    public boolean deservesDeliberation(int redeliberateEvery) {
        return deliberations == 0 || pendingCount() >= redeliberateEvery;
    }

    /** Only what has not been ruled on yet: judging a message twice could sanction it twice. */
    public List<Message> pendingJudgment() {
        return List.copyOf(messages.subList(deliberatedUpTo, messages.size()));
    }

    /**
     * What came before, as context. The model needs the argument's shape to judge where it
     * has got to, but must not re-rule on messages already dealt with.
     */
    public List<Message> priorContext(int cap) {
        int from = Math.max(0, deliberatedUpTo - cap);
        return List.copyOf(messages.subList(from, deliberatedUpTo));
    }

    public int pendingCount() {
        return messages.size() - deliberatedUpTo;
    }

    public void markDeliberated() {
        deliberations++;
        deliberatedUpTo = messages.size();
    }

    public Set<Long> participants() { return Set.copyOf(participants); }
    public int size() { return messages.size(); }
    public int deliberations() { return deliberations; }
    public Instant startedAt() { return startedAt; }
}
