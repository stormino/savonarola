package com.github.stormino.savonarola.rules;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RuleSetService {

    private static final Comparator<RuleExample> NEWEST_FIRST =
            Comparator.comparing(RuleExample::getAddedAt).reversed();

    private final RuleRepository rules;
    private final RuleExampleRepository examples;
    private final SavonarolaProperties props;

    public List<Rule> activeRules() {
        return rules.findByEnabledTrue();
    }

    public boolean anyRequiresHistory() {
        return activeRules().stream().anyMatch(Rule::isRequiresHistory);
    }

    /** What the judge sees — capped, unlike {@link #examplesFor}. */
    public Map<String, List<RuleExample>> examplesByRule() {
        return examples.findAll().stream()
                .collect(Collectors.groupingBy(RuleExample::getRuleId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> capped(entry.getValue())));
    }

    /**
     * Keeps both labels represented rather than simply the newest. The negatives are what
     * stop the judge flagging criticism the rulebook protects, so a recency-only cap would
     * quietly bias it toward flagging as soon as admins trained a run of violations.
     * Each label contributes its newest, and one fills the other's unused share.
     */
    private List<RuleExample> capped(List<RuleExample> all) {
        int cap = props.ruleSet().maxExamplesPerRule();
        if (cap <= 0 || all.size() <= cap) return all;

        Map<ExampleLabel, List<RuleExample>> byLabel = all.stream()
                .sorted(NEWEST_FIRST)
                .collect(Collectors.groupingBy(RuleExample::getLabel));

        List<RuleExample> positive = byLabel.getOrDefault(ExampleLabel.POSITIVE, List.of());
        List<RuleExample> negative = byLabel.getOrDefault(ExampleLabel.NEGATIVE, List.of());

        int fromPositive = Math.min(positive.size(), cap - Math.min(negative.size(), cap / 2));
        int fromNegative = Math.min(negative.size(), cap - fromPositive);

        return Stream.concat(positive.stream().limit(fromPositive),
                             negative.stream().limit(fromNegative))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** The full set for a rule, for admin-facing use. */
    public List<RuleExample> examplesFor(String ruleId) {
        return examples.findByRuleId(ruleId);
    }

    public void addExample(RuleExample example) {
        examples.save(example);
    }

    public boolean exists(String ruleId) {
        return rules.existsById(ruleId);
    }
}
