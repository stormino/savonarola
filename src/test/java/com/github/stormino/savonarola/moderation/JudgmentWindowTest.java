package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JudgmentWindowTest {

    private ModerationPipeline pipeline;
    private JudgmentWindow window;

    @BeforeEach
    void setUp() {
        pipeline = mock(ModerationPipeline.class);
        // Fixture caps the window at 25 messages.
        window = new JudgmentWindow(TestProperties.with(OperatingMode.LOG_ONLY), pipeline);
    }

    private void offer(int count) {
        for (int i = 0; i < count; i++) window.offer(mock(Message.class));
    }

    @SuppressWarnings("unchecked")
    private List<Message> judgedBatch() {
        ArgumentCaptor<List<Message>> batch = ArgumentCaptor.forClass(List.class);
        verify(pipeline).judge(batch.capture());
        return batch.getValue();
    }

    @Test
    void holdsMessagesBackUntilSomethingFlushesIt() {
        offer(5);

        verifyNoInteractions(pipeline);
    }

    @Test
    void theTimerJudgesWhateverHasAccumulated() {
        offer(5);

        window.flush();

        assertThat(judgedBatch()).hasSize(5);
    }

    @Test
    void aBurstFlushesOnSizeWithoutWaitingForTheTimer() {
        offer(25);

        assertThat(judgedBatch()).hasSize(25);
    }

    @Test
    void theSizeFlushLeavesTheBufferEmptyRatherThanRejudging() {
        offer(25);
        window.flush();

        verify(pipeline, times(1)).judge(any());
    }

    @Test
    void carriesOnBufferingAfterAFlush() {
        offer(25);
        offer(3);
        window.flush();

        ArgumentCaptor<List<Message>> batches = ArgumentCaptor.forClass(List.class);
        verify(pipeline, times(2)).judge(batches.capture());
        assertThat(batches.getAllValues().get(0)).hasSize(25);
        assertThat(batches.getAllValues().get(1)).hasSize(3);
    }

    @Test
    void aQuietChatCostsNothing() {
        window.flush();
        window.flush();

        verify(pipeline, never()).judge(any());
    }
}
