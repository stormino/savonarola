package com.github.stormino.savonarola.telegram;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class AdminNotifierTest {

    private final TelegramClient client = mock(TelegramClient.class);

    private String chatIdOfSentMessage() throws Exception {
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        return sent.getValue().getChatId();
    }

    @Test
    void sanctionsGoToTheAdminChat() throws Exception {
        new AdminNotifier(client, TestProperties.with(OperatingMode.LIVE_ACTION))
                .send("mutato");

        assertThat(chatIdOfSentMessage()).isEqualTo(String.valueOf(TestProperties.ADMIN_CHAT));
    }

    @Test
    void theVerboseStreamGoesToTheOwnerChat() throws Exception {
        new AdminNotifier(client, TestProperties.with(OperatingMode.LOG_ONLY))
                .sendToOwner("giudizio");

        assertThat(chatIdOfSentMessage()).isEqualTo(String.valueOf(TestProperties.OWNER_CHAT));
    }

    @Test
    void theVerboseStreamIsDroppedWhenNoOwnerChatIsConfigured() throws Exception {
        new AdminNotifier(client, TestProperties.withoutOwnerChat(OperatingMode.LOG_ONLY))
                .sendToOwner("giudizio");

        verify(client, never()).execute(any(SendMessage.class));
    }

    @Test
    void systemAlertsStayWithTheAdminsWhoHaveToActOnThem() throws Exception {
        new AdminNotifier(client, TestProperties.with(OperatingMode.LOG_ONLY))
                .sendSystem("LLM degradato");

        assertThat(chatIdOfSentMessage()).isEqualTo(String.valueOf(TestProperties.ADMIN_CHAT));
    }
}
