package com.github.stormino.savonarola.rulebook;

import com.github.stormino.savonarola.config.SavonarolaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/** Reads a text attachment, because a rulebook does not fit in Telegram's 4096-char message. */
@Component
@RequiredArgsConstructor
public class TelegramFileReader {

    private static final long MAX_BYTES = 256 * 1024;

    private final TelegramClient client;
    private final SavonarolaProperties props;
    private final RestClient http = RestClient.create();

    public String read(String fileId, Long declaredSize) {
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "File troppo grande: il massimo è " + (MAX_BYTES / 1024) + " KB.");
        }
        try {
            String path = client.execute(GetFile.builder().fileId(fileId).build()).getFilePath();
            byte[] body = http.get()
                    .uri("https://api.telegram.org/file/bot{token}/{path}",
                            props.telegram().token(), path)
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new IllegalArgumentException("Il file risulta vuoto.");
            }
            if (body.length > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "File troppo grande: il massimo è " + (MAX_BYTES / 1024) + " KB.");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Non sono riuscito a scaricare il file: " + e.getMessage());
        }
    }
}
