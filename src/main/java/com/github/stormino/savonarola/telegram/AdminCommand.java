package com.github.stormino.savonarola.telegram;

import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

public interface AdminCommand {

    /** Command name without the leading slash, lowercase. */
    String name();

    String usage();

    void handle(Message msg, List<String> args);
}
