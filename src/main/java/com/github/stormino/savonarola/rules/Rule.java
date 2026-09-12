package com.github.stormino.savonarola.rules;

import com.github.stormino.savonarola.moderation.Severity;
import jakarta.persistence.*;

@Entity
@Table(name = "rules")
public class Rule {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    /** True when a single message is not enough — needs extended sender/target history. */
    private boolean requiresHistory;

    @Column(length = 4096)
    private String definition;

    private boolean enabled;

    protected Rule() {}

    public Rule(String id, Severity severity, boolean requiresHistory, String definition, boolean enabled) {
        this.id = id;
        this.severity = severity;
        this.requiresHistory = requiresHistory;
        this.definition = definition;
        this.enabled = enabled;
    }

    public void redefine(Severity severity, boolean requiresHistory, String definition) {
        this.severity = severity;
        this.requiresHistory = requiresHistory;
        this.definition = definition;
    }

    public String getId() { return id; }
    public Severity getSeverity() { return severity; }
    public boolean isRequiresHistory() { return requiresHistory; }
    public String getDefinition() { return definition; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
