package com.github.stormino.savonarola.profile;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.llm.LlmCallType;
import com.github.stormino.savonarola.llm.LlmClient;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.store.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileSummarizerTest {

    private LlmClient client;
    private ProfileSummarizer summarizer;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        summarizer = new ProfileSummarizer(client, TestProperties.with(OperatingMode.LOG_ONLY));
    }

    private static List<StoredMessage> sample() {
        return List.of(new StoredMessage(1L, 2L, 3L, "@tizio", "che partita assurda",
                null, null, Instant.now()));
    }

    private void responds(String payload) {
        when(client.complete(anyString(), any(), anyString(), anyString())).thenReturn(payload);
    }

    @Test
    void spendsTheCheapModelNotTheJudgmentModel() {
        responds("{\"typicalTone\": \"Blunt.\"}");

        summarizer.summarizeTone(sample());

        verify(client).complete(eq("profile"), eq(LlmCallType.PROFILE), anyString(), anyString());
    }

    @Test
    void extractsTheToneSummary() {
        responds("{\"typicalTone\": \"Habitually sardonic, rarely warm.\"}");

        assertThat(summarizer.summarizeTone(sample()))
                .contains("Habitually sardonic, rarely warm.");
    }

    @Test
    void treatsABlankToneAsNoProfileRatherThanAnEmptyOne() {
        responds("{\"typicalTone\": \"   \"}");

        assertThat(summarizer.summarizeTone(sample())).isEmpty();
    }

    @Test
    void extractsThePairAssessmentIncludingTheNote() {
        responds("{\"negativeInteractions\": 4, \"note\": \"Persistent one-sided needling.\"}");

        var assessment = summarizer.assessPair("@tizio", "@caio", sample()).orElseThrow();

        assertThat(assessment.negativeInteractions()).isEqualTo(4);
        assertThat(assessment.note()).isEqualTo("Persistent one-sided needling.");
    }

    @Test
    void acceptsAPairAssessmentWithNoPatternWorthRemembering() {
        responds("{\"negativeInteractions\": 0, \"note\": null}");

        var assessment = summarizer.assessPair("@tizio", "@caio", sample()).orElseThrow();

        assertThat(assessment.negativeInteractions()).isZero();
        assertThat(assessment.note()).isNull();
    }

    @Test
    void refusesToTurnANonsenseCountIntoASignal() {
        responds("{\"negativeInteractions\": -3, \"note\": null}");

        assertThat(summarizer.assessPair("@tizio", "@caio", sample()).orElseThrow()
                .negativeInteractions()).isZero();
    }

    @Test
    void degradesToNoProfileWhenTheModelIsUnavailable() {
        when(client.complete(anyString(), any(), anyString(), anyString()))
                .thenThrow(new LlmException("rate limited"));

        assertThat(summarizer.summarizeTone(sample())).isEmpty();
        assertThat(summarizer.assessPair("@tizio", "@caio", sample())).isEmpty();
    }

    @Test
    void degradesToNoProfileWhenTheModelAnswersInProse() {
        responds("I'd rather describe them in prose.");

        assertThat(summarizer.summarizeTone(sample())).isEmpty();
        assertThat(summarizer.assessPair("@tizio", "@caio", sample())).isEmpty();
    }

    @Test
    void namesBothPartiesSoTheModelKnowsWhoIsAimingAtWhom() {
        responds("{\"negativeInteractions\": 1, \"note\": null}");

        summarizer.assessPair("@tizio", "@caio", sample());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).complete(anyString(), any(), anyString(), prompt.capture());
        assertThat(prompt.getValue()).contains("FROM @tizio IN REPLY TO @caio");
    }
}
