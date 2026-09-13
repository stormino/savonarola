package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.Action;
import com.github.stormino.savonarola.moderation.ActionExecutor;
import com.github.stormino.savonarola.moderation.Decision;
import com.github.stormino.savonarola.moderation.DecisionRepository;
import com.github.stormino.savonarola.moderation.DecisionStatus;
import com.github.stormino.savonarola.moderation.Judgment;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.settings.SettingsService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.AdminRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecuteCommandTest {

    private static final long ADMIN_ID = 42L;
    private static final long OFFENDER_ID = 777L;

    private DecisionRepository decisions;
    private ActionExecutor executor;
    private MessageStoreService messageStore;
    private AdminRegistry adminRegistry;
    private AdminNotifier notifier;
    private Message command;

    @BeforeEach
    void setUp() {
        decisions = mock(DecisionRepository.class);
        executor = mock(ActionExecutor.class);
        messageStore = mock(MessageStoreService.class);
        adminRegistry = mock(AdminRegistry.class);
        notifier = mock(AdminNotifier.class);

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(ADMIN_ID);

        command = mock(Message.class);
        when(command.getFrom()).thenReturn(admin);

        when(messageStore.find(anyLong(), anyLong())).thenReturn(Optional.of(
                new StoredMessage(TestProperties.MAIN_CHAT, 99L, OFFENDER_ID,
                        "@colpevole", "sei un buffone", null, null, Instant.now())));
    }

    private ExecuteCommand commandUnder(OperatingMode mode) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.operatingMode()).thenReturn(mode);
        return new ExecuteCommand(settings, decisions, executor,
                messageStore, adminRegistry, notifier);
    }

    private Decision pending(Action suggested) {
        Decision decision = Decision.pending(TestProperties.MAIN_CHAT, 99L, OFFENDER_ID,
                new Judgment(true, "direct_insult", 0.9, "Insulto diretto."), suggested);
        when(decisions.findById(decision.getId())).thenReturn(Optional.of(decision));
        return decision;
    }

    @Test
    void refusesToActAtAllInDryRun() throws Exception {
        Decision decision = pending(Action.mute(30, 1));

        commandUnder(OperatingMode.LOG_ONLY)
                .handle(command, List.of(decision.getId().toString()));

        verifyNoInteractions(executor);
        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.PENDING);

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), reply.capture());
        assertThat(reply.getValue()).contains("LOG_ONLY");
    }

    @Test
    void appliesTheProposedPenaltyWhenConfirmedAsIs() throws Exception {
        Decision decision = pending(Action.mute(30, 1));

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString()));

        verify(executor).mute(TestProperties.MAIN_CHAT, OFFENDER_ID, "@colpevole", 99L, 30);
        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.EXECUTED);
        assertThat(decision.getResolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(decision.wasModified()).isFalse();
    }

    @Test
    void recordsAnOverriddenDurationAsAModification() throws Exception {
        Decision decision = pending(Action.mute(30, 1));

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString(), "duration=2h"));

        verify(executor).mute(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
        assertThat(decision.getActualDurationMinutes()).isEqualTo(120);
        assertThat(decision.wasModified()).isTrue();
    }

    @Test
    void dismissLeavesTheUserUntouched() throws Exception {
        Decision decision = pending(Action.mute(30, 1));

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString(), "dismiss"));

        verifyNoInteractions(executor);
        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.DISMISSED);
        assertThat(decision.getResolvedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void willNotActTwiceOnTheSameDecision() throws Exception {
        Decision decision = pending(Action.mute(30, 1));
        decision.markExecuted(Action.mute(30, 1), 1L);

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString()));

        verifyNoInteractions(executor);
    }

    @Test
    void demandsAnExplicitDurationAtTheTopOfTheLadder() {
        Decision decision = pending(Action.adminReview(4));

        assertThatThrownBy(() -> commandUnder(OperatingMode.LIVE_ACTION)
                .handle(command, List.of(decision.getId().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration=");
    }

    @Test
    void executesTopOfLadderDecisionsOnceADurationIsGiven() throws Exception {
        Decision decision = pending(Action.adminReview(4));

        commandUnder(OperatingMode.LIVE_ACTION)
                .handle(command, List.of(decision.getId().toString(), "duration=24h"));

        verify(executor).mute(TestProperties.MAIN_CHAT, OFFENDER_ID, "@colpevole", 99L, 1440);
        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.EXECUTED);
    }

    @Test
    void refusesToSanctionSomeoneWhoIsNowAnAdmin() throws Exception {
        Decision decision = pending(Action.mute(30, 1));
        when(adminRegistry.isAdmin(TestProperties.MAIN_CHAT, OFFENDER_ID)).thenReturn(true);

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString()));

        verifyNoInteractions(executor);
        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.PENDING);
    }

    @Test
    void keepsTheDecisionPendingWhenTelegramRejectsTheMute() throws Exception {
        Decision decision = pending(Action.mute(30, 1));
        org.mockito.Mockito.doThrow(new RuntimeException("chat not found"))
                .when(executor).mute(anyLong(), anyLong(), anyString(), anyLong(), anyInt());

        commandUnder(OperatingMode.ON_DEMAND_ACTION)
                .handle(command, List.of(decision.getId().toString()));

        assertThat(decision.getStatus()).isEqualTo(DecisionStatus.PENDING);
        verify(decisions, never()).save(any());
    }

    @Test
    void rejectsUnparseableInput() {
        ExecuteCommand cmd = commandUnder(OperatingMode.ON_DEMAND_ACTION);

        assertThatThrownBy(() -> cmd.handle(command, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmd.handle(command, List.of("not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmd.handle(command, List.of(UUID.randomUUID().toString())))
                .hasMessageContaining("Nessuna decisione");
    }
}
