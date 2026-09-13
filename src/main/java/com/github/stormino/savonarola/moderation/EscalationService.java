package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Position on the ladder is derived from executed decisions within the decay window,
 * so a clean streak resets a user automatically — no separate counter to maintain.
 */
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final DecisionRepository decisions;
    private final SavonarolaProperties props;

    public Action nextAction(long userId) {
        int rung = currentRung(userId);
        List<Integer> ladder = props.escalation().ladderMinutes();
        if (rung >= ladder.size()) {
            return Action.adminReview(rung);
        }
        return Action.mute(ladder.get(rung), rung);
    }

    private int currentRung(long userId) {
        Instant since = Instant.now().minus(props.escalation().decayAfterDays(), ChronoUnit.DAYS);
        return decisions.findBySubjectUserIdAndStatusAndCreatedAtAfter(
                userId, DecisionStatus.EXECUTED, since).size();
    }
}
