package com.sevino.asistente.voice;

import com.mojang.logging.LogUtils;
import com.sevino.asistente.config.AsistenteClientConfig;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TTS: primero Google translate_tts (mejor pronunciacion/detection del idioma {@code tl});
 * si falla, StreamElements kappa v2/speech con la voz configurada.
 */
public final class SevinoTTSClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Sevino-TTS-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_TTS_CHARS = 1800;

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String STREAMELEMENTS_SPEECH_V2 = "https://api.streamelements.com/kappa/v2/speech";

    private SevinoTTSClient() {}

    public static CompletableFuture<byte[]> speak(String text) {
        return CompletableFuture.supplyAsync(() -> fetchSpeechBytes(text), EXECUTOR);
    }

    private static byte[] fetchSpeechBytes(String text) {
        if (text == null || text.isBlank()) return null;
        String clipped = text.length() > MAX_TTS_CHARS ? text.substring(0, MAX_TTS_CHARS) : text;

        byte[] g = tryGoogleTranslateTtsAll(clipped, googleTtsLang());
        if (g != null && g.length > 0) {
            LOGGER.info("[Sevino TTS] Audio desde Google (tl configurado).");
            return g;
        }

        LOGGER.warn("[Sevino TTS] Google no disponible; probando StreamElements.");
        byte[] se = tryStreamElementsOnce(clipped, streamElementsVoice());
        if (se != null && se.length > 0) {
            LOGGER.info("[Sevino TTS] Audio desde StreamElements (voz {}).", streamElementsVoice());
            return se;
        }

        return null;
    }

    private static void applyGoogleHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty("Accept", "audio/mpeg,audio/*,*/*;q=0.9");
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8");
    }

    private static void applyStreamElementsHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", BROWSER_UA);
        conn.setRequestProperty("Accept", "audio/mpeg,audio/*,*/*;q=0.9");
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8");
        conn.setRequestProperty("Referer", "https://streamelements.com/");
    }

    private static String streamElementsVoice() {
        return sanitizeVoice(AsistenteClientConfig.STREAM_ELEMENTS_VOICE.get());
    }

    private static String googleTtsLang() {
        return sanitizeLang(AsistenteClientConfig.GOOGLE_TTS_LANG.get());
    }

    private static String sanitizeVoice(String raw) {
        if (raw == null) return "Miguel";
        String v = raw.trim();
        if (v.isEmpty()) return "Miguel";
        if (v.length() > 48) v = v.substring(0, 48);
        if (!v.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) return "Miguel";
        return v;
    }

    private static String sanitizeLang(String raw) {
        if (raw == null) return "es";
        String L = raw.trim().toLowerCase(Locale.ROOT);
        if (L.isEmpty()) return "es";
        if (L.length() > 12) L = L.substring(0, 12);
        if (!L.matches("[a-z]{2,3}([_-][a-z0-9]{2,8})?")) return "es";
        return L;
    }

    private static byte[] tryStreamElementsOnce(String text, String voice) {
        HttpURLConnection conn = null;
        try {
            String encodedVoice = URLEncoder.encode(voice, StandardCharsets.UTF_8);
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = STREAMELEMENTS_SPEECH_V2 + "?voice=" + encodedVoice + "&text=" + encodedText;

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            applyStreamElementsHeaders(conn);
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

    /** Para {@code es} prueba castellano generico y luego latin US. */
    private static byte[] tryGoogleTranslateTtsAll(String text, String primaryLang) {
        String sanitized = sanitizeLang(primaryLang);
        LinkedHashSet<String> langs = new LinkedHashSet<>();
        if ("es".equals(sanitized)) {
            langs.add("es");
            langs.add("es-us");
        } else {
            langs.add(sanitized);
        }
        for (String tl : langs) {
            byte[] audio = tryGoogleTranslateTtsOnce(text, tl);
            if (audio != null && audio.length >= 100) {
                return audio;
            }
        }
        return null;
    }

    private static byte[] tryGoogleTranslateTtsOnce(String text, String lang) {
        HttpURLConnection conn = null;
        try {
            int n = Math.min(200, text.length());
            String q = URLEncoder.encode(text.substring(0, n), StandardCharsets.UTF_8);
            String tl = URLEncoder.encode(lang, StandardCharsets.UTF_8);
            String urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" + tl + "&q=" + q;

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            applyGoogleHeaders(conn);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(45000);

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.warn("[Sevino TTS] Google TTS tl={} HTTP {}", lang, code);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int r;
                while ((r = is.read(buffer)) != -1) bos.write(buffer, 0, r);
                byte[] audio = bos.toByteArray();
                if (audio.length < 100) {
                    LOGGER.warn("[Sevino TTS] Google TTS tl={} respuesta demasiado pequena ({} bytes)", lang, audio.length);
                    return null;
                }
                return audio;
            }
        } catch (Exception e) {
            LOGGER.warn("[Sevino TTS] Google TTS tl={}: {}", lang, e.toString());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
