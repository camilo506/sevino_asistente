package com.sevino.asistente.voice;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TTS: StreamElements primero; si falla (401, red, etc.), respaldo no oficial de Google translate_tts.
 */
public final class SevinoTTSClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Sevino-TTS-Worker");
        t.setDaemon(true);
        return t;
    });

    /** Limite para URLs GET (StreamElements y Google). */
    private static final int MAX_TTS_CHARS = 1800;

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private SevinoTTSClient() {}

    public static CompletableFuture<byte[]> speak(String text) {
        return CompletableFuture.supplyAsync(() -> fetchSpeechBytes(text), EXECUTOR);
    }

    private static byte[] fetchSpeechBytes(String text) {
        if (text == null || text.isBlank()) return null;
        String clipped = text.length() > MAX_TTS_CHARS ? text.substring(0, MAX_TTS_CHARS) : text;

        byte[] se = tryStreamElements(clipped);
        if (se != null && se.length > 0) return se;

        LOGGER.warn("[Sevino TTS] StreamElements no disponible; probando respaldo Google.");
        byte[] g = tryGoogleTranslateTts(clipped);
        if (g != null && g.length > 0) return g;

        return null;
    }

    private static void applyBrowserHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty("Accept", "audio/mpeg,audio/*,*/*;q=0.9");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,es;q=0.8");
    }

    private static byte[] tryStreamElements(String text) {
        HttpURLConnection conn = null;
        try {
            String voice = "Brian";
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = "https://api.streamelements.com/kappa/v2/speech?voice=" + voice + "&text=" + encodedText;

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            applyBrowserHeaders(conn);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(45000);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.warn("[Sevino TTS] StreamElements HTTP {}", code);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) bos.write(buffer, 0, n);
                byte[] audio = bos.toByteArray();
                if (audio.length == 0) LOGGER.warn("[Sevino TTS] StreamElements respuesta vacia");
                return audio;
            }
        } catch (Exception e) {
            LOGGER.warn("[Sevino TTS] StreamElements: {}", e.toString());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Endpoint historico usado por muchos clientes; puede cambiar o limitarse por Google.
     */
    private static byte[] tryGoogleTranslateTts(String text) {
        HttpURLConnection conn = null;
        try {
            int n = Math.min(200, text.length());
            String q = URLEncoder.encode(text.substring(0, n), StandardCharsets.UTF_8);
            String urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=en&q=" + q;

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            applyBrowserHeaders(conn);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(45000);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.warn("[Sevino TTS] Google TTS HTTP {}", code);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int r;
                while ((r = is.read(buffer)) != -1) bos.write(buffer, 0, r);
                byte[] audio = bos.toByteArray();
                if (audio.length < 100) {
                    LOGGER.warn("[Sevino TTS] Google TTS respuesta demasiado pequena ({} bytes)", audio.length);
                    return null;
                }
                return audio;
            }
        } catch (Exception e) {
            LOGGER.warn("[Sevino TTS] Google TTS: {}", e.toString());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
