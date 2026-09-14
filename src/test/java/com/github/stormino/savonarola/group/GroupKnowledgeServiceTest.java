package com.github.stormino.savonarola.group;

import com.github.stormino.savonarola.TestProperties;
import com.github.stormino.savonarola.moderation.OperatingMode;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupKnowledgeServiceTest {

    private static final long CHAT = TestProperties.MAIN_CHAT;
    private static final long ADMIN = 42L;

    private StoredMessageRepository messages;
    private GroupDossierRepository dossiers;
    private GroupKnowledgeService knowledge;

    @BeforeEach
    void setUp() {
        messages = mock(StoredMessageRepository.class);
        dossiers = mock(GroupDossierRepository.class);
        knowledge = new GroupKnowledgeService(messages, dossiers,
                TestProperties.with(OperatingMode.LOG_ONLY));

        when(messages.findParticipantNames(anyLong(), any(), any())).thenReturn(List.of());
        when(dossiers.findById(anyLong())).thenReturn(Optional.empty());
        when(dossiers.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void capsTheRosterSoAHugeGroupCannotFloodThePrompt() {
        knowledge.roster(CHAT);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findParticipantNames(anyLong(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(40);
    }

    @Test
    void reportsNoDossierRatherThanAnEmptyOne() {
        assertThat(knowledge.dossier(CHAT)).isEmpty();

        when(dossiers.findById(CHAT)).thenReturn(Optional.of(new GroupDossier(CHAT, "   ", ADMIN)));
        assertThat(knowledge.dossier(CHAT)).isEmpty();
    }

    @Test
    void storesTheFirstNoteAsANewDossier() {
        knowledge.appendToDossier(CHAT, "Dario Puppo è un giornalista, non un membro.", ADMIN);

        ArgumentCaptor<GroupDossier> saved = ArgumentCaptor.forClass(GroupDossier.class);
        verify(dossiers).save(saved.capture());
        assertThat(saved.getValue().getNotes())
                .isEqualTo("Dario Puppo è un giornalista, non un membro.");
        assertThat(saved.getValue().getUpdatedBy()).isEqualTo(ADMIN);
    }

    @Test
    void appendsToWhatIsAlreadyThere() {
        GroupDossier existing = new GroupDossier(CHAT, "Prima nota.", ADMIN);
        when(dossiers.findById(CHAT)).thenReturn(Optional.of(existing));

        knowledge.appendToDossier(CHAT, "Seconda nota.", ADMIN);

        assertThat(existing.getNotes()).isEqualTo("Prima nota.\nSeconda nota.");
        verify(dossiers, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void replacingDiscardsWhatWasThereBefore() {
        GroupDossier existing = new GroupDossier(CHAT, "Vecchio.", ADMIN);
        when(dossiers.findById(CHAT)).thenReturn(Optional.of(existing));

        knowledge.replaceDossier(CHAT, "Nuovo.", ADMIN);

        assertThat(existing.getNotes()).isEqualTo("Nuovo.");
    }

    @Test
    void clearingLeavesTheJudgeWithTheRosterAlone() {
        GroupDossier existing = new GroupDossier(CHAT, "Qualcosa.", ADMIN);
        when(dossiers.findById(CHAT)).thenReturn(Optional.of(existing));

        knowledge.clearDossier(CHAT, ADMIN);

        assertThat(knowledge.dossier(CHAT)).isEmpty();
    }
}
