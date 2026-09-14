package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Fixture window: tick 15s, max wait 180s, max 25 messages, min 4. */
class JudgmentWindowTest {

    private ModerationPipeline pipeline;
    private JudgmentWindow window;

    @BeforeEach
    void setUp() {
        pipeline = mock(ModerationPipeline.class);
        window = new JudgmentWindow(TestProperties.with(OperatingMode.LOG_ONLY), pipeline);
    }

    private void offer(int count) {
        for (int i = 0; i < count; i++) window.offer(mock(Message.class));
    }

    /** Pretends the buffered messages have been waiting, without sleeping for three minutes. */
    private void pretendWaited(int seconds) {
        try {
            var field = JudgmentWindow.class.getDeclaredField("oldestQueuedAt");
            field.setAccessible(true);
            field.set(window, Instant.now().minus(seconds, ChronoUnit.SECONDS));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> judgedBatch() {
        ArgumentCaptor<List<Message>> batch = ArgumentCaptor.forClass(List.class);
        verify(pipeline).judge(batch.capture());
        return batch.getValue();
    }

    @Test
    void holdsALoneMessageBackToWaitForCompany() {
        offer(1);

        window.flushIfReady();

        // Judging one message costs the whole rulebook for a single line.
        verifyNoInteractions(pipeline);
    }

    @Test
    void judgesOnceEnoughHaveGathered() {
        offer(4);

        window.flushIfReady();

        assertThat(judgedBatch()).hasSize(4);
    }

    @Test
    void neverLeavesAQuietChatUnjudgedForever() {
        offer(1);
        pretendWaited(180);

        window.flushIfReady();

        assertThat(judgedBatch()).hasSize(1);
    }

    @Test
    void aBurstFlushesOnSizeWithoutWaiting() {
        offer(25);

        assertThat(judgedBatch()).hasSize(25);
    }

    @Test
    void theWaitIsMeasuredFromTheOldestMessageNotTheNewest() {
        offer(1);
        pretendWaited(180);
        offer(1);

        window.flushIfReady();

        assertThat(judgedBatch()).hasSize(2);
    }

    @Test
    void theClockRestartsAfterAFlush() {
        offer(25);
        offer(1);

        window.flushIfReady();

        // The second message is fresh, so it waits rather than riding the old deadline.
        verify(pipeline, times(1)).judge(any());
    }

    @Test
    void aQuietChatCostsNothing() {
        window.flushIfReady();
        window.flushIfReady();

        verify(pipeline, never()).judge(any());
    }
}
