package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.group.GroupKnowledgeService;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.llm.LlmJudge;
import com.github.stormino.savonarola.llm.Violation;
import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleSetService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationPipelineTest {

    private static final long CHAT = TestProperties.MAIN_CHAT;
    private static final long ALICE = 1L;
    private static final long BRUNO = 2L;

    private RuleSetService ruleSet;
    private GroupKnowledgeService groupKnowledge;
    private MessageStoreService messageStore;
    private ProfileProvider profiles;
    private LlmJudge judge;
    private EscalationService escalation;
    private DecisionRouter router;
    private JudgmentDigest digest;
    private ModerationPipeline pipeline;

    @BeforeEach
    void setUp() {
        ruleSet = mock(RuleSetService.class);
        groupKnowledge = mock(GroupKnowledgeService.class);
        messageStore = mock(MessageStoreService.class);
        profiles = mock(ProfileProvider.class);
        judge = mock(LlmJudge.class);
        escalation = mock(EscalationService.class);
        router = mock(DecisionRouter.class);
        digest = mock(JudgmentDigest.class);

        pipeline = new ModerationPipeline(TestProperties.with(OperatingMode.LOG_ONLY),
                ruleSet, groupKnowledge, messageStore, profiles, judge, escalation, router,
                new PipelineMetrics(), digest);

        when(ruleSet.activeRules()).thenReturn(List.of(
                new Rule("direct_insult", Severity.HIGH, false, "d", true)));
        when(ruleSet.examplesByRule()).thenReturn(Map.of());
        when(messageStore.contextWindow(anyLong())).thenReturn(List.of());
        when(escalation.nextAction(anyLong())).thenReturn(Action.mute(5, 0));
        when(profiles.profileFor(anyLong())).thenReturn(null);
        when(groupKnowledge.roster(anyLong())).thenReturn(List.of("@u1", "@u2"));
        when(groupKnowledge.dossier(anyLong())).thenReturn(Optional.empty());
    }

    private Message message(long messageId, long senderId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(senderId);
        Message msg = mock(Message.class);
        when(msg.getChatId()).thenReturn(CHAT);
        when(msg.getMessageId()).thenReturn((int) messageId);
        when(msg.getFrom()).thenReturn(user);
        when(messageStore.find(CHAT, messageId)).thenReturn(Optional.of(
                new StoredMessage(CHAT, messageId, senderId, "@u" + senderId,
                        "testo", null, null, Instant.now())));
        return msg;
    }

    private static Violation violation(long messageId, double confidence) {
        return new Violation(messageId, "direct_insult", confidence, "motivo");
    }

    @Test
    void judgesTheWholeWindowInASingleCall() {
        when(judge.judge(any())).thenReturn(List.of());

        pipeline.judge(List.of(message(10, ALICE), message(11, BRUNO), message(12, ALICE)));

        verify(judge, times(1)).judge(any());
    }

    @Test
    void aCleanWindowIsCountedNotAnnounced() {
        when(judge.judge(any())).thenReturn(List.of());

        pipeline.judge(List.of(message(10, ALICE), message(11, BRUNO)));

        // Counted for the hourly digest; nothing sent, because nothing happened.
        verify(digest).recordWindow(2, 0);
        verify(router, never()).route(any(), any(), any());
    }

    @Test
    void routesOneViolationPerOffendingMessage() {
        when(judge.judge(any())).thenReturn(List.of(violation(10, 0.9), violation(11, 0.8)));

        pipeline.judge(List.of(message(10, ALICE), message(11, BRUNO)));

        verify(router, times(2)).route(any(), any(), any());
    }

    @Test
    void sanctionsSomeoneOnceHoweverManyTimesTheyOffendInOneWindow() {
        when(judge.judge(any())).thenReturn(List.of(
                violation(10, 0.7), violation(11, 0.95), violation(12, 0.6)));

        pipeline.judge(List.of(message(10, ALICE), message(11, ALICE), message(12, ALICE)));

        ArgumentCaptor<Judgment> judgment = ArgumentCaptor.forClass(Judgment.class);
        verify(router, times(1)).route(any(), judgment.capture(), any());
        // The strongest of the three stands, not the first.
        assertThat(judgment.getValue().confidence()).isEqualTo(0.95);
    }

    @Test
    void climbsTheLadderOncePerPersonNotOncePerMessage() {
        when(judge.judge(any())).thenReturn(List.of(violation(10, 0.9), violation(11, 0.9)));

        pipeline.judge(List.of(message(10, ALICE), message(11, ALICE)));

        verify(escalation, times(1)).nextAction(ALICE);
    }

    @Test
    void stillSanctionsEachOffenderWhenSeveralMisbehaveAtOnce() {
        when(judge.judge(any())).thenReturn(List.of(violation(10, 0.9), violation(11, 0.9)));

        pipeline.judge(List.of(message(10, ALICE), message(11, BRUNO)));

        verify(escalation).nextAction(ALICE);
        verify(escalation).nextAction(BRUNO);
    }

    @Test
    void actsOnNothingWhenTheJudgmentFailed() {
        when(judge.judge(any())).thenThrow(new LlmException("all models down"));

        pipeline.judge(List.of(message(10, ALICE)));

        verify(router, never()).route(any(), any(), any());
    }

    @Test
    void judgesNothingWithoutAnActiveRulebook() {
        when(ruleSet.activeRules()).thenReturn(List.of());

        pipeline.judge(List.of(message(10, ALICE)));

        verify(judge, never()).judge(any());
    }

    @Test
    void skipsAMessageMissingFromTheStoreRatherThanFailingTheWindow() {
        Message present = message(10, ALICE);
        Message missing = mock(Message.class);
        when(missing.getChatId()).thenReturn(CHAT);
        when(missing.getMessageId()).thenReturn(99);
        when(messageStore.find(CHAT, 99L)).thenReturn(Optional.empty());
        when(judge.judge(any())).thenReturn(List.of());

        pipeline.judge(List.of(present, missing));

        verify(judge).judge(any());
    }

    @Test
    void anEmptyWindowNeverReachesTheModel() {
        pipeline.judge(List.of());

        verify(judge, never()).judge(any());
    }
}
