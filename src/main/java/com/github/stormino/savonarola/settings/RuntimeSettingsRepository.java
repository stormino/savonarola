package com.github.stormino.savonarola.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeSettingsRepository extends JpaRepository<RuntimeSettings, Long> {
}
