package com.github.stormino.savonarola.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleExampleRepository extends JpaRepository<RuleExample, Long> {
    List<RuleExample> findByRuleId(String ruleId);

    boolean existsByRuleIdAndText(String ruleId, String text);
}
