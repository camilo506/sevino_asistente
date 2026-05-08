package com.sevino.asistente.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteConfig;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente para la API de Groq Cloud.
 * Maneja tanto la transcripción de audio (Whisper) como el chat (LLM).
 */
public final class GroqClient {

    private static final Gson GSON = new Gson();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Sevino-Groq-" + UUID.randomUUID().toString().substring(0, 4));
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(EXECUTOR)
            .build();

    private GroqClient() {}

    /**
     * Transcribe audio (formato WAV/PCM) usando Groq Whisper.
     */
    public static CompletableFuture<String> transcribe(byte[] audioData) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = AsistenteConfig.GROQ_API_KEY.get();
            String model = AsistenteConfig.WHISPER_MODEL.get();

            if (apiKey == null || apiKey.isBlank()) return "[Groq] Error: API Key no configurada.";

            try {
                String boundary = "SevinoBoundary" + System.currentTimeMillis();
                byte[] boundaryBytes = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] finishBytes = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                
                // Campo "model"
                bos.write(boundaryBytes);
                bos.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                bos.write(model.getBytes(StandardCharsets.UTF_8));
                bos.write(crlf);

                // Campo "file"
                bos.write(boundaryBytes);
                bos.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".getBytes(StandardCharsets.UTF_8));
                bos.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                bos.write(audioData);
                bos.write(crlf);
                
                bos.write(finishBytes);

                byte[] body = bos.toByteArray();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                
                if (resp.statusCode() != 200) {
                    SevinoAsistente.LOGGER.error("[Groq STT] Error {}: {}", resp.statusCode(), resp.body());
                    return "[Groq STT] Error " + resp.statusCode();
                }

                JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                return json.has("text") ? json.get("text").getAsString() : "";

            } catch (Exception e) {
                SevinoAsistente.LOGGER.error("[Groq STT] Error inesperado", e);
                return "[Groq STT] Error: " + e.getMessage();
            }
        }, EXECUTOR);
    }

    /**
     * Chat completion usando Groq Llama 3.
     */
    public static CompletableFuture<String> chat(List<OllamaClient.Message> messages) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = AsistenteConfig.GROQ_API_KEY.get();
            String model = AsistenteConfig.GROQ_MODEL.get();

            if (apiKey == null || apiKey.isBlank()) return "[Groq] Error: API Key no configurada.";

            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                
                JsonArray msgArr = new JsonArray();
                for (OllamaClient.Message m : messages) {
                    JsonObject jm = new JsonObject();
                    jm.addProperty("role", m.role());
                    jm.addProperty("content", m.content());
                    msgArr.add(jm);
                }
                body.add("messages", msgArr);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    SevinoAsistente.LOGGER.error("[Groq LLM] Error {}: {}", resp.statusCode(), resp.body());
                    return "[Groq LLM] Error " + resp.statusCode();
                }

                JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                return json.get("choices").getAsJsonArray().get(0)
                        .getAsJsonObject().get("message")
                        .getAsJsonObject().get("content").getAsString();

            } catch (Exception e) {
                SevinoAsistente.LOGGER.error("[Groq LLM] Error inesperado", e);
                return "[Groq LLM] Error: " + e.getMessage();
            }
        }, EXECUTOR);
    }
}
