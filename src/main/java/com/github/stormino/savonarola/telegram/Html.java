package com.github.stormino.savonarola.telegram;

/**
 * Admin notifications are sent with parseMode=HTML, so every interpolated value has
 * to be escaped. Most of them are untrusted: message text is whatever a member wrote,
 * and reasoning is whatever the model wrote. An unescaped '<' makes Telegram reject
 * the whole send, which would silently drop the verdict the admins are waiting for.
 */
public final class Html {

    private Html() {}

    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}
