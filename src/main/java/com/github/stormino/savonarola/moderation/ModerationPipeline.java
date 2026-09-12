package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.llm.JudgmentInput;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.llm.LlmJudge;
import com.github.stormino.savonarola.rules.RuleSetService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import com.github.stormino.savonarola.telegram.AdminNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModerationPipeline {

    private final SavonarolaProperties props;
    private final RuleSetService ruleSet;
    private final MessageStoreService messageStore;
    private final ProfileProvider profiles;
    private final LlmJudge judge;
    private final EscalationService escalation;
    private final DecisionRouter router;

    @Async
    public void process(Message msg) {
        var rules = ruleSet.activeRules();
        if (rules.isEmpty()) return;

        long chatId = msg.getChatId();
        long senderId = msg.getFrom().getId();
        Long targetId = msg.getReplyToMessage() != null && msg.getReplyToMessage().getFrom() != null
                ? msg.getReplyToMessage().getFrom().getId()
                : null;

        // @Async swallows whatever is thrown here, so a miss is logged rather than raised.
        Optional<StoredMessage> stored = messageStore.find(chatId, msg.getMessageId());
        if (stored.isEmpty()) {
            log.warn("Message {} in chat {} was not persisted before judging — skipping",
                    msg.getMessageId(), chatId);
            return;
        }
        StoredMessage target = stored.get();

        List<StoredMessage> extendedHistory = maybeExtendedHistory(chatId, senderId, targetId);

        JudgmentInput input = new JudgmentInput(
                rules,
                ruleSet.examplesByRule(),
                target,
                messageStore.contextWindow(chatId),
                profiles.profileFor(senderId),
                targetId != null ? profiles.profileFor(targetId) : null,
                extendedHistory);

        try {
            Judgment judgment = judge.judge(input);
            Action suggested = judgment.violated()
                    ? escalation.nextAction(senderId)
                    : null;
            router.route(msg, judgment, suggested);
        } catch (LlmException e) {
            // Already reported by HealthMonitor; never act on a failed judgment.
            log.warn("Skipping message {} — judgment unavailable", msg.getMessageId());
        }
    }

    /**
     * The expensive history lookup only runs when a weak signal already suggests
     * a pattern, mirroring how a human admin checks back only when something feels off.
     */
    private List<StoredMessage> maybeExtendedHistory(long chatId, long senderId, Long targetId) {
        if (targetId == null || !ruleSet.anyRequiresHistory()) return List.of();

        int count = profiles.negativeInteractionCount(senderId, targetId);
        if (count < props.patternDetection().negativeInteractionThreshold()) return List.of();

        return messageStore.interactions(chatId, senderId, targetId, props.patternDetection().windowDays());
    }
}
