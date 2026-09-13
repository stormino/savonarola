package com.github.stormino.savonarola.settings;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private static final long ADMIN = 42L;

    private RuntimeSettingsRepository repository;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        repository = mock(RuntimeSettingsRepository.class);
        settings = new SettingsService(repository,
                TestProperties.with(OperatingMode.LOG_ONLY, 0.6));

        when(repository.findById(anyLong())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private RuntimeSettings stored() {
        RuntimeSettings row = new RuntimeSettings();
        when(repository.findById(anyLong())).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void fallsBackToTheConfiguredStartingPoint() {
        assertThat(settings.operatingMode()).isEqualTo(OperatingMode.LOG_ONLY);
        assertThat(settings.confidenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void anOverrideWins() {
        stored();

        settings.setOperatingMode(OperatingMode.LIVE_ACTION, ADMIN);
        settings.setConfidenceThreshold(0.85, ADMIN);

        assertThat(settings.operatingMode()).isEqualTo(OperatingMode.LIVE_ACTION);
        assertThat(settings.confidenceThreshold()).isEqualTo(0.85);
    }

    @Test
    void overridingOneDialLeavesTheOtherOnItsConfiguredValue() {
        stored();

        settings.setConfidenceThreshold(0.85, ADMIN);

        assertThat(settings.operatingMode()).isEqualTo(OperatingMode.LOG_ONLY);
        assertThat(settings.confidenceThreshold()).isEqualTo(0.85);
    }

    @Test
    void recordsWhoMovedTheDial() {
        RuntimeSettings row = stored();

        settings.setOperatingMode(OperatingMode.ON_DEMAND_ACTION, ADMIN);

        assertThat(row.getChangedBy()).isEqualTo(ADMIN);
        assertThat(row.getChangedAt()).isNotNull();
    }

    @Test
    void refusesAThresholdOutsideTheProbabilityRange() {
        stored();

        assertThatThrownBy(() -> settings.setConfidenceThreshold(1.5, ADMIN))
                .hasMessageContaining("fra 0 e 1");
        assertThatThrownBy(() -> settings.setConfidenceThreshold(-0.1, ADMIN))
                .hasMessageContaining("fra 0 e 1");
    }

    @Test
    void treatsAnUntouchedRowAsNoOverrideAtAll() {
        stored();

        assertThat(settings.current()).isEmpty();
        assertThat(settings.operatingMode()).isEqualTo(OperatingMode.LOG_ONLY);
    }
}
