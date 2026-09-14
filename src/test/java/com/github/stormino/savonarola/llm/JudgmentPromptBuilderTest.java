package com.github.stormino.savonarola.llm;

import com.github.stormino.savonarola.moderation.Severity;
import com.github.stormino.savonarola.rules.Rule;
import com.github.stormino.savonarola.store.StoredMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The participants block exists because the bot flagged an insult against "Puppo", a
 * journalist the group discusses. These assert that the judge is told who is in the room.
 */
class JudgmentPromptBuilderTest {

    private final JudgmentPromptBuilder builder = new JudgmentPromptBuilder();

    private static StoredMessage message(long id, String text) {
        return new StoredMessage(1L, id, 7L, "@marco", text, null, null, Instant.now());
    }

    private static JudgmentInput input(List<String> participants, String dossier) {
        return new JudgmentInput(participants, dossier,
                List.of(new Rule("direct_insult", Severity.HIGH, false, "Def.", true)),
                Map.of(), List.of(message(10, "che pena Puppo")), List.of(), Map.of(), List.of());
    }

    @Test
    void namesTheParticipantsSoAThirdPartyCannotBeMistakenForOne() {
        String prompt = builder.userPrompt(input(List.of("@marco", "@luca"), null));

        assertThat(prompt)
                .contains("PARTICIPANTS IN THIS GROUP")
                .contains("- @marco")
                .contains("- @luca")
                .contains("NOT on this list is a third party");
    }

    @Test
    void theSystemPromptSpellsOutThatThirdPartiesAreNotProtected() {
        assertThat(builder.systemPrompt())
                .contains("THIRD PARTY")
                .contains("journalists")
                .contains("never apply to");
    }

    @Test
    void saysSoWhenItDoesNotYetKnowWhoIsInTheGroup() {
        String prompt = builder.userPrompt(input(List.of(), null));

        assertThat(prompt).contains("unknown").contains("possibly a third party");
    }

    @Test
    void carriesTheDossierWhenThereIsOne() {
        String prompt = builder.userPrompt(
                input(List.of("@marco"), "Dario Puppo è un giornalista di cui si scherza."));

        assertThat(prompt)
                .contains("ABOUT THIS GROUP")
                .contains("Dario Puppo è un giornalista");
    }

    @Test
    void omitsTheDossierSectionEntirelyWhenEmpty() {
        assertThat(builder.userPrompt(input(List.of("@marco"), "   ")))
                .doesNotContain("ABOUT THIS GROUP");
    }

    @Test
    void marksTheJudgeableMessagesApartFromContext() {
        String prompt = builder.userPrompt(input(List.of("@marco"), null));

        assertThat(prompt).contains("MESSAGES TO JUDGE").contains("[id 10]");
    }
}
