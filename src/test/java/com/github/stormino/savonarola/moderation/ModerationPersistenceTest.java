package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.profile.DynamicSource;
import com.github.stormino.savonarola.profile.KnownDynamic;
import com.github.stormino.savonarola.profile.KnownDynamicRepository;
import com.github.stormino.savonarola.profile.PairSignal;
import com.github.stormino.savonarola.profile.PairSignalRepository;
import com.github.stormino.savonarola.profile.UserProfile;
import com.github.stormino.savonarola.profile.UserProfileRepository;
import com.github.stormino.savonarola.rulebook.ProposalStatus;
import com.github.stormino.savonarola.rulebook.RulebookProposal;
import com.github.stormino.savonarola.rulebook.RulebookProposalRepository;
import com.github.stormino.savonarola.rules.ExampleLabel;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.rules.RuleExampleRepository;
import com.github.stormino.savonarola.rules.RuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Covers the mappings and constraints that only a real database enforces. */
@DataJpaTest
class ModerationPersistenceTest {

    private static final long CHAT = -1001234567890L;
    private static final long OFFENDER = 7L;

    @Autowired private DecisionRepository decisions;
    @Autowired private RuleRepository rules;
    @Autowired private RuleExampleRepository examples;
    @Autowired private UserProfileRepository profiles;
    @Autowired private KnownDynamicRepository dynamics;
    @Autowired private PairSignalRepository pairSignals;
    @Autowired private RulebookProposalRepository proposals;

    private static Decision violation(long userId, Action suggested) {
        return Decision.pending(CHAT, 1L, userId,
                new Judgment(true, "direct_insult", 0.91, "Insulto diretto."), suggested);
    }

    @Test
    void roundTripsADecisionWithItsAssignedUuidAndEnums() {
        Decision saved = decisions.save(violation(OFFENDER, Action.mute(30, 1)));

        Decision loaded = decisions.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(DecisionStatus.PENDING);
        assertThat(loaded.getRuleId()).isEqualTo("direct_insult");
        assertThat(loaded.getConfidence()).isEqualTo(0.91);
        assertThat(loaded.suggestedAction().type()).isEqualTo(ActionType.MUTE);
        assertThat(loaded.suggestedAction().durationMinutes()).isEqualTo(30);
    }

    @Test
    void countsOnlyExecutedDecisionsInsideTheWindowForTheLadder() {
        Decision executed = decisions.save(violation(OFFENDER, Action.mute(5, 0)));
        executed.markExecuted(Action.mute(5, 0), 42L);
        decisions.save(executed);

        decisions.save(violation(OFFENDER, Action.mute(5, 0)));               // pending
        Decision dismissed = decisions.save(violation(OFFENDER, Action.mute(5, 0)));
        dismissed.markDismissed(42L);
        decisions.save(dismissed);
        decisions.save(Decision.logged(CHAT, 2L, OFFENDER,
                new Judgment(true, "direct_insult", 0.9, "dry run"), Action.mute(5, 0)));
        decisions.save(violation(999L, Action.mute(5, 0)));                   // someone else

        assertThat(decisions.findBySubjectUserIdAndStatusAndCreatedAtAfter(
                OFFENDER, DecisionStatus.EXECUTED, Instant.now().minus(30, ChronoUnit.DAYS)))
                .hasSize(1);
    }

    @Test
    void storesAReasoningLongerThanATelegramMessage() {
        String reasoning = "x".repeat(2000);
        Decision saved = decisions.save(Decision.pending(CHAT, 1L, OFFENDER,
                new Judgment(true, "direct_insult", 0.9, reasoning), Action.mute(5, 0)));

        assertThat(decisions.findById(saved.getId()).orElseThrow().getReasoning()).hasSize(2000);
    }

    @Test
    void findsRulesByEnabledAndKeepsALongDefinition() {
        rules.save(new Rule("direct_insult", Severity.HIGH, false, "d".repeat(4096), true));
        rules.save(new Rule("no_politics", Severity.LOW, false, "Old.", false));

        assertThat(rules.findByEnabledTrue()).extracting(Rule::getId)
                .containsExactly("direct_insult");
        assertThat(rules.findById("direct_insult").orElseThrow().getDefinition()).hasSize(4096);
    }

    @Test
    void detectsAnExampleAlreadyStoredForThatRule() {
        examples.save(new RuleExample("direct_insult", "sei un buffone",
                ExampleLabel.POSITIVE, 42L, CHAT, 1L));

        assertThat(examples.existsByRuleIdAndText("direct_insult", "sei un buffone")).isTrue();
        assertThat(examples.existsByRuleIdAndText("direct_insult", "altro testo")).isFalse();
        assertThat(examples.existsByRuleIdAndText("mockery_of_opinions", "sei un buffone")).isFalse();
        assertThat(examples.findByRuleId("direct_insult")).hasSize(1);
    }

    @Test
    void keepsOneDynamicPerOrderedPair() {
        dynamics.saveAndFlush(new KnownDynamic(1L, 2L, "rivalità", DynamicSource.ADMIN_ANNOTATED));
        dynamics.saveAndFlush(new KnownDynamic(2L, 1L, "rivalità", DynamicSource.ADMIN_ANNOTATED));

        assertThat(dynamics.findByUserIdAndWithUserId(1L, 2L)).isPresent();
        assertThat(dynamics.findByUserId(1L)).hasSize(1);

        assertThatThrownBy(() -> dynamics.saveAndFlush(
                new KnownDynamic(1L, 2L, "duplicato", DynamicSource.BOT_INFERRED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsPairSignalsDirectionalAndUnique() {
        pairSignals.saveAndFlush(new PairSignal(1L, 2L, 5, "one-sided"));
        pairSignals.saveAndFlush(new PairSignal(2L, 1L, 0, null));

        assertThat(pairSignals.findBySenderIdAndTargetId(1L, 2L).orElseThrow()
                .getNegativeInteractionCount()).isEqualTo(5);
        assertThat(pairSignals.findBySenderIdAndTargetId(2L, 1L).orElseThrow()
                .getNegativeInteractionCount()).isZero();

        assertThatThrownBy(() -> pairSignals.saveAndFlush(new PairSignal(1L, 2L, 9, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesAProfileKeyedByUserIdWithNoToneYet() {
        profiles.save(new UserProfile(1L, "@alice", null));

        assertThat(profiles.findById(1L)).get().satisfies(p -> {
            assertThat(p.getDisplayName()).isEqualTo("@alice");
            assertThat(p.getTypicalTone()).isNull();
            assertThat(p.getLastUpdated()).isNotNull();
        });
    }

    @Test
    void storesACompiledRulebookFarLargerThanAColumnDefault() {
        String json = "[" + "{\"id\":\"r\"},".repeat(2000) + "{\"id\":\"last\"}]";
        RulebookProposal saved = proposals.save(new RulebookProposal(json, 42L));

        assertThat(proposals.findById(saved.getId()).orElseThrow().getProposalJson())
                .hasSameSizeAs(json);
    }

    @Test
    void listsPendingProposalsNewestFirst() {
        proposals.save(new RulebookProposal("[]", 42L));
        RulebookProposal resolved = proposals.save(new RulebookProposal("[]", 42L));
        resolved.resolve(ProposalStatus.APPROVED, 42L);
        proposals.save(resolved);

        assertThat(proposals.findByStatusOrderByCreatedAtDesc(ProposalStatus.PENDING)).hasSize(1);
        assertThat(proposals.findByStatusOrderByCreatedAtDesc(ProposalStatus.APPROVED)).hasSize(1);
    }

}
