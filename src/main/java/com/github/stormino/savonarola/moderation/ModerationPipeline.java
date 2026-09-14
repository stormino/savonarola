package com.github.stormino.savonarola.moderation;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import com.github.stormino.savonarola.group.GroupKnowledgeService;
import com.github.stormino.savonarola.llm.JudgmentInput;
import com.github.stormino.savonarola.llm.LlmException;
import com.github.stormino.savonarola.llm.LlmJudge;
import com.github.stormino.savonarola.llm.Violation;
import com.github.stormino.savonarola.rules.RuleSetService;
import com.github.stormino.savonarola.store.MessageStoreService;
import com.github.stormino.savonarola.store.StoredMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Judges a window of messages in one call. See SPEC 4.1. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModerationPipeline {

    private final SavonarolaProperties props;
    private final RuleSetService ruleSet;
    private final GroupKnowledgeService groupKnowledge;
    private final MessageStoreService messageStore;
    private final ProfileProvider profiles;
    private final LlmJudge judge;
    private final EscalationService escalation;
    private final DecisionRouter router;
    private final PipelineMetrics metrics;
    private final JudgmentDigest digest;

    @Async
    public void judge(List<Message> window) {
        judge(window, List.of());
    }

    /**
     * {@code priorContext} is what the model may read but must not rule on — messages from
     * the same episode that have already been judged. Keeping them apart is what stops a
     * re-examined argument sanctioning the same message twice.
     */
    @Async
    public void judge(List<Message> window, List<Message> priorContext) {
        if (window.isEmpty()) return;

        var rules = ruleSet.activeRules();
        if (rules.isEmpty()) {
            log.warn("No active rules — {} messages went unjudged", window.size());
            return;
        }

        long chatId = window.get(0).getChatId();
        Map<Long, Message> byMessageId = new LinkedHashMap<>();
        List<StoredMessage> candidates = new ArrayList<>();
        for (Message msg : window) {
            Optional<StoredMessage> stored = messageStore.find(chatId, msg.getMessageId());
            // @Async swallows whatever is thrown here, so a miss is logged, not raised.
            if (stored.isEmpty()) {
                log.warn("Message {} was not persisted before judging — skipping", msg.getMessageId());
                continue;
            }
            byMessageId.put((long) msg.getMessageId(), msg);
            candidates.add(stored.get());
        }
        if (candidates.isEmpty()) return;

        List<StoredMessage> extendedHistory = extendedHistory(chatId, candidates);
        metrics.recordJudged(!extendedHistory.isEmpty(), candidates.size());

        JudgmentInput input = new JudgmentInput(
                groupKnowledge.roster(chatId),
                groupKnowledge.dossier(chatId).orElse(null),
                rules,
                ruleSet.examplesByRule(),
                candidates,
                contextFor(chatId, priorContext),
                profilesOf(candidates),
                extendedHistory);

        List<Violation> violations;
        try {
            violations = judge.judge(input);
        } catch (LlmException e) {
            // Already reported by HealthMonitor; never act on a failed judgment.
            log.warn("Skipping a window of {} messages — judgment unavailable", candidates.size());
            return;
        }

        List<Violation> actionable = worstPerSender(violations, byMessageId);
        digest.recordWindow(candidates.size(), actionable.size());

        // A "nothing happened" line per window is noise, not information: it says only
        // that the bot is alive, once a minute, forever. Rolled into an hourly digest.
        if (actionable.isEmpty()) return;

        for (Violation violation : actionable) {
            Message msg = byMessageId.get(violation.messageId());
            Judgment judgment = new Judgment(true, violation.ruleId(),
                    violation.confidence(), violation.reasoning());
            router.route(msg, judgment, escalation.nextAction(msg.getFrom().getId()));
        }
    }

    /**
     * One sanction per person per window. Several violations in the same minute is one
     * episode, not a climb up the ladder — so the strongest stands and the rest are
     * reported with it rather than acted on separately.
     */
    private List<Violation> worstPerSender(List<Violation> violations, Map<Long, Message> byId) {
        Map<Long, Violation> strongest = new LinkedHashMap<>();
        for (Violation v : violations) {
            Message msg = byId.get(v.messageId());
            if (msg == null) continue;
            strongest.merge(msg.getFrom().getId(), v,
                    (a, b) -> a.confidence() >= b.confidence() ? a : b);
        }
        return new ArrayList<>(strongest.values());
    }

    private List<StoredMessage> contextFor(long chatId, List<Message> priorContext) {
        List<StoredMessage> context = new ArrayList<>(messageStore.contextWindow(chatId));
        for (Message msg : priorContext) {
            messageStore.find(chatId, msg.getMessageId()).ifPresent(context::add);
        }
        return context.stream().distinct().toList();
    }

    private Map<Long, String> profilesOf(List<StoredMessage> candidates) {
        Map<Long, String> byUser = new HashMap<>();
        for (StoredMessage m : candidates) {
            byUser.computeIfAbsent(m.getSenderId(), profiles::profileFor);
            if (m.getReplyToUserId() != null) {
                byUser.computeIfAbsent(m.getReplyToUserId(), profiles::profileFor);
            }
        }
        byUser.values().removeIf(java.util.Objects::isNull);
        return byUser;
    }

    /**
     * The expensive history lookup still only runs when a weak signal already suggests a
     * pattern, mirroring how a human admin checks back only when something feels off.
     */
    private List<StoredMessage> extendedHistory(long chatId, List<StoredMessage> candidates) {
        if (!ruleSet.anyRequiresHistory()) return List.of();

        List<StoredMessage> history = new ArrayList<>();
        for (StoredMessage m : candidates) {
            Long targetId = m.getReplyToUserId();
            if (targetId == null || targetId == m.getSenderId()) continue;

            int count = profiles.negativeInteractionCount(m.getSenderId(), targetId);
            if (count < props.patternDetection().negativeInteractionThreshold()) continue;

            history.addAll(messageStore.interactions(
                    chatId, m.getSenderId(), targetId, props.patternDetection().windowDays()));
        }
        return history.stream()
                .distinct()
                .sorted(Comparator.comparing(StoredMessage::getSentAt))
                .toList();
    }
}
