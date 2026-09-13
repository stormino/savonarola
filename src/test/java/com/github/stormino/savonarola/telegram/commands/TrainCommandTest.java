package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.rules.ExampleLabel;
import com.github.stormino.savonarola.rules.RuleExample;
import com.github.stormino.savonarola.rules.RuleSetService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainCommandTest {

    private static final long ADMIN_ID = 42L;
    private static final String LINK = "https://t.me/c/1234567890/99";

    private RuleSetService ruleSet;
    private MessageStoreService messageStore;
    private AdminNotifier notifier;
    private TrainCommand train;
    private Message command;

    @BeforeEach
    void setUp() {
        ruleSet = mock(RuleSetService.class);
        messageStore = mock(MessageStoreService.class);
        notifier = mock(AdminNotifier.class);
        train = new TrainCommand(ruleSet, messageStore,
                TestProperties.with(OperatingMode.LOG_ONLY), notifier);

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(ADMIN_ID);
        command = mock(Message.class);
        when(command.getFrom()).thenReturn(admin);

        when(ruleSet.exists("direct_insult")).thenReturn(true);
        when(messageStore.find(TestProperties.MAIN_CHAT, 99L)).thenReturn(Optional.of(
                new StoredMessage(TestProperties.MAIN_CHAT, 99L, 777L, "@tizio",
                        "sei un buffone", null, null, Instant.now())));
    }

    @Test
    void storesTheLabelledMessageAsARuleExample() {
        train.handle(command, List.of("direct_insult", "positive", LINK));

        ArgumentCaptor<RuleExample> example = ArgumentCaptor.forClass(RuleExample.class);
        verify(ruleSet).addExample(example.capture());
        assertThat(example.getValue().getRuleId()).isEqualTo("direct_insult");
        assertThat(example.getValue().getLabel()).isEqualTo(ExampleLabel.POSITIVE);
        assertThat(example.getValue().getText()).isEqualTo("sei un buffone");
    }

    @Test
    void tellsTheAdminTrainingIsNeverRetroactive() {
        train.handle(command, List.of("direct_insult", "positive", LINK));

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), reply.capture());
        assertThat(reply.getValue()).contains("Nessuna azione retroattiva");
    }

    @Test
    void reportsWhenTheMessageHasAgedOutOfTheStore() {
        when(messageStore.find(anyLong(), anyLong())).thenReturn(Optional.empty());

        train.handle(command, List.of("direct_insult", "negative", LINK));

        verify(ruleSet, never()).addExample(any());
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), reply.capture());
        assertThat(reply.getValue()).contains("retention");
    }

    @Test
    void fallsBackToTheMainChatForPublicLinks() {
        train.handle(command, List.of("direct_insult", "positive", "https://t.me/savgroup/99"));

        verify(messageStore).find(eq(TestProperties.MAIN_CHAT), eq(99L));
        verify(ruleSet).addExample(any());
    }

    @Test
    void rejectsUnknownRulesLabelsAndLinks() {
        assertThatThrownBy(() -> train.handle(command, List.of("nope", "positive", LINK)))
                .hasMessageContaining("Regola sconosciuta");
        assertThatThrownBy(() -> train.handle(command, List.of("direct_insult", "maybe", LINK)))
                .hasMessageContaining("Etichetta non valida");
        assertThatThrownBy(() -> train.handle(command, List.of("direct_insult", "positive", "nope")))
                .hasMessageContaining("Link non riconosciuto");
        assertThatThrownBy(() -> train.handle(command, List.of("direct_insult")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
