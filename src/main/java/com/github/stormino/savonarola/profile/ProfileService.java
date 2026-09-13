package com.github.stormino.savonarola.profile;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.ProfileProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileService implements ProfileProvider {

    private final UserProfileRepository profiles;
    private final KnownDynamicRepository dynamics;
    private final PairSignalRepository pairSignals;
    private final SavonarolaProperties props;

    @Override
    public String profileFor(long userId) {
        Optional<UserProfile> profile = profiles.findById(userId);
        List<KnownDynamic> known = dynamics.findByUserId(userId);

        if (profile.isEmpty() && known.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        profile.map(UserProfile::getTypicalTone)
                .ifPresent(tone -> sb.append("Typical tone: ").append(tone).append('\n'));

        if (!known.isEmpty()) {
            sb.append("Known dynamics:\n");
            for (KnownDynamic dynamic : known) {
                sb.append("- with ").append(nameOf(dynamic.getWithUserId()))
                  .append(": ").append(dynamic.getPattern())
                  .append(" (").append(dynamic.getSource()).append(")\n");
            }
        }
        return sb.toString().strip();
    }

    /**
     * A signal the batch job has not refreshed within the detection window has decayed:
     * counting it would keep a pair flagged long after they stopped interacting.
     */
    @Override
    public int negativeInteractionCount(long senderId, long targetId) {
        Instant floor = Instant.now().minus(props.patternDetection().windowDays(), ChronoUnit.DAYS);
        return pairSignals.findBySenderIdAndTargetId(senderId, targetId)
                .filter(signal -> signal.getUpdatedAt().isAfter(floor))
                .map(PairSignal::getNegativeInteractionCount)
                .orElse(0);
    }

    private String nameOf(long userId) {
        return profiles.findById(userId)
                .map(UserProfile::getDisplayName)
                .orElse("user " + userId);
    }
}
