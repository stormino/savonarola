package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActionExecutorTest {

    private static final long CHAT = TestProperties.MAIN_CHAT;
    private static final long OFFENDER = 777L;

    private TelegramClient client;
    private ActionExecutor executor;

    @BeforeEach
    void setUp() {
        client = mock(TelegramClient.class);
        executor = new ActionExecutor(client, TestProperties.with(OperatingMode.LIVE_ACTION));
    }

    private String announcementText() throws TelegramApiException {
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        return sent.getValue().getText();
    }

    @Test
    void restrictsTheMemberForTheGivenDuration() throws Exception {
        executor.mute(CHAT, OFFENDER, "@colpevole", 99L, 30);

        ArgumentCaptor<RestrictChatMember> restrict =
                ArgumentCaptor.forClass(RestrictChatMember.class);
        verify(client).execute(restrict.capture());
        assertThat(restrict.getValue().getUserId()).isEqualTo(OFFENDER);
        assertThat(restrict.getValue().getPermissions().getCanSendMessages()).isFalse();
    }

    @Test
    void mentionsAUsernameDirectly() throws Exception {
        executor.mute(CHAT, OFFENDER, "@colpevole", 99L, 30);

        assertThat(announcementText()).isEqualTo("Utente @colpevole mutato per 30 minuti.");
    }

    @Test
    void mentionsSomeoneWithoutAUsernameThroughATgLink() throws Exception {
        executor.mute(CHAT, OFFENDER, "Mario Rossi", 99L, 120);

        assertThat(announcementText())
                .isEqualTo("Utente <a href=\"tg://user?id=777\">Mario Rossi</a> mutato per 2 ore.");
    }

    @Test
    void escapesADisplayNameThatWouldBreakTheMarkup() throws Exception {
        executor.mute(CHAT, OFFENDER, "Mario <b>Rossi</b>", 99L, 5);

        assertThat(announcementText()).contains("Mario &lt;b&gt;Rossi&lt;/b&gt;");
    }

    @Test
    void namesDurationsTheWayAnItalianReaderWouldSayThem() throws Exception {
        executor.mute(CHAT, OFFENDER, "@x", 99L, 5);
        assertThat(announcementText()).contains("5 minuti");
    }

    @Test
    void stillMutesWhenTheAnnouncementCannotBeSent() throws Exception {
        org.mockito.Mockito.when(client.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("chat not found"));

        executor.mute(CHAT, OFFENDER, "@colpevole", 99L, 30);

        verify(client).execute(any(RestrictChatMember.class));
    }
}
