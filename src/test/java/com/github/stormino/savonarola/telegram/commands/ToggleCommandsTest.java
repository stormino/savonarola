package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.rules.RuleRepository;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToggleCommandsTest {

    private static final long ADMIN = 42L;

    private SettingsService settings;
    private RuleRepository rules;
    private AdminNotifier notifier;
    private Message msg;

    @BeforeEach
    void setUp() {
        settings = mock(SettingsService.class);
        rules = mock(RuleRepository.class);
        notifier = mock(AdminNotifier.class);

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(ADMIN);
        msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(admin);

        when(settings.operatingMode()).thenReturn(OperatingMode.LOG_ONLY);
        when(settings.confidenceThreshold()).thenReturn(0.6);
    }

    private String announcement() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(text.capture());
        return text.getValue();
    }

    private String reply() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), text.capture());
        return text.getValue();
    }

    @Test
    void modeWithoutArgumentsOnlyReports() {
        new ModeCommand(settings, notifier).handle(msg, List.of());

        assertThat(reply()).contains("LOG_ONLY");
        verify(settings, never()).setOperatingMode(any(), anyLong());
    }

    @Test
    void grantingAutonomyIsAnnouncedToEveryAdminWithItsConsequence() {
        new ModeCommand(settings, notifier).handle(msg, List.of("live_action"));

        verify(settings).setOperatingMode(OperatingMode.LIVE_ACTION, ADMIN);
        assertThat(announcement())
                .contains("LOG_ONLY")
                .contains("LIVE_ACTION")
                .contains("esegue da solo");
    }

    @Test
    void changingToTheCurrentModeIsANoOp() {
        new ModeCommand(settings, notifier).handle(msg, List.of("LOG_ONLY"));

        verify(settings, never()).setOperatingMode(any(), anyLong());
        verify(notifier, never()).send(anyString());
    }

    @Test
    void rejectsAModeThatDoesNotExist() {
        assertThatThrownBy(() -> new ModeCommand(settings, notifier).handle(msg, List.of("yolo")))
                .hasMessageContaining("non riconosciuta");
    }

    @Test
    void thresholdReportsThenSetsAndAnnounces() {
        ThresholdCommand command = new ThresholdCommand(settings, notifier);

        command.handle(msg, List.of());
        assertThat(reply()).contains("0.60");

        command.handle(msg, List.of("0.85"));
        verify(settings).setConfidenceThreshold(0.85, ADMIN);
        assertThat(announcement()).contains("0.60").contains("0.85");
    }

    @Test
    void thresholdAcceptsACommaDecimalAndRejectsWords() {
        ThresholdCommand command = new ThresholdCommand(settings, notifier);

        command.handle(msg, List.of("0,75"));
        verify(settings).setConfidenceThreshold(0.75, ADMIN);

        assertThatThrownBy(() -> command.handle(msg, List.of("alta")))
                .hasMessageContaining("non valida");
    }

    @Test
    void disablingARuleKeepsItAndItsExamples() {
        Rule rule = new Rule("direct_insult", Severity.HIGH, false, "Def.", true);
        when(rules.findById("direct_insult")).thenReturn(Optional.of(rule));

        new RuleCommand(rules, notifier).handle(msg, List.of("disable", "direct_insult"));

        assertThat(rule.isEnabled()).isFalse();
        verify(rules, never()).delete(any());
        assertThat(announcement()).contains("disattivata").contains("esempi restano");
    }

    @Test
    void enablingAnAlreadyActiveRuleAnnouncesNothing() {
        Rule rule = new Rule("direct_insult", Severity.HIGH, false, "Def.", true);
        when(rules.findById("direct_insult")).thenReturn(Optional.of(rule));

        new RuleCommand(rules, notifier).handle(msg, List.of("enable", "direct_insult"));

        verify(notifier, never()).send(anyString());
        assertThat(reply()).contains("già attiva");
    }

    @Test
    void listMarksWhichRulesAreLive() {
        when(rules.findAll()).thenReturn(List.of(
                new Rule("direct_insult", Severity.HIGH, false, "Def.", true),
                new Rule("no_politics", Severity.LOW, false, "Def.", false)));

        new RuleCommand(rules, notifier).handle(msg, List.of("list"));

        assertThat(reply()).contains("✅").contains("direct_insult")
                           .contains("⛔").contains("no_politics");
    }

    @Test
    void listSaysPlainlyWhenThereIsNothingToJudgeWith() {
        when(rules.findAll()).thenReturn(List.of());

        new RuleCommand(rules, notifier).handle(msg, List.of("list"));

        assertThat(reply()).contains("non può giudicare");
    }

    @Test
    void ruleRejectsNonsense() {
        RuleCommand command = new RuleCommand(rules, notifier);
        when(rules.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> command.handle(msg, List.of()))
                .hasMessageContaining("sottocomando");
        assertThatThrownBy(() -> command.handle(msg, List.of("destroy")))
                .hasMessageContaining("Sottocomando sconosciuto");
        assertThatThrownBy(() -> command.handle(msg, List.of("enable")))
                .hasMessageContaining("Manca l'id");
        assertThatThrownBy(() -> command.handle(msg, List.of("enable", "nope")))
                .hasMessageContaining("Regola sconosciuta");
    }
}
