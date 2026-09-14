package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.health.HealthMonitor;
import com.github.stormino.savonarola.health.LlmUsageTracker;
import com.github.stormino.savonarola.llm.LlmCallType;
import com.github.stormino.savonarola.moderation.Action;
import com.github.stormino.savonarola.moderation.Decision;
import com.github.stormino.savonarola.moderation.DecisionRepository;
import com.github.stormino.savonarola.moderation.Judgment;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.moderation.PipelineMetrics;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsCommandTest {

    private DecisionRepository decisions;
    private StoredMessageRepository messages;
    private PipelineMetrics metrics;
    private LlmUsageTracker usage;
    private HealthMonitor health;
    private AdminNotifier notifier;
    private StatsCommand command;
    private Message msg;

    @BeforeEach
    void setUp() {
        decisions = mock(DecisionRepository.class);
        messages = mock(StoredMessageRepository.class);
        metrics = new PipelineMetrics();
        usage = new LlmUsageTracker();
        health = mock(HealthMonitor.class);
        notifier = mock(AdminNotifier.class);

        SettingsService settings = mock(SettingsService.class);
        when(settings.operatingMode()).thenReturn(OperatingMode.ON_DEMAND_ACTION);
        when(settings.confidenceThreshold()).thenReturn(0.6);

        command = new StatsCommand(decisions, messages, metrics, usage, health, settings,
                TestProperties.with(OperatingMode.ON_DEMAND_ACTION), notifier);

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(42L);
        msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(admin);

        when(decisions.findByCreatedAtAfter(any())).thenReturn(List.of());
        when(messages.countByChatIdAndSentAtAfter(anyLong(), any())).thenReturn(0L);
        when(health.getLastSuccess()).thenReturn(Instant.now());
    }

    private static Decision decision(String ruleId, Action suggested) {
        return Decision.pending(TestProperties.MAIN_CHAT, 1L, 7L,
                new Judgment(true, ruleId, 0.9, "motivo"), suggested);
    }

    private String report() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(notifier).reply(any(), text.capture());
        return text.getValue();
    }

    @Test
    void breaksDecisionsDownByRule() {
        when(decisions.findByCreatedAtAfter(any())).thenReturn(List.of(
                decision("direct_insult", Action.mute(5, 0)),
                decision("direct_insult", Action.mute(5, 0)),
                decision("mockery_of_opinions", Action.mute(5, 0))));

        command.handle(msg, List.of("week"));

        assertThat(report())
                .contains("Decisioni: 3")
                .contains("direct_insult</code>: 2")
                .contains("mockery_of_opinions</code>: 1");
    }

    @Test
    void separatesConfirmedModifiedAndDiscardedDecisions() {
        Decision asProposed = decision("direct_insult", Action.mute(30, 1));
        asProposed.markExecuted(Action.mute(30, 1), 42L);
        Decision overridden = decision("direct_insult", Action.mute(30, 1));
        overridden.markExecuted(Action.mute(120, 1), 42L);
        Decision discarded = decision("direct_insult", Action.mute(30, 1));
        discarded.markDismissed(42L);
        Decision waiting = decision("direct_insult", Action.mute(30, 1));

        when(decisions.findByCreatedAtAfter(any()))
                .thenReturn(List.of(asProposed, overridden, discarded, waiting));

        command.handle(msg, List.of("all"));

        assertThat(report())
                .contains("Eseguite: 2")
                .contains("modificate dagli admin: 1")
                .contains("Scartate: 1")
                .contains("In attesa: 1");
    }

    @Test
    void groupsExecutedMutesByLadderRung() {
        Decision first = decision("direct_insult", Action.mute(5, 0));
        first.markExecuted(Action.mute(5, 0), 42L);
        Decision second = decision("direct_insult", Action.mute(30, 1));
        second.markExecuted(Action.mute(30, 1), 42L);

        when(decisions.findByCreatedAtAfter(any())).thenReturn(List.of(first, second));

        command.handle(msg, List.of("month"));

        assertThat(report()).contains("gradino 0: 1").contains("gradino 1: 1");
    }

    @Test
    void reportsHowOftenTheExpensiveLookupFired() {
        metrics.recordJudged(true, 10);
        metrics.recordJudged(false, 8);
        metrics.recordJudged(false, 5);
        metrics.recordJudged(false, 2);

        command.handle(msg, List.of("today"));

        // One window in four used the lookup; 25 messages were judged across them.
        assertThat(report()).contains("25.0%").contains("25 messaggi");
    }

    @Test
    void attributesTokenSpendToModelAndCallType() throws Exception {
        var tokens = new ObjectMapper().readTree(
                "{\"prompt_tokens\": 800, \"completion_tokens\": 200}");
        usage.record("primary", LlmCallType.JUDGMENT, tokens);
        usage.record("profile", LlmCallType.PROFILE, tokens);

        command.handle(msg, List.of("today"));

        assertThat(report())
                .contains("primary / JUDGMENT: 1 chiamate, 1000 token")
                .contains("profile / PROFILE: 1 chiamate, 1000 token");
    }

    @Test
    void surfacesDegradationAndFallbacks() {
        when(health.isDegraded()).thenReturn(true);
        when(health.getConsecutiveFailures()).thenReturn(4);
        when(health.getFallbacks()).thenReturn(9);
        when(health.getLastError()).thenReturn("429 <rate limited>");

        command.handle(msg, List.of("today"));

        assertThat(report())
                .contains("degradato")
                .contains("Errori consecutivi: 4")
                .contains("Fallback su modello secondario: 9")
                .contains("429 &lt;rate limited&gt;");
    }

    @Test
    void showsTheDialsThatDecideHowMuchPowerTheBotHas() {
        command.handle(msg, List.of("today"));

        assertThat(report()).contains("ON_DEMAND_ACTION").contains("0.60");
    }

    @Test
    void defaultsToTodayAndRejectsAnUnknownPeriod() {
        command.handle(msg, List.of());
        assertThat(report()).contains("today");

        assertThatThrownBy(() -> command.handle(msg, List.of("yesterday")))
                .hasMessageContaining("Periodo non riconosciuto");
    }
}
