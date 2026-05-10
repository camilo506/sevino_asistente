package com.sevino.asistente.ollama;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sevino.asistente.config.AsistenteConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente para Groq usando HttpURLConnection (Método clásico para evitar
 * bloqueos).
 */
public final class GroqClient {

    private static final Gson GSON = new Gson();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Sevino-Worker");
        t.setDaemon(true);
        return t;
    });

    private GroqClient() {
    }

    public static CompletableFuture<String> transcribe(byte[] audioData) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String apiKey = AsistenteConfig.GROQ_API_KEY.get();
                String model = AsistenteConfig.WHISPER_MODEL.get();
                if (apiKey == null || apiKey.isEmpty())
                    return "[Error] API Key faltante.";

                URL url = new URL("https://api.groq.com/openai/v1/audio/transcriptions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                String boundary = "SevinoBoundary";
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream();
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8),
                                true)) {

                    // Model field
                    writer.println("--" + boundary);
                    writer.println("Content-Disposition: form-data; name=\"model\"");
                    writer.println();
                    writer.println(model);

                    // File field
                    writer.println("--" + boundary);
                    writer.println("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"");
                    writer.println("Content-Type: audio/wav");
                    writer.println();
                    writer.flush();
                    os.write(audioData);
                    os.flush();
                    writer.println();
                    writer.println("--" + boundary + "--");
                }

                int code = conn.getResponseCode();
                InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        resp.append(line);

                    if (code != 200)
                        return "[Error STT] " + code;
                    JsonObject json = GSON.fromJson(resp.toString(), JsonObject.class);
                    return json.has("text") ? json.get("text").getAsString() : "";
                }
            } catch (Exception e) {
                return "[Error Red] " + e.getMessage();
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<String> chat(List<OllamaClient.Message> messages) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String apiKey = AsistenteConfig.GROQ_API_KEY.get();
                String model = AsistenteConfig.GROQ_MODEL.get();
                if (apiKey == null || apiKey.isEmpty())
                    return "[Error] API Key faltante.";

                URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

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

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        resp.append(line);

                    if (code != 200)
                        return "[Error LLM] " + code;
                    JsonObject json = GSON.fromJson(resp.toString(), JsonObject.class);
                    return json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .get("message").getAsJsonObject()
                            .get("content").getAsString();
                }
            } catch (Exception e) {
                return "[Error Red] " + e.getMessage();
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        }, EXECUTOR);
    }
}
