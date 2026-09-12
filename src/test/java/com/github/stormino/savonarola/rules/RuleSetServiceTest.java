package com.github.stormino.savonarola.rules;

import com.github.stormino.savonarola.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleSetServiceTest {

    private static final String RULE = "direct_insult";

    private RuleExampleRepository examples;

    @BeforeEach
    void setUp() {
        examples = mock(RuleExampleRepository.class);
    }

    private RuleSetService service(int cap) {
        return new RuleSetService(mock(RuleRepository.class), examples,
                TestProperties.withRuleSet(cap));
    }

    /** Stored oldest first, so index 0 is the oldest and the newest carry the highest numbers. */
    private void stored(int positives, int negatives) {
        List<RuleExample> all = new ArrayList<>();
        Instant base = Instant.now().minus(100, ChronoUnit.DAYS);
        for (int i = 0; i < positives; i++) all.add(example("pos-" + i, ExampleLabel.POSITIVE));
        for (int i = 0; i < negatives; i++) all.add(example("neg-" + i, ExampleLabel.NEGATIVE));
        // addedAt is set in the constructor, so nudge them apart in insertion order
        for (int i = 0; i < all.size(); i++) {
            setAddedAt(all.get(i), base.plus(i, ChronoUnit.HOURS));
        }
        when(examples.findAll()).thenReturn(all);
    }

    private static RuleExample example(String text, ExampleLabel label) {
        return new RuleExample(RULE, text, label, 42L, -100L, 1L);
    }

    private static void setAddedAt(RuleExample example, Instant when) {
        try {
            var field = RuleExample.class.getDeclaredField("addedAt");
            field.setAccessible(true);
            field.set(example, when);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> sent(int cap) {
        return service(cap).examplesByRule().get(RULE).stream().map(RuleExample::getText).toList();
    }

    @Test
    void sendsEverythingWhenTheRulebookIsSmallerThanTheCap() {
        stored(2, 2);

        assertThat(sent(6)).hasSize(4);
    }

    @Test
    void splitsTheCapEvenlyWhenBothLabelsAreWellStocked() {
        stored(10, 10);

        List<String> sent = sent(8);

        assertThat(sent).hasSize(8);
        assertThat(sent.stream().filter(t -> t.startsWith("pos")).count()).isEqualTo(4);
        assertThat(sent.stream().filter(t -> t.startsWith("neg")).count()).isEqualTo(4);
    }

    @Test
    void letsOneLabelFillTheSharePositiveExamplesCannotUse() {
        stored(2, 10);

        List<String> sent = sent(8);

        assertThat(sent).hasSize(8);
        assertThat(sent.stream().filter(t -> t.startsWith("pos")).count()).isEqualTo(2);
        assertThat(sent.stream().filter(t -> t.startsWith("neg")).count()).isEqualTo(6);
    }

    @Test
    void keepsNegativesWhenTrainingHasBeenAllViolations() {
        stored(20, 1);

        List<String> sent = sent(6);

        assertThat(sent).hasSize(6);
        assertThat(sent).contains("neg-0");
    }

    @Test
    void copesWithOnlyOneLabelPresent() {
        stored(10, 0);
        assertThat(sent(6)).hasSize(6).allSatisfy(t -> assertThat(t).startsWith("pos"));

        stored(0, 10);
        assertThat(sent(6)).hasSize(6).allSatisfy(t -> assertThat(t).startsWith("neg"));
    }

    @Test
    void keepsTheNewestOfEachLabel() {
        stored(10, 10);

        List<String> sent = sent(4);

        assertThat(sent).containsExactlyInAnyOrder("pos-9", "pos-8", "neg-9", "neg-8");
    }

    @Test
    void treatsANonPositiveCapAsNoCapAtAll() {
        stored(10, 10);

        assertThat(sent(0)).hasSize(20);
    }

    @Test
    void boundsThePromptHoweverMuchTrainingAccumulates() {
        stored(200, 200);

        assertThat(sent(6)).hasSize(6);
    }
}
