package com.github.stormino.savonarola.rulebook;

import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.ExampleLabel;

import java.util.List;

/** One rule as the model read it out of the rulebook, before any admin has agreed. */
public record ProposedRule(
        String id,
        Severity severity,
        boolean requiresHistory,
        String definition,
        List<ProposedExample> examples
) {
    public record ProposedExample(String text, ExampleLabel label) {}
}
