package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rulebook.ProposedRule;
import com.github.stormino.savonarola.rulebook.RulebookCompiler;
import com.github.stormino.savonarola.rulebook.RulebookProposal;
import com.github.stormino.savonarola.rulebook.RulebookService;
import com.github.stormino.savonarola.rulebook.TelegramFileReader;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulebookCommandTest {

    private static final long ADMIN = 42L;

    private RulebookCompiler compiler;
    private RulebookService rulebook;
    private MessageStoreService messageStore;
    private TelegramFileReader files;
    private AdminNotifier notifier;
    private RulebookCommand command;
    private Message msg;

    @BeforeEach
    void setUp() {
        compiler = mock(RulebookCompiler.class);
        rulebook = mock(RulebookService.class);
        messageStore = mock(MessageStoreService.class);
        files = mock(TelegramFileReader.class);
        notifier = mock(AdminNotifier.class);
        command = new RulebookCommand(compiler, rulebook, messageStore, files,
                TestProperties.with(OperatingMode.LOG_ONLY), notifier);

        User admin = mock(User.class);
        when(admin.getId()).thenReturn(ADMIN);
        msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(admin);

        when(compiler.compile(anyString())).thenReturn(List.of(
                new ProposedRule("direct_insult", Severity.HIGH, false, "Def.", List.of())));
        when(rulebook.propose(any(), anyLong()))
                .thenReturn(new RulebookProposal("[]", ADMIN));
    }

    private Message repliedMessage() {
        Message replied = mock(Message.class);
        when(msg.getReplyToMessage()).thenReturn(replied);
        return replied;
    }

    private String compiledText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(compiler).compile(text.capture());
        return text.getValue();
    }

    @Test
    void compilesTheTextOfTheQuotedMessage() {
        Message replied = repliedMessage();
        when(replied.hasDocument()).thenReturn(false);
        when(replied.hasText()).thenReturn(true);
        when(replied.getText()).thenReturn("Articolo 1: niente insulti.");

        command.handle(msg, List.of("update"));

        assertThat(compiledText()).isEqualTo("Articolo 1: niente insulti.");
    }

    @Test
    void readsAQuotedAttachmentSinceARulebookOutgrowsOneMessage() {
        Message replied = repliedMessage();
        Document doc = mock(Document.class);
        when(doc.getFileId()).thenReturn("file-123");
        when(doc.getFileSize()).thenReturn(2048L);
        when(replied.hasDocument()).thenReturn(true);
        when(replied.getDocument()).thenReturn(doc);
        when(files.read("file-123", 2048L)).thenReturn("Regolamento lunghissimo.");

        command.handle(msg, List.of("update"));

        assertThat(compiledText()).isEqualTo("Regolamento lunghissimo.");
    }

    @Test
    void resolvesAMessageLinkThroughTheStore() {
        when(messageStore.find(TestProperties.MAIN_CHAT, 99L)).thenReturn(Optional.of(
                new StoredMessage(TestProperties.MAIN_CHAT, 99L, 7L, "@tizio",
                        "Regolamento dal link.", null, null, Instant.now())));

        command.handle(msg, List.of("update", "https://t.me/c/1234567890/99"));

        assertThat(compiledText()).isEqualTo("Regolamento dal link.");
    }

    @Test
    void acceptsInlineTextForAShortAmendment() {
        command.handle(msg, List.of("update", "Vietato", "insultare", "i", "presenti."));

        assertThat(compiledText()).isEqualTo("Vietato insultare i presenti.");
    }

    @Test
    void saysSoWhenTheLinkedMessageHasAgedOut() {
        when(messageStore.find(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> command.handle(msg, List.of("update", "https://t.me/c/1/99")))
                .hasMessageContaining("non trovato");
        verify(compiler, never()).compile(anyString());
    }

    @Test
    void refusesToCompileNothing() {
        assertThatThrownBy(() -> command.handle(msg, List.of("update")))
                .hasMessageContaining("Serve il regolamento");

        Message replied = repliedMessage();
        when(replied.hasDocument()).thenReturn(false);
        when(replied.hasText()).thenReturn(false);
        assertThatThrownBy(() -> command.handle(msg, List.of("update")))
                .hasMessageContaining("né testo né un file");
    }

    @Test
    void postsTheProposalForReviewWithoutActivatingIt() {
        command.handle(msg, List.of("update", "testo"));

        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), reply.capture());
        assertThat(reply.getValue())
                .contains("Non è ancora attivo")
                .contains("/rulebook approve")
                .contains("/rulebook reject");
    }

    @Test
    void approvingAppliesTheProposalItWasGiven() {
        RulebookProposal proposal = new RulebookProposal("[]", ADMIN);
        when(rulebook.find(proposal.getId())).thenReturn(Optional.of(proposal));
        when(rulebook.approve(proposal, ADMIN))
                .thenReturn(new RulebookService.ApplyResult(2, 1, 3));

        command.handle(msg, List.of("approve", proposal.getId().toString()));

        verify(rulebook).approve(proposal, ADMIN);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(notifier).reply(any(), reply.capture());
        assertThat(reply.getValue()).contains("2").contains("1").contains("3");
    }

    @Test
    void rejectingChangesNothingActive() {
        RulebookProposal proposal = new RulebookProposal("[]", ADMIN);
        when(rulebook.find(proposal.getId())).thenReturn(Optional.of(proposal));

        command.handle(msg, List.of("reject", proposal.getId().toString()));

        verify(rulebook).reject(proposal, ADMIN);
        verify(rulebook, never()).approve(any(), anyLong());
    }

    @Test
    void willNotApplyAProposalTwice() {
        RulebookProposal proposal = new RulebookProposal("[]", ADMIN);
        proposal.resolve(com.github.stormino.savonarola.rulebook.ProposalStatus.APPROVED, ADMIN);
        when(rulebook.find(proposal.getId())).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> command.handle(msg, List.of("approve", proposal.getId().toString())))
                .hasMessageContaining("già risolta");
    }

    @Test
    void rejectsUnknownSubcommandsAndIds() {
        assertThatThrownBy(() -> command.handle(msg, List.of()))
                .hasMessageContaining("sottocomando");
        assertThatThrownBy(() -> command.handle(msg, List.of("destroy")))
                .hasMessageContaining("Sottocomando sconosciuto");
        assertThatThrownBy(() -> command.handle(msg, List.of("approve", "nope")))
                .hasMessageContaining("non valido");
        when(rulebook.find(any(UUID.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> command.handle(msg,
                List.of("approve", UUID.randomUUID().toString())))
                .hasMessageContaining("Nessuna proposta");
    }
}
