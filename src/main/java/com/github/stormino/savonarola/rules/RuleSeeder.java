package com.github.stormino.savonarola.rules;

import com.github.stormino.savonarola.moderation.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the rules formalised in SPEC section 2.3 on first start.
 *
 * Definitions are in English (SPEC section 16) and are written as boundaries rather
 * than topics: the judge is told what does NOT cross the line as explicitly as what
 * does, because the group's own rulebook welcomes blunt disagreement.
 *
 * Existing rules are never overwritten — once admins have curated a rule through
 * /regolamento aggiorna or /train, this seeder must not undo that work.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RuleSeeder implements ApplicationRunner {

    private final RuleRepository rules;

    private static final List<Rule> DEFAULTS = List.of(
            new Rule("direct_insult", Severity.HIGH, false, """
                    The message directs an insult, slur, or demeaning label at another
                    participant as a person — attacking who they are rather than what
                    they said. The target must be a member of the conversation, not a
                    player, coach, pundit, or other public figure being discussed.

                    NOT a violation: harsh, sarcastic, or dismissive judgements about a
                    tennis opinion, a match, or a player; profanity used as emphasis and
                    not aimed at anyone; insults the sender clearly aims at themselves.
                    """, true),

            new Rule("passive_aggressive_pattern", Severity.MEDIUM, true, """
                    The message needles a specific participant through implication rather
                    than open disagreement — veiled digs, pointed "innocent" questions,
                    ostentatious politeness used to belittle, or repeated allusions to a
                    past exchange meant to keep scoring points off them.

                    Judge this against the sender's habitual tone and the pair's history:
                    a sender who is dry or sardonic with everyone is not targeting anyone,
                    and long-running mutual ribbing between two participants is not a
                    violation. What distinguishes a violation is asymmetry and persistence
                    toward one person who is not playing along.
                    """, true),

            new Rule("intimidation_pattern", Severity.HIGH, true, """
                    The sender is pressuring a specific participant into silence or
                    withdrawal — through threats, menacing insinuation, or a sustained
                    campaign of hostile replies that follows that person across the
                    conversation.

                    By construction this cannot be established from a single message. A
                    violation requires evidence in the supplied prior interactions that the
                    behaviour is repeated and directed at the same person. If only the
                    message at hand looks hostile, this rule is not violated — consider
                    direct_insult instead.
                    """, true),

            new Rule("mockery_of_opinions", Severity.MEDIUM, false, """
                    The message ridicules another participant for holding an opinion —
                    treating the opinion as evidence that the person is stupid, inviting
                    others to laugh at them, or mimicking them to make them look foolish.
                    The object of the ridicule is the person through their view, not the
                    view itself.

                    NOT a violation: calling an opinion wrong, absurd, or indefensible,
                    however bluntly, and arguing against it with contempt for the argument.
                    The group explicitly protects strong disagreement; the line is crossed
                    only when the point stops being the opinion and becomes the holder.
                    """, true),

            new Rule("troll_hit_and_run", Severity.HIGH, true, """
                    A participant with little or no ongoing presence in the conversation
                    drops in with a pointed, inflammatory intervention aimed at a specific
                    person or an ongoing episode, with no apparent interest in the exchange
                    that follows.

                    The signature is behavioural, so it needs the history: sparse prior
                    participation combined with a targeted aggressive message. A regular,
                    engaged participant who says something aggressive does not match this
                    rule — judge that message on its own terms under the other rules.
                    """, true));

    @Override
    public void run(ApplicationArguments args) {
        for (Rule rule : DEFAULTS) {
            if (!rules.existsById(rule.getId())) {
                rules.save(rule);
                log.info("Seeded rule {}", rule.getId());
            }
        }
    }
}
