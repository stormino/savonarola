package com.github.stormino.savonarola.profile;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private static final long SENDER = 1L;
    private static final long TARGET = 2L;

    private UserProfileRepository profiles;
    private KnownDynamicRepository dynamics;
    private PairSignalRepository pairSignals;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        profiles = mock(UserProfileRepository.class);
        dynamics = mock(KnownDynamicRepository.class);
        pairSignals = mock(PairSignalRepository.class);
        service = new ProfileService(profiles, dynamics, pairSignals,
                TestProperties.with(OperatingMode.LOG_ONLY));

        when(profiles.findById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
        when(dynamics.findByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
    }

    @Test
    void yieldsNoProfileTextForAnUnknownUser() {
        assertThat(service.profileFor(SENDER)).isNull();
    }

    @Test
    void describesToneAndDynamicsByName() {
        when(profiles.findById(SENDER)).thenReturn(Optional.of(
                new UserProfile(SENDER, "@tizio", "Habitually blunt and sardonic.")));
        when(profiles.findById(TARGET)).thenReturn(Optional.of(
                new UserProfile(TARGET, "@caio", "Warm and verbose.")));
        when(dynamics.findByUserId(SENDER)).thenReturn(List.of(new KnownDynamic(
                SENDER, TARGET, "Long-running joking rivalry.", DynamicSource.ADMIN_ANNOTATED)));

        String profile = service.profileFor(SENDER);

        assertThat(profile)
                .contains("Habitually blunt and sardonic.")
                .contains("with @caio: Long-running joking rivalry.")
                .contains("ADMIN_ANNOTATED");
    }

    @Test
    void describesDynamicsEvenWithoutAToneSummary() {
        when(dynamics.findByUserId(SENDER)).thenReturn(List.of(new KnownDynamic(
                SENDER, TARGET, "Persistent one-sided needling.", DynamicSource.BOT_INFERRED)));

        assertThat(service.profileFor(SENDER)).contains("Persistent one-sided needling.");
    }

    @Test
    void fallsBackToTheIdWhenTheCounterpartIsUnprofiled() {
        when(dynamics.findByUserId(SENDER)).thenReturn(List.of(new KnownDynamic(
                SENDER, 99L, "Some pattern.", DynamicSource.BOT_INFERRED)));

        assertThat(service.profileFor(SENDER)).contains("with user 99");
    }

    @Test
    void reportsTheNegativeCountForAFreshSignal() {
        when(pairSignals.findBySenderIdAndTargetId(SENDER, TARGET))
                .thenReturn(Optional.of(new PairSignal(SENDER, TARGET, 7, null)));

        assertThat(service.negativeInteractionCount(SENDER, TARGET)).isEqualTo(7);
    }

    @Test
    void discardsASignalTheBatchHasNotRefreshedInsideTheWindow() {
        PairSignal stale = mock(PairSignal.class);
        when(stale.getUpdatedAt()).thenReturn(Instant.now().minus(45, ChronoUnit.DAYS));
        when(pairSignals.findBySenderIdAndTargetId(SENDER, TARGET)).thenReturn(Optional.of(stale));

        assertThat(service.negativeInteractionCount(SENDER, TARGET)).isZero();
    }

    @Test
    void reportsZeroForAPairWithNoSignalAtAll() {
        when(pairSignals.findBySenderIdAndTargetId(SENDER, TARGET)).thenReturn(Optional.empty());

        assertThat(service.negativeInteractionCount(SENDER, TARGET)).isZero();
    }
}
