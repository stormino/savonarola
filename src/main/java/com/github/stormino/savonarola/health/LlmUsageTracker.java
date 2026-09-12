package com.github.stormino.savonarola.health;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LlmUsageTracker {

    private final Map<String, Usage> byModel = new ConcurrentHashMap<>();

    public void record(String model, JsonNode usageNode) {
        Usage usage = byModel.computeIfAbsent(model, m -> new Usage());
        usage.calls.incrementAndGet();
        if (usageNode != null && !usageNode.isMissingNode()) {
            usage.promptTokens.addAndGet(usageNode.path("prompt_tokens").asLong(0));
            usage.completionTokens.addAndGet(usageNode.path("completion_tokens").asLong(0));
        }
    }

    public Map<String, Usage> snapshot() {
        return Map.copyOf(byModel);
    }

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
