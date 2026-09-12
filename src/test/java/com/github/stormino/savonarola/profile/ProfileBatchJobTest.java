package com.github.stormino.savonarola.profile;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileBatchJobTest {

    private static final long SENDER = 1L;
    private static final long TARGET = 2L;

    private StoredMessageRepository messages;
    private UserProfileRepository profiles;
    private KnownDynamicRepository dynamics;
    private PairSignalRepository pairSignals;
    private ProfileSummarizer summarizer;
    private AdminNotifier notifier;

    @BeforeEach
    void setUp() {
        messages = mock(StoredMessageRepository.class);
        profiles = mock(UserProfileRepository.class);
        dynamics = mock(KnownDynamicRepository.class);
        pairSignals = mock(PairSignalRepository.class);
        summarizer = mock(ProfileSummarizer.class);
        notifier = mock(AdminNotifier.class);

        when(messages.findActiveSenders(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        when(messages.findFrequentPairs(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        when(profiles.findById(anyLong())).thenReturn(Optional.empty());
        when(dynamics.findByUserIdAndWithUserId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(pairSignals.findBySenderIdAndTargetId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    private ProfileBatchJob job() {
        return job(TestProperties.with(OperatingMode.LOG_ONLY));
    }

    private ProfileBatchJob job(SavonarolaProperties props) {
        return new ProfileBatchJob(messages, profiles, dynamics, pairSignals,
                summarizer, props, notifier);
    }

    private void withActiveSender(long userId, String name) {
        when(messages.findActiveSenders(anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of(userId));
        when(messages.findByChatIdAndSenderIdAndSentAtAfterOrderBySentAtDesc(
                anyLong(), org.mockito.ArgumentMatchers.eq(userId), any(), any()))
                .thenReturn(List.of(new StoredMessage(TestProperties.MAIN_CHAT, 5L, userId,
                        name, "che partita", null, null, Instant.now())));
    }

    private void withFrequentPair(long senderId, long targetId) {
        StoredMessageRepository.PairCount pair = mock(StoredMessageRepository.PairCount.class);
        when(pair.getSenderId()).thenReturn(senderId);
        when(pair.getTargetId()).thenReturn(targetId);
        when(messages.findFrequentPairs(anyLong(), any(), anyLong(), any())).thenReturn(List.of(pair));
        when(messages.findInteractions(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(new StoredMessage(TestProperties.MAIN_CHAT, 6L, senderId,
                        "@tizio", "ancora tu", 5L, targetId, Instant.now())));
    }

    @Test
    void doesNothingAtAllWhenProfilingIsDisabled() {
        SavonarolaProperties props = TestProperties.with(OperatingMode.LOG_ONLY);
        SavonarolaProperties disabled = new SavonarolaProperties(
                props.operatingMode(), props.telegram(), props.decision(), props.patternDetection(),
                props.escalation(), props.messageStore(), props.llm(),
                new SavonarolaProperties.Profile(false, "0 0 4 * * *", 30, 20, 8, 25, 15, 60),
                props.health(), props.actionAnnouncement());

        job(disabled).run();

        verifyNoInteractions(messages, summarizer, profiles);
    }

    @Test
    void storesAToneSummaryForAnActiveUser() {
        withActiveSender(SENDER, "@tizio");
        when(summarizer.summarizeTone(any())).thenReturn(Optional.of("Habitually blunt."));

        job().run();

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(SENDER);
        assertThat(saved.getValue().getTypicalTone()).isEqualTo("Habitually blunt.");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("@tizio");
    }

    @Test
    void refreshesAnExistingProfileInsteadOfDuplicatingIt() {
        withActiveSender(SENDER, "@tizio");
        UserProfile existing = new UserProfile(SENDER, "@vecchio", "Old summary.");
        when(profiles.findById(SENDER)).thenReturn(Optional.of(existing));
        when(summarizer.summarizeTone(any())).thenReturn(Optional.of("New summary."));

        job().run();

        verify(profiles, never()).save(any());
        assertThat(existing.getTypicalTone()).isEqualTo("New summary.");
        assertThat(existing.getDisplayName()).isEqualTo("@tizio");
    }

    @Test
    void writesNoProfileWhenTheModelGivesNothingBack() {
        withActiveSender(SENDER, "@tizio");
        when(summarizer.summarizeTone(any())).thenReturn(Optional.empty());

        job().run();

        verify(profiles, never()).save(any());
    }

    @Test
    void storesThePairSignalThatDrivesTheExtendedHistoryLookup() {
        withFrequentPair(SENDER, TARGET);
        when(summarizer.assessPair(anyString(), anyString(), any()))
                .thenReturn(Optional.of(new ProfileSummarizer.PairAssessment(5, null)));

        job().run();

        ArgumentCaptor<PairSignal> saved = ArgumentCaptor.forClass(PairSignal.class);
        verify(pairSignals).save(saved.capture());
        assertThat(saved.getValue().getSenderId()).isEqualTo(SENDER);
        assertThat(saved.getValue().getTargetId()).isEqualTo(TARGET);
        assertThat(saved.getValue().getNegativeInteractionCount()).isEqualTo(5);
    }

    @Test
    void recordsAnInferredDynamicWhenThereIsAPatternWorthKeeping() {
        withFrequentPair(SENDER, TARGET);
        when(summarizer.assessPair(anyString(), anyString(), any())).thenReturn(
                Optional.of(new ProfileSummarizer.PairAssessment(5, "One-sided needling.")));

        job().run();

        ArgumentCaptor<KnownDynamic> saved = ArgumentCaptor.forClass(KnownDynamic.class);
        verify(dynamics).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo(DynamicSource.BOT_INFERRED);
        assertThat(saved.getValue().getPattern()).isEqualTo("One-sided needling.");
    }

    @Test
    void leavesAnAdminAnnotatedDynamicAlone() {
        withFrequentPair(SENDER, TARGET);
        KnownDynamic annotated = new KnownDynamic(SENDER, TARGET,
                "Long-standing joking rivalry.", DynamicSource.ADMIN_ANNOTATED);
        when(dynamics.findByUserIdAndWithUserId(SENDER, TARGET)).thenReturn(Optional.of(annotated));
        when(summarizer.assessPair(anyString(), anyString(), any())).thenReturn(
                Optional.of(new ProfileSummarizer.PairAssessment(5, "One-sided needling.")));

        job().run();

        assertThat(annotated.getPattern()).isEqualTo("Long-standing joking rivalry.");
        assertThat(annotated.getSource()).isEqualTo(DynamicSource.ADMIN_ANNOTATED);
    }

    @Test
    void capsTheRunSoItCannotExhaustTheSharedCallBudget() {
        job().run();

        ArgumentCaptor<Pageable> users = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findActiveSenders(anyLong(), any(), anyLong(), users.capture());
        assertThat(users.getValue().getPageSize()).isEqualTo(25);

        ArgumentCaptor<Pageable> pairs = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findFrequentPairs(anyLong(), any(), anyLong(), pairs.capture());
        assertThat(pairs.getValue().getPageSize()).isEqualTo(15);
    }

    @Test
    void tellsAdminsWhenTheWholeRunProducedNothing() {
        withActiveSender(SENDER, "@tizio");
        when(summarizer.summarizeTone(any())).thenReturn(Optional.empty());

        job().run();

        verify(notifier).sendSystem(anyString());
    }

    @Test
    void staysQuietOnAQuietGroup() {
        job().run();

        verifyNoInteractions(notifier);
    }
}
