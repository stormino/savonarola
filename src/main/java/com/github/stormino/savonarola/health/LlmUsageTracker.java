package com.github.stormino.savonarola.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.stormino.savonarola.llm.LlmCallType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Counts live in memory, so every figure reported from here is "since startup". */
@Component
public class LlmUsageTracker {

    private final Map<Key, Usage> usage = new ConcurrentHashMap<>();

    public void record(String model, LlmCallType callType, JsonNode usageNode) {
        Usage entry = usage.computeIfAbsent(new Key(model, callType), k -> new Usage());
        entry.calls.incrementAndGet();
        if (usageNode != null && !usageNode.isMissingNode()) {
            entry.promptTokens.addAndGet(usageNode.path("prompt_tokens").asLong(0));
            entry.completionTokens.addAndGet(usageNode.path("completion_tokens").asLong(0));
        }
    }

    public Map<Key, Usage> snapshot() {
        return Map.copyOf(usage);
    }

    public record Key(String model, LlmCallType callType) {}

    public static class Usage {
        final AtomicLong calls = new AtomicLong();
        final AtomicLong promptTokens = new AtomicLong();
        final AtomicLong completionTokens = new AtomicLong();

        public long calls() { return calls.get(); }
        public long promptTokens() { return promptTokens.get(); }
        public long completionTokens() { return completionTokens.get(); }
        public long totalTokens() { return promptTokens.get() + completionTokens.get(); }
    }
}
