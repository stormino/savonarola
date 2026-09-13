package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscalationServiceTest {

    private DecisionRepository decisions;
    private EscalationService escalation;

    @BeforeEach
    void setUp() {
        decisions = mock(DecisionRepository.class);
        escalation = new EscalationService(
                decisions, TestProperties.with(OperatingMode.ON_DEMAND_ACTION));
    }

    private void withPriorExecutions(int count) {
        when(decisions.findBySubjectUserIdAndStatusAndCreatedAtAfter(
                anyLong(), eq(DecisionStatus.EXECUTED), any(Instant.class)))
                .thenReturn(Collections.nCopies(count, mock(Decision.class)));
    }

    @Test
    void startsAtTheFirstRungForACleanUser() {
        withPriorExecutions(0);

        Action action = escalation.nextAction(1L);

        assertThat(action.type()).isEqualTo(ActionType.MUTE);
        assertThat(action.durationMinutes()).isEqualTo(5);
        assertThat(action.rung()).isZero();
    }

    @Test
    void climbsOneRungPerPriorExecution() {
        List<Integer> expected = List.of(5, 30, 120, 1440);
        for (int prior = 0; prior < expected.size(); prior++) {
            withPriorExecutions(prior);

            assertThat(escalation.nextAction(1L).durationMinutes())
                    .as("rung %d", prior)
                    .isEqualTo(expected.get(prior));
        }
    }

    @Test
    void handsOverToAHumanPastTheTopOfTheLadder() {
        withPriorExecutions(4);

        Action action = escalation.nextAction(1L);

        assertThat(action.type()).isEqualTo(ActionType.ADMIN_REVIEW);
        assertThat(action.durationMinutes()).isZero();
    }

    @Test
    void staysAtAdminReviewBeyondTheLadder() {
        withPriorExecutions(9);

        assertThat(escalation.nextAction(1L).type()).isEqualTo(ActionType.ADMIN_REVIEW);
    }
}
