package com.github.stormino.savonarola.group;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** What the judge needs to know about the group itself, as opposed to the rulebook. */
@Service
@RequiredArgsConstructor
public class GroupKnowledgeService {

    private final StoredMessageRepository messages;
    private final GroupDossierRepository dossiers;
    private final SavonarolaProperties props;

    /** Busiest speakers first, so a capped roster keeps the people who actually matter. */
    public List<String> roster(long chatId) {
        var group = props.group();
        Instant since = Instant.now().minus(group.rosterDays(), ChronoUnit.DAYS);
        return messages.findParticipantNames(chatId, since, PageRequest.of(0, group.rosterMaxNames()));
    }

    public Optional<String> dossier(long chatId) {
        return dossiers.findById(chatId)
                .map(GroupDossier::getNotes)
                .filter(notes -> notes != null && !notes.isBlank());
    }

    @Transactional
    public void replaceDossier(long chatId, String notes, long adminId) {
        dossiers.findById(chatId).ifPresentOrElse(
                existing -> existing.replace(notes, adminId),
                () -> dossiers.save(new GroupDossier(chatId, notes, adminId)));
    }

    @Transactional
    public void appendToDossier(long chatId, String line, long adminId) {
        dossiers.findById(chatId).ifPresentOrElse(
                existing -> existing.append(line, adminId),
                () -> dossiers.save(new GroupDossier(chatId, line, adminId)));
    }

    @Transactional
    public void clearDossier(long chatId, long adminId) {
        dossiers.findById(chatId).ifPresent(existing -> existing.replace(null, adminId));
    }
}
