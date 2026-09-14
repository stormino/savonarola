package com.github.stormino.savonarola.conversation;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.ModerationPipeline;
import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Fixture: an episode opens at 3 exchanges within 300s; re-deliberation every 5 messages. */
class ConversationTrackerTest {

    private static final long ALICE = 1L;
    private static final long BRUNO = 2L;
    private static final long CARLA = 3L;

    private ModerationPipeline pipeline;
    private ConversationTracker tracker;

    @BeforeEach
    void setUp() {
        pipeline = mock(ModerationPipeline.class);
        tracker = new ConversationTracker(TestProperties.with(OperatingMode.LOG_ONLY), pipeline);
    }

    private Message message(long senderId, Long replyToUserId) {
        User from = mock(User.class);
        when(from.getId()).thenReturn(senderId);
        Message msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(from);

        if (replyToUserId != null) {
            User target = mock(User.class);
            when(target.getId()).thenReturn(replyToUserId);
            Message replied = mock(Message.class);
            when(replied.getFrom()).thenReturn(target);
            when(msg.getReplyToMessage()).thenReturn(replied);
        }
        return msg;
    }

    private void exchange(int rounds) {
        for (int i = 0; i < rounds; i++) {
            tracker.observe(message(ALICE, BRUNO));
            tracker.observe(message(BRUNO, ALICE));
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Message>> deliberations() {
        ArgumentCaptor<List<Message>> batch = ArgumentCaptor.forClass(List.class);
        verify(pipeline, org.mockito.Mockito.atLeastOnce())
                .judge(batch.capture(), any());
        return batch.getAllValues();
    }

    @Test
    void ordinaryConversationNeverReachesAModel() {
        for (int i = 0; i < 20; i++) tracker.observe(message(ALICE, null));

        verifyNoInteractions(pipeline);
    }

    @Test
    void aPolitePairBelowTheThresholdCostsNothing() {
        tracker.observe(message(ALICE, BRUNO));
        tracker.observe(message(BRUNO, ALICE));

        verifyNoInteractions(pipeline);
    }

    @Test
    void aBackAndForthOpensAnEpisodeAndIsLookedAt() {
        exchange(2);

        verify(pipeline, times(1)).judge(any(), any());
        assertThat(deliberations().get(0)).isNotEmpty();
    }

    @Test
    void aLiveEpisodeIsNotRejudgedOnEveryMessage() {
        exchange(2);
        tracker.observe(message(ALICE, BRUNO));
        tracker.observe(message(BRUNO, ALICE));

        // Two further messages is not enough movement to be worth another call.
        verify(pipeline, times(1)).judge(any(), any());
    }

    @Test
    void anEpisodeThatKeepsRunningIsLookedAtAgain() {
        exchange(2);
        for (int i = 0; i < 5; i++) tracker.observe(message(ALICE, BRUNO));

        verify(pipeline, times(2)).judge(any(), any());
    }

    @Test
    void aThirdPersonJoiningTheArgumentIsPartOfTheSameEpisode() {
        exchange(2);
        tracker.observe(message(CARLA, ALICE));

        // Carla answered someone already in the episode, so this is not a new quarrel.
        verify(pipeline, times(1)).judge(any(), any());
    }

    @Test
    void anIsolatedMessageIsStillSweptSoItIsNotInvisible() {
        tracker.observe(message(ALICE, null));

        tracker.sweepUnjudged();

        assertThat(deliberations().get(0)).hasSize(1);
    }

    @Test
    void aReexaminedArgumentOnlyRulesOnWhatIsNew() {
        exchange(2);
        for (int i = 0; i < 5; i++) tracker.observe(message(ALICE, BRUNO));

        List<List<Message>> judged = deliberations();
        assertThat(judged).hasSize(2);
        // The second look rules only on the five new messages; the earlier ones travel
        // as context so the model sees the argument without re-sentencing it.
        assertThat(judged.get(1)).hasSize(5).doesNotContainAnyElementsOf(judged.get(0));
    }

    @Test
    void noMessageIsEverJudgedTwice() {
        exchange(2);
        for (int i = 0; i < 5; i++) tracker.observe(message(ALICE, BRUNO));
        tracker.observe(message(CARLA, null));
        tracker.sweepUnjudged();

        List<Message> everJudged = deliberations().stream().flatMap(List::stream).toList();
        // Judging the same message under two batches could sanction it twice.
        assertThat(everJudged).doesNotHaveDuplicates();
    }

    @Test
    void aLiveEpisodeKeepsItsMessagesAwayFromTheSweep() {
        exchange(2);
        tracker.observe(message(ALICE, BRUNO));

        tracker.sweepUnjudged();

        // Only the two messages that preceded the episode are left for the sweep.
        assertThat(deliberations().get(1)).hasSize(2);
    }

    @Test
    void aQuietPeriodCostsNothing() {
        tracker.sweepUnjudged();
        tracker.closeIdleEpisodes();

        verifyNoInteractions(pipeline);
    }
}
