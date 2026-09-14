package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.store.StoredMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelChainJudgeTest {

    private LlmClient client;
    private HealthMonitor health;
    private ModelChainJudge judge;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        health = mock(HealthMonitor.class);
        judge = new ModelChainJudge(client, new JudgmentPromptBuilder(),
                TestProperties.with(OperatingMode.LOG_ONLY), health);
    }

    private static StoredMessage message(long messageId, String text) {
        return new StoredMessage(1L, messageId, 3L, "@tizio", text, null, null, Instant.now());
    }

    /** A window of three: ids 10, 11 and 12 are judgeable, nothing else is. */
    private static JudgmentInput window() {
        return new JudgmentInput(List.of(), Map.of(),
                List.of(message(10, "ciao"), message(11, "sei un buffone"), message(12, "ok")),
                List.of(), Map.of(), List.of());
    }

    private void respondsWith(String model, String payload) {
        when(client.complete(eq(model), any(), anyString(), anyString())).thenReturn(payload);
    }

    @Test
    void returnsOnlyTheMessagesTheJudgeFlagged() {
        respondsWith("primary", """
                {"violations": [
                  {"messageId": 11, "ruleId": "direct_insult", "confidence": 0.9,
                   "reasoning": "Insulto diretto."}]}
                """);

        List<Violation> violations = judge.judge(window());

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.messageId()).isEqualTo(11);
            assertThat(v.ruleId()).isEqualTo("direct_insult");
            assertThat(v.confidence()).isEqualTo(0.9);
        });
        verify(health).recordSuccess();
    }

    @Test
    void anEmptyListIsAValidVerdictAndTheCommonOne() {
        respondsWith("primary", "{\"violations\": []}");

        assertThat(judge.judge(window())).isEmpty();
        verify(health).recordSuccess();
        verify(health, never()).recordFailure(anyString());
    }

    @Test
    void reportsEveryViolationInTheWindow() {
        respondsWith("primary", """
                {"violations": [
                  {"messageId": 10, "ruleId": "mockery_of_opinions", "confidence": 0.7, "reasoning": "a"},
                  {"messageId": 11, "ruleId": "direct_insult", "confidence": 0.95, "reasoning": "b"}]}
                """);

        assertThat(judge.judge(window())).hasSize(2)
                .extracting(Violation::messageId).containsExactly(10L, 11L);
    }

    @Test
    void discardsAnIdThatWasNotInTheWindow() {
        respondsWith("primary", """
                {"violations": [
                  {"messageId": 999, "ruleId": "direct_insult", "confidence": 0.99, "reasoning": "x"},
                  {"messageId": 11, "ruleId": "direct_insult", "confidence": 0.9, "reasoning": "y"}]}
                """);

        assertThat(judge.judge(window()))
                .extracting(Violation::messageId).containsExactly(11L);
    }

    @Test
    void discardsAViolationThatNamesNoRule() {
        respondsWith("primary", """
                {"violations": [{"messageId": 11, "ruleId": "", "confidence": 0.9, "reasoning": "x"}]}
                """);

        assertThat(judge.judge(window())).isEmpty();
    }

    @Test
    void toleratesJsonWrappedInAMarkdownFence() {
        respondsWith("primary", """
                ```json
                {"violations": []}
                ```""");

        assertThat(judge.judge(window())).isEmpty();
    }

    @Test
    void fallsBackToTheSecondaryModelAndCountsIt() {
        when(client.complete(eq("primary"), any(), anyString(), anyString()))
                .thenThrow(new LlmException("429 rate limited"));
        respondsWith("fallback", "{\"violations\": []}");

        assertThat(judge.judge(window())).isEmpty();
        verify(health).recordFallback();
        verify(health).recordSuccess();
    }

    @Test
    void reportsDegradationWhenEveryModelFails() {
        when(client.complete(anyString(), any(), anyString(), anyString()))
                .thenThrow(new LlmException("upstream down"));

        assertThatThrownBy(() -> judge.judge(window())).isInstanceOf(LlmException.class);

        verify(health).recordFailure(anyString());
        verify(health, never()).recordSuccess();
    }

    @Test
    void refusesAPayloadWithNoViolationsArray() {
        respondsWith("primary", "{\"verdict\": \"fine\"}");
        respondsWith("fallback", "not json at all");

        assertThatThrownBy(() -> judge.judge(window())).isInstanceOf(LlmException.class);
    }
}
