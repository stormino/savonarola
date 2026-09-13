package com.github.stormino.savonarola.telegram.commands;

import com.github.stormino.savonarola.profile.DynamicSource;
import com.github.stormino.savonarola.profile.KnownDynamic;
import com.github.stormino.savonarola.profile.KnownDynamicRepository;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.store.StoredMessageRepository;
import com.github.stormino.savonarola.telegram.AdminCommand;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import com.github.stormino.savonarola.telegram.Html;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

/**
 * SPEC 7.3 — seeds a dynamic the bot has no history to infer yet, which is most of the
 * time early on. Written in both directions: the example in the spec ("a long-standing
 * joking rivalry") describes a relationship, not one person's behaviour.
 */
@Component
@RequiredArgsConstructor
public class DynamicCommand implements AdminCommand {

    private final KnownDynamicRepository dynamics;
    private final StoredMessageRepository messages;
    private final AdminNotifier notifier;

    @Override
    public String name() {
        return "dynamic";
    }

    @Override
    public String usage() {
        return "/dynamic <@utenteA|id> <@utenteB|id> <descrizione>";
    }

    @Override
    public void handle(Message msg, List<String> args) {
        if (args.size() < 3) {
            throw new IllegalArgumentException("Servono due utenti e una descrizione.");
        }

        long userA = resolve(args.get(0));
        long userB = resolve(args.get(1));
        if (userA == userB) {
            throw new IllegalArgumentException("I due utenti devono essere diversi.");
        }

        String pattern = String.join(" ", args.subList(2, args.size()));

        annotate(userA, userB, pattern);
        annotate(userB, userA, pattern);

        notifier.reply(msg, "Dinamica registrata tra <code>" + userA + "</code> e <code>"
                + userB + "</code>:\n<i>" + Html.escape(pattern) + "</i>"
                + "\n\nIl bot non la sovrascriverà con le proprie inferenze.");
    }

    private void annotate(long userId, long withUserId, String pattern) {
        dynamics.findByUserIdAndWithUserId(userId, withUserId)
                .ifPresentOrElse(
                        existing -> existing.update(pattern, DynamicSource.ADMIN_ANNOTATED),
                        () -> dynamics.save(new KnownDynamic(
                                userId, withUserId, pattern, DynamicSource.ADMIN_ANNOTATED)));
    }

    /** Usernames are resolved from what the bot has actually seen; ids are taken as given. */
    private long resolve(String reference) {
        if (!reference.startsWith("@")) {
            try {
                return Long.parseLong(reference);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Utente non riconosciuto: '" + reference + "'. Usa @username o l'id numerico.");
            }
        }
        return messages.findFirstBySenderNameOrderBySentAtDesc(reference)
                .map(StoredMessage::getSenderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Non ho mai visto scrivere " + reference + ": non posso risalire al suo id."));
    }
}
