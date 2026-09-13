package com.github.stormino.savonarola.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByStatus(DecisionStatus status);

    List<Decision> findByCreatedAtAfter(Instant since);

    List<Decision> findBySubjectUserIdAndStatusAndCreatedAtAfter(
            long subjectUserId, DecisionStatus status, Instant since);
}
