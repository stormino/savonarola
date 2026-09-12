package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.moderation.Judgment;
import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class OpenRouterJudgeTest {

    private LlmClient client;
    private HealthMonitor health;
    private OpenRouterJudge judge;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        health = mock(HealthMonitor.class);
        judge = new OpenRouterJudge(client, new JudgmentPromptBuilder(),
                TestProperties.with(OperatingMode.LOG_ONLY), health);
    }

    private static JudgmentInput input() {
        return new JudgmentInput(List.of(), Map.of(),
                new com.github.stormino.savonarola.store.StoredMessage(
                        1L, 2L, 3L, "tizio", "testo", null, null, java.time.Instant.now()),
                List.of(), null, null, List.of());
    }

    private void respondsWith(String model, String payload) {
        when(client.complete(eq(model), any(), anyString(), anyString())).thenReturn(payload);
    }

    @Test
    void parsesAStructuredVerdict() {
        respondsWith("primary", """
                {"violated": true, "ruleId": "direct_insult", "confidence": 0.82,
                 "reasoning": "Insulto rivolto a un partecipante."}
                """);

        Judgment judgment = judge.judge(input());

        assertThat(judgment.violated()).isTrue();
        assertThat(judgment.ruleId()).isEqualTo("direct_insult");
        assertThat(judgment.confidence()).isEqualTo(0.82);
        verify(health).recordSuccess();
    }

    @Test
    void toleratesJsonWrappedInAMarkdownFence() {
        respondsWith("primary", """
                ```json
                {"violated": false, "ruleId": null, "confidence": 0.1, "reasoning": "Solo tennis."}
                ```""");

        Judgment judgment = judge.judge(input());

        assertThat(judgment.violated()).isFalse();
        assertThat(judgment.ruleId()).isNull();
        assertThat(judgment.reasoning()).isEqualTo("Solo tennis.");
    }

    @Test
    void fallsBackToTheSecondaryModelWhenThePrimaryFails() {
        when(client.complete(eq("primary"), any(), anyString(), anyString()))
                .thenThrow(new LlmException("429 rate limited"));
        respondsWith("fallback", """
                {"violated": false, "ruleId": null, "confidence": 0.0, "reasoning": "Ok."}
                """);

        assertThat(judge.judge(input()).violated()).isFalse();
        verify(health).recordSuccess();
        verify(health, never()).recordFailure(anyString());
    }

    @Test
    void countsAFallbackSoDegradationIsVisibleBeforeAnythingBreaks() {
        when(client.complete(eq("primary"), any(), anyString(), anyString()))
                .thenThrow(new LlmException("429 rate limited"));
        respondsWith("fallback", """
                {"violated": false, "ruleId": null, "confidence": 0.0, "reasoning": "Ok."}
                """);

        judge.judge(input());

        verify(health).recordFallback();
    }

    @Test
    void countsNoFallbackWhenThePrimaryAnswers() {
        respondsWith("primary", """
                {"violated": false, "ruleId": null, "confidence": 0.0, "reasoning": "Ok."}
                """);

        judge.judge(input());

        verify(health, never()).recordFallback();
    }

    @Test
    void reportsDegradationWhenEveryModelFails() {
        when(client.complete(anyString(), any(), anyString(), anyString()))
                .thenThrow(new LlmException("upstream down"));

        assertThatThrownBy(() -> judge.judge(input())).isInstanceOf(LlmException.class);

        verify(health).recordFailure(anyString());
        verify(health, never()).recordSuccess();
    }

    @Test
    void rejectsAVerdictThatClaimsAViolationWithoutNamingARule() {
        respondsWith("primary", """
                {"violated": true, "ruleId": null, "confidence": 0.9, "reasoning": "Brutto."}
                """);
        respondsWith("fallback", "not json at all");

        assertThatThrownBy(() -> judge.judge(input())).isInstanceOf(LlmException.class);
        verify(health).recordFailure(anyString());
    }
}
