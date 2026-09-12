package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.store.StoredMessage;

import java.util.List;
import java.util.Map;

/** Everything the judge sees for a single verdict. */
public record JudgmentInput(
        List<Rule> activeRules,
        Map<String, List<RuleExample>> examplesByRule,
        StoredMessage targetMessage,
        List<StoredMessage> contextWindow,
        String senderProfile,
        String targetProfile,
        List<StoredMessage> extendedHistory
) {}
