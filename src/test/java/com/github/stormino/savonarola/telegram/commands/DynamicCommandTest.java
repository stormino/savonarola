package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.profile.DynamicSource;
import com.github.stormino.savonarola.profile.KnownDynamic;
import com.github.stormino.savonarola.profile.KnownDynamicRepository;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.store.StoredMessageRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCommandTest {

    private static final long TIZIO = 111L;
    private static final long CAIO = 222L;

    private KnownDynamicRepository dynamics;
    private StoredMessageRepository messages;
    private DynamicCommand command;
    private Message msg;

    @BeforeEach
    void setUp() {
        dynamics = mock(KnownDynamicRepository.class);
        messages = mock(StoredMessageRepository.class);
        command = new DynamicCommand(dynamics, messages, mock(AdminNotifier.class));

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(42L);
        msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(admin);

        when(dynamics.findByUserIdAndWithUserId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(messages.findFirstBySenderNameOrderBySentAtDesc(anyString()))
                .thenReturn(Optional.empty());
        seenWriting("@tizio", TIZIO);
        seenWriting("@caio", CAIO);
    }

    private void seenWriting(String username, long userId) {
        when(messages.findFirstBySenderNameOrderBySentAtDesc(username)).thenReturn(Optional.of(
                new StoredMessage(TestProperties.MAIN_CHAT, 1L, userId, username,
                        "ciao", null, null, Instant.now())));
    }

    @Test
    void recordsTheRelationshipInBothDirections() {
        command.handle(msg, List.of("@tizio", "@caio", "rivalità", "scherzosa", "di", "vecchia", "data"));

        ArgumentCaptor<KnownDynamic> saved = ArgumentCaptor.forClass(KnownDynamic.class);
        verify(dynamics, org.mockito.Mockito.times(2)).save(saved.capture());

        assertThat(saved.getAllValues())
                .extracting(KnownDynamic::getUserId, KnownDynamic::getWithUserId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(TIZIO, CAIO),
                        org.assertj.core.groups.Tuple.tuple(CAIO, TIZIO));
        assertThat(saved.getAllValues()).allSatisfy(dynamic -> {
            assertThat(dynamic.getSource()).isEqualTo(DynamicSource.ADMIN_ANNOTATED);
            assertThat(dynamic.getPattern()).isEqualTo("rivalità scherzosa di vecchia data");
        });
    }

    @Test
    void acceptsNumericIdsForMembersTheBotHasNotSeenWrite() {
        command.handle(msg, List.of("999", "888", "si", "punzecchiano"));

        ArgumentCaptor<KnownDynamic> saved = ArgumentCaptor.forClass(KnownDynamic.class);
        verify(dynamics, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(KnownDynamic::getUserId)
                .containsExactlyInAnyOrder(999L, 888L);
    }

    @Test
    void overwritesAnEarlierAnnotationRatherThanAddingASecond() {
        KnownDynamic existing = new KnownDynamic(TIZIO, CAIO, "vecchia nota", DynamicSource.BOT_INFERRED);
        when(dynamics.findByUserIdAndWithUserId(TIZIO, CAIO)).thenReturn(Optional.of(existing));

        command.handle(msg, List.of("@tizio", "@caio", "in", "realtà", "si", "stimano"));

        assertThat(existing.getPattern()).isEqualTo("in realtà si stimano");
        assertThat(existing.getSource()).isEqualTo(DynamicSource.ADMIN_ANNOTATED);
        verify(dynamics, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesAUsernameTheBotHasNeverSeen() {
        assertThatThrownBy(() -> command.handle(msg, List.of("@ignoto", "@caio", "qualcosa")))
                .hasMessageContaining("Non ho mai visto scrivere @ignoto");
        verify(dynamics, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesNonsenseInput() {
        assertThatThrownBy(() -> command.handle(msg, List.of("@tizio", "@caio")))
                .hasMessageContaining("descrizione");
        assertThatThrownBy(() -> command.handle(msg, List.of("@tizio", "@tizio", "sé", "stesso")))
                .hasMessageContaining("diversi");
        assertThatThrownBy(() -> command.handle(msg, List.of("tizio", "@caio", "qualcosa")))
                .hasMessageContaining("Utente non riconosciuto");
    }
}
