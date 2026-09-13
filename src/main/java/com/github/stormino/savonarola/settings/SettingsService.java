package com.github.stormino.savonarola.settings;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The operating mode and the confidence gate are the two dials that decide how much power
 * the bot has, so admins can move them without a redeploy. Reads fall back to
 * application.yml, which stays the declared starting point.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final RuntimeSettingsRepository repository;
    private final SavonarolaProperties props;

    public OperatingMode operatingMode() {
        return current()
                .map(RuntimeSettings::getOperatingMode)
                .orElse(props.operatingMode());
    }

    public double confidenceThreshold() {
        return current()
                .map(RuntimeSettings::getConfidenceThreshold)
                .orElse(props.decision().confidenceThreshold());
    }

    @Transactional
    public void setOperatingMode(OperatingMode mode, long adminId) {
        mutable().setOperatingMode(mode, adminId);
    }

    @Transactional
    public void setConfidenceThreshold(double threshold, long adminId) {
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("La soglia deve stare fra 0 e 1.");
        }
        mutable().setConfidenceThreshold(threshold, adminId);
    }

    public Optional<RuntimeSettings> current() {
        return repository.findById(RuntimeSettings.SINGLETON_ID)
                .filter(settings -> settings.getOperatingMode() != null
                        || settings.getConfidenceThreshold() != null);
    }

    private RuntimeSettings mutable() {
        return repository.findById(RuntimeSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(new RuntimeSettings()));
    }
}
