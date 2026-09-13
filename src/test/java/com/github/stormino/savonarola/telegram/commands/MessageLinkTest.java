package com.github.stormino.savonarola.telegram.commands;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLinkTest {

    @Test
    void parsesPrivateSupergroupLink() {
        var link = MessageLink.parse("https://t.me/c/1234567890/42").orElseThrow();

        assertThat(link.chatId()).isEqualTo(-1001234567890L);
        assertThat(link.messageId()).isEqualTo(42);
    }

    @Test
    void treatsLastSegmentAsMessageIdInForumTopics() {
        var link = MessageLink.parse("https://t.me/c/1234567890/7/42").orElseThrow();

        assertThat(link.chatId()).isEqualTo(-1001234567890L);
        assertThat(link.messageId()).isEqualTo(42);
    }

    @Test
    void leavesChatIdUnresolvedForPublicLinks() {
        var link = MessageLink.parse("https://t.me/savgroup/42").orElseThrow();

        assertThat(link.chatId()).isNull();
        assertThat(link.resolveChatId(-100999L)).isEqualTo(-100999L);
    }

    @Test
    void acceptsLinksWithoutSchemeAndWithQuery() {
        assertThat(MessageLink.parse("t.me/c/1/2").orElseThrow().messageId()).isEqualTo(2);
        assertThat(MessageLink.parse("https://t.me/c/1/2?single").orElseThrow().messageId())
                .isEqualTo(2);
    }

    @Test
    void rejectsAnythingThatIsNotAMessageLink() {
        assertThat(MessageLink.parse("https://example.com/c/1/2")).isEmpty();
        assertThat(MessageLink.parse("https://t.me/c/1")).isEmpty();
        assertThat(MessageLink.parse("not a link")).isEmpty();
        assertThat(MessageLink.parse(null)).isEmpty();
        assertThat(MessageLink.parse("  ")).isEmpty();
    }
}
