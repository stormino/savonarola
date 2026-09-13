package com.github.stormino.savonarola.rules;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rule_examples")
public class RuleExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ruleId;

    @Column(length = 4096)
    private String text;

    @Enumerated(EnumType.STRING)
    private ExampleLabel label;

    private long addedBy;
    private Instant addedAt;
    private Long sourceChatId;
    private Long sourceMessageId;

    protected RuleExample() {}

    public RuleExample(String ruleId, String text, ExampleLabel label,
                       long addedBy, Long sourceChatId, Long sourceMessageId) {
        this.ruleId = ruleId;
        this.text = text;
        this.label = label;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
        this.sourceChatId = sourceChatId;
        this.sourceMessageId = sourceMessageId;
    }

    public String getRuleId() { return ruleId; }
    public String getText() { return text; }
    public ExampleLabel getLabel() { return label; }
    public Instant getAddedAt() { return addedAt; }
}
