package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DecisionRouterTest {

    private static final long SENDER = 777L;

    private DecisionRepository decisions;
    private AdminNotifier notifier;
    private ActionExecutor executor;
    private Message message;

    @BeforeEach
    void setUp() {
        decisions = mock(DecisionRepository.class);
        notifier = mock(AdminNotifier.class);
        executor = mock(ActionExecutor.class);

        User sender = mock(User.class);
        when(sender.getId()).thenReturn(SENDER);
        when(sender.getUserName()).thenReturn("colpevole");

        message = mock(Message.class);
        when(message.getChatId()).thenReturn(TestProperties.MAIN_CHAT);
        when(message.getMessageId()).thenReturn(99);
        when(message.getFrom()).thenReturn(sender);
        when(message.getText()).thenReturn("sei un buffone");
    }

    private DecisionRouter router(OperatingMode mode) {
        return new DecisionRouter(TestProperties.with(mode), decisions, notifier, executor);
    }

    private static Judgment violation(double confidence) {
        return new Judgment(true, "direct_insult", confidence, "Insulto diretto al destinatario.");
    }

    @Test
    void logOnlyRecordsTheVerdictAndNeverActs() throws Exception {
        router(OperatingMode.LOG_ONLY).route(message, violation(0.9), Action.mute(5, 0));

        ArgumentCaptor<Decision> saved = ArgumentCaptor.forClass(Decision.class);
        verify(decisions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DecisionStatus.LOGGED);
        assertThat(saved.getValue().getRuleId()).isEqualTo("direct_insult");

        verifyNoInteractions(executor);
        verify(notifier).send(anyString());
    }

    @Test
    void logOnlyReportsCleanMessagesWithoutRecordingADecision() {
        router(OperatingMode.LOG_ONLY)
                .route(message, Judgment.noViolation("Critica dura ma legittima."), null);

        verify(decisions, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notifier).send(anyString());
    }

    @Test
    void staysSilentOnCleanMessagesOnceOutOfDryRun() {
        router(OperatingMode.ON_DEMAND_ACTION)
                .route(message, Judgment.noViolation("Nessun problema."), null);

        verifyNoInteractions(notifier, decisions, executor);
    }

    @Test
    void flagsWithoutProposingWhenBelowTheConfidenceThreshold() {
        router(OperatingMode.ON_DEMAND_ACTION).route(message, violation(0.4), Action.mute(5, 0));

        verify(decisions, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(executor);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(text.capture());
        assertThat(text.getValue()).contains("bassa confidenza");
    }

    @Test
    void onDemandParksThePenaltyBehindAnExecuteCommand() throws Exception {
        router(OperatingMode.ON_DEMAND_ACTION).route(message, violation(0.9), Action.mute(30, 1));

        ArgumentCaptor<Decision> saved = ArgumentCaptor.forClass(Decision.class);
        verify(decisions).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DecisionStatus.PENDING);

        verifyNoInteractions(executor);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(text.capture());
        assertThat(text.getValue()).contains("/execute " + saved.getValue().getId());
    }

    @Test
    void liveActionMutesImmediatelyAndMarksTheDecisionExecuted() throws Exception {
        router(OperatingMode.LIVE_ACTION).route(message, violation(0.9), Action.mute(30, 1));

        verify(executor).mute(TestProperties.MAIN_CHAT, SENDER, "@colpevole", 99, 30);

        ArgumentCaptor<Decision> saved = ArgumentCaptor.forClass(Decision.class);
        verify(decisions, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DecisionStatus.EXECUTED);
    }

    @Test
    void topOfTheLadderIsHandedToAHumanEvenInLiveAction() throws Exception {
        router(OperatingMode.LIVE_ACTION).route(message, violation(0.95), Action.adminReview(4));

        verify(executor, never()).mute(anyLong(), anyLong(), anyString(), anyLong(), anyInt());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(text.capture());
        assertThat(text.getValue()).contains("decisione umana");
    }

    @Test
    void escapesModelAndMemberTextSoTheNotificationSurvivesHtmlParsing() {
        when(message.getText()).thenReturn("guarda <b>questo</b> & piangi");
        Judgment judgment = new Judgment(true, "direct_insult", 0.9, "Contiene <tag> sospetti.");

        router(OperatingMode.LOG_ONLY).route(message, judgment, Action.mute(5, 0));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(text.capture());
        assertThat(text.getValue())
                .contains("guarda &lt;b&gt;questo&lt;/b&gt; &amp; piangi")
                .contains("Contiene &lt;tag&gt; sospetti.");
    }
}
