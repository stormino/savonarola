package com.github.stormino.savonarola.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, String> {
    List<Rule> findByEnabledTrue();
}
