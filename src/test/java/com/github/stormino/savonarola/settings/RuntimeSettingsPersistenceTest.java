package com.github.stormino.savonarola.settings;

import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** In this package because the entity's constructor is protected, as JPA wants it. */
@DataJpaTest
class RuntimeSettingsPersistenceTest {

    @Autowired
    private RuntimeSettingsRepository repository;

    @Test
    void overridesLiveOnASingleRowAndLeaveTheOtherDialUnset() {
        RuntimeSettings row = repository.save(new RuntimeSettings());
        row.setOperatingMode(OperatingMode.LIVE_ACTION, 42L);
        repository.save(row);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(RuntimeSettings.SINGLETON_ID)).get().satisfies(saved -> {
            assertThat(saved.getOperatingMode()).isEqualTo(OperatingMode.LIVE_ACTION);
            assertThat(saved.getConfidenceThreshold()).isNull();
            assertThat(saved.getChangedBy()).isEqualTo(42L);
            assertThat(saved.getChangedAt()).isNotNull();
        });
    }

    @Test
    void savingTwiceStillLeavesOneRow() {
        repository.save(new RuntimeSettings());
        repository.save(new RuntimeSettings());

        assertThat(repository.findAll()).hasSize(1);
    }
}
