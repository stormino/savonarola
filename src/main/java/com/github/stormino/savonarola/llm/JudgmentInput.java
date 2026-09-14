package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.store.StoredMessage;

import java.util.List;
import java.util.Map;

/** Everything the judge sees for one window of messages. */
public record JudgmentInput(
        List<Rule> activeRules,
        Map<String, List<RuleExample>> examplesByRule,
        List<StoredMessage> candidates,
        List<StoredMessage> contextWindow,
        Map<Long, String> profilesBySender,
        List<StoredMessage> extendedHistory
) {}
