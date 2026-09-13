package com.github.stormino.savonarola.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These queries are the ones no mock can vouch for: two use JPQL projections with GROUP BY
 * and HAVING, and until now nothing had run them against a real database.
 */
@DataJpaTest
class StoredMessageRepositoryTest {

    private static final long CHAT = -1001234567890L;
    private static final long OTHER_CHAT = -1009999999999L;
    private static final long ALICE = 1L;
    private static final long BRUNO = 2L;
    private static final long CARLA = 3L;

    @Autowired
    private StoredMessageRepository repository;

    private long nextMessageId = 1;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    private StoredMessage save(long chatId, long senderId, String name, String text,
                               Long replyToUserId, Instant sentAt) {
        return repository.save(new StoredMessage(chatId, nextMessageId++, senderId, name, text,
                replyToUserId == null ? null : 999L, replyToUserId, sentAt));
    }

    private StoredMessage save(long senderId, String name, String text, Long replyToUserId) {
        return save(CHAT, senderId, name, text, replyToUserId, Instant.now());
    }

    @Test
    void findsAMessageByItsChatAndId() {
        StoredMessage saved = save(ALICE, "@alice", "ciao", null);

        assertThat(repository.findByChatIdAndMessageId(CHAT, saved.getMessageId()))
                .get()
                .satisfies(m -> assertThat(m.getText()).isEqualTo("ciao"));
        assertThat(repository.findByChatIdAndMessageId(OTHER_CHAT, saved.getMessageId())).isEmpty();
    }

    @Test
    void returnsTheContextWindowNewestFirstAndCapped() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 5; i++) {
            save(CHAT, ALICE, "@alice", "messaggio " + i, null, base.plusSeconds(i * 60));
        }

        List<StoredMessage> window = repository.findByChatIdOrderBySentAtDesc(
                CHAT, PageRequest.of(0, 3));

        assertThat(window).hasSize(3);
        assertThat(window).extracting(StoredMessage::getText)
                .containsExactly("messaggio 4", "messaggio 3", "messaggio 2");
    }

    @Test
    void findsOnlyWhatTheSenderAimedAtThatOneRecipient() {
        save(ALICE, "@alice", "verso bruno", BRUNO);
        save(ALICE, "@alice", "verso carla", CARLA);
        save(BRUNO, "@bruno", "verso alice", ALICE);
        save(ALICE, "@alice", "a nessuno", null);

        List<StoredMessage> interactions = repository.findInteractions(
                CHAT, ALICE, BRUNO, Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(interactions).extracting(StoredMessage::getText).containsExactly("verso bruno");
    }

    @Test
    void excludesInteractionsOlderThanTheWindow() {
        save(CHAT, ALICE, "@alice", "vecchio", BRUNO, Instant.now().minus(60, ChronoUnit.DAYS));
        save(CHAT, ALICE, "@alice", "recente", BRUNO, Instant.now());

        assertThat(repository.findInteractions(CHAT, ALICE, BRUNO,
                Instant.now().minus(30, ChronoUnit.DAYS)))
                .extracting(StoredMessage::getText).containsExactly("recente");
    }

    @Test
    void listsActiveSendersBusiestFirstAboveTheMinimum() {
        for (int i = 0; i < 5; i++) save(ALICE, "@alice", "a" + i, null);
        for (int i = 0; i < 3; i++) save(BRUNO, "@bruno", "b" + i, null);
        save(CARLA, "@carla", "una sola", null);

        List<Long> active = repository.findActiveSenders(
                CHAT, Instant.now().minus(30, ChronoUnit.DAYS), 3, PageRequest.of(0, 10));

        assertThat(active).containsExactly(ALICE, BRUNO);
    }

    @Test
    void capsTheActiveSenderListAtThePageSize() {
        for (int i = 0; i < 5; i++) save(ALICE, "@alice", "a" + i, null);
        for (int i = 0; i < 4; i++) save(BRUNO, "@bruno", "b" + i, null);

        assertThat(repository.findActiveSenders(CHAT, Instant.now().minus(30, ChronoUnit.DAYS),
                1, PageRequest.of(0, 1))).containsExactly(ALICE);
    }

    @Test
    void projectsFrequentPairsWithTheirDirectionAndCount() {
        for (int i = 0; i < 4; i++) save(ALICE, "@alice", "verso bruno " + i, BRUNO);
        for (int i = 0; i < 2; i++) save(BRUNO, "@bruno", "verso alice " + i, ALICE);

        List<StoredMessageRepository.PairCount> pairs = repository.findFrequentPairs(
                CHAT, Instant.now().minus(30, ChronoUnit.DAYS), 3, PageRequest.of(0, 10));

        assertThat(pairs).singleElement().satisfies(pair -> {
            assertThat(pair.getSenderId()).isEqualTo(ALICE);
            assertThat(pair.getTargetId()).isEqualTo(BRUNO);
            assertThat(pair.getInteractions()).isEqualTo(4);
        });
    }

    @Test
    void neverPairsSomeoneWithThemselves() {
        for (int i = 0; i < 5; i++) save(ALICE, "@alice", "rispondo a me " + i, ALICE);

        assertThat(repository.findFrequentPairs(CHAT, Instant.now().minus(30, ChronoUnit.DAYS),
                1, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void keepsChatsApartInEveryAggregate() {
        for (int i = 0; i < 5; i++) save(OTHER_CHAT, ALICE, "@alice", "altrove " + i, BRUNO, Instant.now());

        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(repository.findActiveSenders(CHAT, since, 1, PageRequest.of(0, 10))).isEmpty();
        assertThat(repository.findFrequentPairs(CHAT, since, 1, PageRequest.of(0, 10))).isEmpty();
        assertThat(repository.countByChatIdAndSentAtAfter(CHAT, since)).isZero();
        assertThat(repository.countByChatIdAndSentAtAfter(OTHER_CHAT, since)).isEqualTo(5);
    }

    @Test
    void resolvesAUsernameToTheIdItWasLastSeenOn() {
        save(CHAT, ALICE, "@alice", "vecchio", null, Instant.now().minus(2, ChronoUnit.DAYS));
        save(CHAT, ALICE, "@alice", "recente", null, Instant.now());

        assertThat(repository.findFirstBySenderNameOrderBySentAtDesc("@alice"))
                .get()
                .satisfies(m -> assertThat(m.getText()).isEqualTo("recente"));
        assertThat(repository.findFirstBySenderNameOrderBySentAtDesc("@ignoto")).isEmpty();
    }

    @Test
    void purgesOnlyWhatIsPastRetention() {
        save(CHAT, ALICE, "@alice", "vecchio", null, Instant.now().minus(120, ChronoUnit.DAYS));
        save(CHAT, ALICE, "@alice", "recente", null, Instant.now());

        repository.deleteBySentAtBefore(Instant.now().minus(90, ChronoUnit.DAYS));

        assertThat(repository.findAll()).extracting(StoredMessage::getText)
                .containsExactly("recente");
    }

    @Test
    void storesTextUpToTheColumnLimit() {
        String long_ = "a".repeat(4096);

        StoredMessage saved = save(ALICE, "@alice", long_, null);

        assertThat(repository.findByChatIdAndMessageId(CHAT, saved.getMessageId()))
                .get().satisfies(m -> assertThat(m.getText()).hasSize(4096));
    }
}
