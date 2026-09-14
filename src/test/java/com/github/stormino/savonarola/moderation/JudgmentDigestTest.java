package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JudgmentDigestTest {

    private final AdminNotifier notifier = mock(AdminNotifier.class);
    private final JudgmentDigest digest = new JudgmentDigest(notifier);

    private String published() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendToOwner(text.capture());
        return text.getValue();
    }

    @Test
    void summarisesTheHourInsteadOfAnnouncingEveryWindow() {
        digest.recordWindow(8, 0);
        digest.recordWindow(12, 1);
        digest.recordWindow(5, 0);

        digest.publish();

        assertThat(published())
                .contains("25 messaggi")
                .contains("3 finestre")
                .contains("8.3")
                .contains("1 violazione");
    }

    @Test
    void saysNothingAboutAnHourInWhichNothingHappened() {
        digest.publish();

        verify(notifier, never()).sendToOwner(anyString());
    }

    @Test
    void startsFromZeroEachHourRatherThanAccumulating() {
        digest.recordWindow(10, 0);
        digest.publish();
        digest.recordWindow(3, 0);
        digest.publish();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier, org.mockito.Mockito.times(2)).sendToOwner(text.capture());
        assertThat(text.getAllValues().get(1)).contains("3 messaggi").contains("1 finestre");
    }
}
