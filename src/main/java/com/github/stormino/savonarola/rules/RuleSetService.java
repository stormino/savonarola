package com.github.stormino.savonarola.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleSetService {

    private final RuleRepository rules;
    private final RuleExampleRepository examples;

    public List<Rule> activeRules() {
        return rules.findByEnabledTrue();
    }

    public boolean anyRequiresHistory() {
        return activeRules().stream().anyMatch(Rule::isRequiresHistory);
    }

    public Map<String, List<RuleExample>> examplesByRule() {
        return examples.findAll().stream()
                .collect(Collectors.groupingBy(RuleExample::getRuleId));
    }

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
