package com.sevino.asistente.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cliente HTTP para hablar con un servidor Ollama local.
 *
 * Usa el endpoint /api/chat con stream=false (respuesta completa de una sola vez).
 * Las peticiones se ejecutan en un thread pool dedicado para no bloquear el hilo
 * principal de Minecraft.
 */
public final class OllamaClient {

    static {
        // Forzar IPv4 al resolver "localhost" (Ollama por defecto solo escucha en 127.0.0.1).
        // Esto evita el clasico "Connection refused" cuando Java elige ::1.
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    private static final Gson GSON = new Gson();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SevinoAsistente-Ollama-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(EXECUTOR)
            .build();

    /**
     * Mensaje del historial de chat con Ollama.
     */
    public record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }

    private OllamaClient() {}

    /**
     * Envia un prompt simple sin historial (system + user) y devuelve el texto.
     */
    public static CompletableFuture<String> chat(String systemPrompt, String userPrompt) {
        List<Message> msgs = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            msgs.add(Message.system(systemPrompt));
        }
        msgs.add(Message.user(userPrompt));
        return chat(msgs);
    }

    /**
     * Envia un historial completo de mensajes a Ollama y devuelve la respuesta del asistente.
     */
    public static CompletableFuture<String> chat(List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> doChat(messages), EXECUTOR);
    }

    private static String doChat(List<Message> messages) {
        String baseUrl = AsistenteConfig.OLLAMA_URL.get();
        String model = AsistenteConfig.OLLAMA_MODEL.get();
        int timeoutSec = AsistenteConfig.OLLAMA_TIMEOUT_SECONDS.get();
        int maxTokens = AsistenteConfig.OLLAMA_MAX_TOKENS.get();
        double temperature = AsistenteConfig.OLLAMA_TEMPERATURE.get();

        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", false);

            com.google.gson.JsonArray msgArr = new com.google.gson.JsonArray();
            for (Message m : messages) {
                JsonObject jm = new JsonObject();
                jm.addProperty("role", m.role());
                jm.addProperty("content", m.content());
                msgArr.add(jm);
            }
            body.add("messages", msgArr);

            JsonObject options = new JsonObject();
            options.addProperty("temperature", temperature);
            options.addProperty("num_predict", maxTokens);
            body.add("options", options);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(baseUrl) + "/api/chat"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() / 100 != 2) {
                SevinoAsistente.LOGGER.warn("[Ollama] Status {} respuesta {}", resp.statusCode(), resp.body());
                return "[Ollama] Error " + resp.statusCode() + ": " + truncate(resp.body(), 200);
            }

            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            if (json == null) {
                return "[Ollama] Respuesta vacia.";
            }

            // Formato /api/chat: { "message": { "role": "assistant", "content": "..." }, ... }
            if (json.has("message") && json.get("message").isJsonObject()) {
                JsonObject msg = json.getAsJsonObject("message");
                if (msg.has("content")) {
                    return msg.get("content").getAsString().trim();
                }
            }

            // Fallback por si la API devuelve "response" (formato /api/generate)
            if (json.has("response")) {
                return json.get("response").getAsString().trim();
            }

            return "[Ollama] Formato de respuesta inesperado.";
        } catch (java.net.ConnectException ce) {
            SevinoAsistente.LOGGER.warn("[Ollama] No se pudo conectar: {}", ce.getMessage());
            return "[Ollama] No se pudo conectar al servidor en " + baseUrl
                    + ". Asegurate de que Ollama este corriendo (`ollama serve`).";
        } catch (java.net.http.HttpTimeoutException te) {
            return "[Ollama] La respuesta tardo demasiado (timeout " + timeoutSec + "s).";
        } catch (Exception e) {
            SevinoAsistente.LOGGER.error("[Ollama] Error inesperado", e);
            return "[Ollama] Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
