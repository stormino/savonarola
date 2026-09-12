package com.github.stormino.savonarola.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PairSignalRepository extends JpaRepository<PairSignal, Long> {

    Optional<PairSignal> findBySenderIdAndTargetId(long senderId, long targetId);
}
