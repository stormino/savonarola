package com.github.stormino.savonarola.telegram;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandDispatcher {

    private final Map<String, AdminCommand> commands;
    private final AdminRegistry adminRegistry;
    private final AdminNotifier notifier;
    private final SavonarolaProperties props;

    public CommandDispatcher(List<AdminCommand> commands,
                             AdminRegistry adminRegistry,
                             AdminNotifier notifier,
                             SavonarolaProperties props) {
        this.commands = commands.stream()
                .collect(Collectors.toMap(AdminCommand::name, Function.identity()));
        this.adminRegistry = adminRegistry;
        this.notifier = notifier;
        this.props = props;
    }

    public void dispatch(Message msg) {
        // Authority comes from the main group, not from membership of the admin chat.
        if (!adminRegistry.isAdmin(props.telegram().mainChatId(), msg.getFrom().getId())) {
            return;
        }

        String[] parts = msg.getText().trim().split("\\s+");
        String name = parts[0].substring(1).toLowerCase();
        if (name.contains("@")) {
            name = name.substring(0, name.indexOf('@'));
        }
        List<String> args = Arrays.asList(parts).subList(1, parts.length);

        AdminCommand command = commands.get(name);
        if (command == null) {
            notifier.reply(msg, "Unknown command: " + name);
            return;
        }
        try {
            command.handle(msg, args);
        } catch (IllegalArgumentException e) {
            notifier.reply(msg, e.getMessage() + "\nUsage: " + command.usage());
        } catch (Exception e) {
            log.error("Command {} failed", name, e);
            notifier.reply(msg, "Command failed: " + e.getMessage());
        }
    }
}
