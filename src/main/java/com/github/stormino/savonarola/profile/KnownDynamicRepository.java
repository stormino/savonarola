package com.github.stormino.savonarola.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnownDynamicRepository extends JpaRepository<KnownDynamic, Long> {

    List<KnownDynamic> findByUserId(long userId);

    Optional<KnownDynamic> findByUserIdAndWithUserId(long userId, long withUserId);
}
