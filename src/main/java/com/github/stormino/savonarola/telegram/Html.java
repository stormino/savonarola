package com.github.stormino.savonarola.telegram;

/**
 * Admin notifications use parseMode=HTML and interpolate member text and model output.
 * One unescaped '<' makes Telegram reject the send, silently dropping the verdict.
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
