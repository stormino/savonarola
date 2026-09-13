package com.github.stormino.savonarola.profile;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * SPEC 7.1 — the periodic half of profiling; the live half is the context window.
 *
 * Every user costs one LLM call and every pair another, against a free-tier budget of
 * roughly 200 calls a day shared with live judging (SPEC 14). So the run is capped and
 * works busiest-first: partial profiles are fine, a starved judge is not.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileBatchJob {

    private final StoredMessageRepository messages;
    private final UserProfileRepository profiles;
    private final KnownDynamicRepository dynamics;
    private final PairSignalRepository pairSignals;
    private final ProfileSummarizer summarizer;
    private final SavonarolaProperties props;
    private final AdminNotifier notifier;

    @Scheduled(cron = "${savonarola.profile.cron}")
    @Transactional
    public void run() {
        var config = props.profile();
        if (!config.enabled()) return;

        long chatId = props.telegram().mainChatId();
        Instant since = Instant.now().minus(config.activeWindowDays(), ChronoUnit.DAYS);

        int attempted = 0;
        int succeeded = 0;

        for (Long userId : messages.findActiveSenders(chatId, since, config.minMessagesForTone(),
                PageRequest.of(0, config.maxUsersPerRun()))) {
            attempted++;
            if (profileUser(chatId, userId, since, config.maxMessagesPerSummary())) succeeded++;
        }

        for (var pair : messages.findFrequentPairs(chatId, since, config.minInteractionsForPair(),
                PageRequest.of(0, config.maxPairsPerRun()))) {
            attempted++;
            if (assessPair(chatId, pair, since, config.maxMessagesPerSummary())) succeeded++;
        }

        log.info("Profile batch: {}/{} summaries succeeded", succeeded, attempted);

        // Silence here means the judge keeps running on stale priors without anyone noticing.
        if (attempted > 0 && succeeded == 0) {
            notifier.sendSystem("Profile batch produced nothing: "
                    + attempted + " attempts, all failed. Judgments run without tone priors.");
        }
    }

    private boolean profileUser(long chatId, long userId, Instant since, int sampleSize) {
        List<StoredMessage> sample = messages.findByChatIdAndSenderIdAndSentAtAfterOrderBySentAtDesc(
                chatId, userId, since, PageRequest.of(0, sampleSize));
        if (sample.isEmpty()) return false;

        Optional<String> tone = summarizer.summarizeTone(sample);
        if (tone.isEmpty()) return false;

        String displayName = sample.get(0).getSenderName();
        profiles.findById(userId)
                .ifPresentOrElse(
                        existing -> existing.refresh(tone.get(), displayName),
                        () -> profiles.save(new UserProfile(userId, displayName, tone.get())));
        return true;
    }

    private boolean assessPair(long chatId, StoredMessageRepository.PairCount pair,
                               Instant since, int sampleSize) {
        List<StoredMessage> exchange = messages.findInteractions(
                chatId, pair.getSenderId(), pair.getTargetId(), since);
        if (exchange.isEmpty()) return false;
        if (exchange.size() > sampleSize) exchange = exchange.subList(0, sampleSize);

        Optional<ProfileSummarizer.PairAssessment> assessment = summarizer.assessPair(
                displayNameOf(pair.getSenderId()), displayNameOf(pair.getTargetId()), exchange);
        if (assessment.isEmpty()) return false;

        ProfileSummarizer.PairAssessment result = assessment.get();
        pairSignals.findBySenderIdAndTargetId(pair.getSenderId(), pair.getTargetId())
                .ifPresentOrElse(
                        existing -> existing.refresh(result.negativeInteractions(), result.note()),
                        () -> pairSignals.save(new PairSignal(pair.getSenderId(), pair.getTargetId(),
                                result.negativeInteractions(), result.note())));

        if (result.note() != null && !result.note().isBlank()) {
            recordInferredDynamic(pair.getSenderId(), pair.getTargetId(), result.note());
        }
        return true;
    }

    private void recordInferredDynamic(long userId, long withUserId, String pattern) {
        Optional<KnownDynamic> existing = dynamics.findByUserIdAndWithUserId(userId, withUserId);
        if (existing.isPresent()) {
            if (existing.get().supersededBy(DynamicSource.BOT_INFERRED)) {
                existing.get().update(pattern, DynamicSource.BOT_INFERRED);
            }
            return;
        }
        dynamics.save(new KnownDynamic(userId, withUserId, pattern, DynamicSource.BOT_INFERRED));
    }

    private String displayNameOf(long userId) {
        return profiles.findById(userId)
                .map(UserProfile::getDisplayName)
                .orElse("user " + userId);
    }
}
