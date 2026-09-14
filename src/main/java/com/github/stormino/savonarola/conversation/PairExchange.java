package com.github.stormino.savonarola.conversation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Free attention: who is replying to whom, how often, how recently. No model involved.
 *
 * A human notices raised voices without deliberating about them, and the signals that say
 * an argument is heating up are structural — a pair going back and forth quickly, or one
 * person being answered repeatedly by the same other. Only when these cross a threshold is
 * the model asked anything, so ordinary conversation costs nothing at all.
 */
public class PairExchange {

    private final Deque<Instant> replies = new ArrayDeque<>();

    public int record(Instant at, int windowSeconds) {
        replies.addLast(at);
        prune(windowSeconds);
        return replies.size();
    }

    public int countWithin(int windowSeconds) {
        prune(windowSeconds);
        return replies.size();
    }

    private void prune(int windowSeconds) {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(windowSeconds));
        while (!replies.isEmpty() && replies.peekFirst().isBefore(cutoff)) {
            replies.removeFirst();
        }
    }

    public boolean isEmpty() {
        return replies.isEmpty();
    }

    /** Unordered: a quarrel between A and B is one exchange, not two. */
    public static String key(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
