package com.github.stormino.savonarola.rulebook;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.llm.LlmCallType;
import com.github.stormino.savonarola.llm.LlmClient;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.ExampleLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulebookCompilerTest {

    private LlmClient client;
    private RulebookCompiler compiler;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        compiler = new RulebookCompiler(client, TestProperties.with(OperatingMode.LOG_ONLY));
    }

    private void responds(String model, String payload) {
        when(client.complete(eq(model), any(), anyString(), anyString())).thenReturn(payload);
    }

    @Test
    void readsRulesOutOfTheCompiledPayload() {
        responds("primary", """
                {"rules": [
                  {"id": "direct_insult", "severity": "HIGH", "requiresHistory": false,
                   "definition": "Insulting a participant as a person.",
                   "examples": [{"text": "sei un buffone", "label": "POSITIVE"},
                                {"text": "che partita oscena", "label": "NEGATIVE"}]},
                  {"id": "intimidation_pattern", "severity": "HIGH", "requiresHistory": true,
                   "definition": "Sustained pressure on one person.", "examples": []}
                ]}
                """);

        List<ProposedRule> rules = compiler.compile("testo del regolamento");

        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).id()).isEqualTo("direct_insult");
        assertThat(rules.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(rules.get(0).requiresHistory()).isFalse();
        assertThat(rules.get(0).examples()).hasSize(2);
        assertThat(rules.get(0).examples().get(1).label()).isEqualTo(ExampleLabel.NEGATIVE);
        assertThat(rules.get(1).requiresHistory()).isTrue();
    }

    @Test
    void usesTheJudgmentModelsBecauseAMistakeHerePoisonsEveryDecision() {
        responds("primary", """
                {"rules": [{"id": "r", "severity": "LOW", "requiresHistory": false,
                            "definition": "d", "examples": []}]}
                """);

        compiler.compile("testo");

        verify(client).complete(eq("primary"), eq(LlmCallType.RULEBOOK), anyString(), anyString());
    }

    @Test
    void fallsBackToTheSecondaryModel() {
        when(client.complete(eq("primary"), any(), anyString(), anyString()))
                .thenThrow(new LlmException("rate limited"));
        responds("fallback", """
                {"rules": [{"id": "r", "severity": "LOW", "requiresHistory": false,
                            "definition": "d", "examples": []}]}
                """);

        assertThat(compiler.compile("testo")).hasSize(1);
    }

    @Test
    void refusesARuleMissingAnIdOrADefinition() {
        responds("primary", """
                {"rules": [{"id": "", "severity": "LOW", "requiresHistory": false,
                            "definition": "d", "examples": []}]}
                """);
        responds("fallback", """
                {"rules": [{"id": "r", "severity": "LOW", "requiresHistory": false,
                            "definition": "  ", "examples": []}]}
                """);

        assertThatThrownBy(() -> compiler.compile("testo")).isInstanceOf(LlmException.class);
    }

    @Test
    void refusesAnEmptyRulebookRatherThanDisablingEverything() {
        responds("primary", "{\"rules\": []}");
        responds("fallback", "{\"rules\": []}");

        assertThatThrownBy(() -> compiler.compile("testo"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("No rules found");
    }

    @Test
    void skipsExamplesWithNoText() {
        responds("primary", """
                {"rules": [{"id": "r", "severity": "LOW", "requiresHistory": false,
                            "definition": "d",
                            "examples": [{"text": "  ", "label": "POSITIVE"},
                                         {"text": "vero esempio", "label": "POSITIVE"}]}]}
                """);

        assertThat(compiler.compile("testo").get(0).examples())
                .singleElement()
                .satisfies(e -> assertThat(e.text()).isEqualTo("vero esempio"));
    }
}
