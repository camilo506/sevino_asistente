package com.sevino.asistente.chat;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.ollama.GroqClient;
import com.sevino.asistente.ollama.OllamaClient;
import com.sevino.asistente.ollama.PromptBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Capa de alto nivel que junta historial + prompt + Ollama + envio del resultado al chat
 * del jugador. Las respuestas se envian de forma asincrona desde el thread pool del cliente
 * Ollama y se vuelven a programar en el thread del servidor para tocar entidades de forma segura.
 */
public final class AssistantConversation {

    private AssistantConversation() {}

    public static void chat(ServerPlayer player, String userMessage) {
        sendThinking(player);

        String system = PromptBuilder.buildSystemPrompt(player);
        List<OllamaClient.Message> history = new ArrayList<>();
        history.add(OllamaClient.Message.system(system));
        history.addAll(ChatHistory.get(player.getUUID()));
        history.add(OllamaClient.Message.user(userMessage));

        getChatReply(history).whenComplete((reply, ex) -> deliver(player, userMessage, reply, ex));
    }

    public static void translate(ServerPlayer player, String text) {
        sendThinking(player);
        List<OllamaClient.Message> msgs = List.of(
                OllamaClient.Message.system(PromptBuilder.buildTranslateSystemPrompt()),
                OllamaClient.Message.user(text)
        );
        getChatReply(msgs).whenComplete((reply, ex) -> deliverSimple(player, "[Sevino tr]", reply, ex));
    }

    public static void correct(ServerPlayer player, String text) {
        sendThinking(player);
        List<OllamaClient.Message> msgs = List.of(
                OllamaClient.Message.system(PromptBuilder.buildCorrectSystemPrompt()),
                OllamaClient.Message.user(text)
        );
        getChatReply(msgs).whenComplete((reply, ex) -> deliverSimple(player, "[Sevino fix]", reply, ex));
    }

    public static void vocab(ServerPlayer player, String topic) {
        sendThinking(player);
        String level = AsistenteConfig.ENGLISH_LEVEL.get();
        String system = PromptBuilder.buildVocabSystemPrompt(topic, level);
        List<OllamaClient.Message> msgs = List.of(
                OllamaClient.Message.system(system),
                OllamaClient.Message.user("Give me a vocabulary card now.")
        );
        getChatReply(msgs).whenComplete((reply, ex) -> deliverSimple(player, "[Sevino vocab/" + topic + "]", reply, ex));
    }

    public static void quest(ServerPlayer player) {
        sendThinking(player);
        String level = AsistenteConfig.ENGLISH_LEVEL.get();
        String system = PromptBuilder.buildQuestSystemPrompt(level);
        String userCtx = "Player context: " + PromptBuilder.buildGameContext(player);
        List<OllamaClient.Message> msgs = List.of(
                OllamaClient.Message.system(system),
                OllamaClient.Message.user(userCtx)
        );
        getChatReply(msgs).whenComplete((reply, ex) -> deliverSimple(player, "[Sevino quest]", reply, ex));
    }

    private static java.util.concurrent.CompletableFuture<String> getChatReply(List<OllamaClient.Message> messages) {
        String provider = AsistenteConfig.AI_PROVIDER.get();
        if ("groq".equalsIgnoreCase(provider)) {
            return GroqClient.chat(messages);
        } else {
            return OllamaClient.chat(messages);
        }
    }

    private static void sendThinking(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Sevino] thinking... / pensando..."));
    }

    private static void deliver(ServerPlayer player, String userMessage, String reply, Throwable ex) {
        player.server.execute(() -> {
            if (ex != null) {
                SevinoAsistente.LOGGER.error("[Sevino] error en chat", ex);
                player.sendSystemMessage(Component.literal("[Sevino] Error: " + ex.getMessage()));
                return;
            }
            if (reply == null || reply.isBlank()) {
                player.sendSystemMessage(Component.literal("[Sevino] (respuesta vacia)"));
                return;
            }

            ChatHistory.append(player.getUUID(), OllamaClient.Message.user(userMessage));
            ChatHistory.append(player.getUUID(), OllamaClient.Message.assistant(reply));

            for (String line : reply.split("\\R")) {
                if (line.isBlank()) continue;
                player.sendSystemMessage(Component.literal("[Sevino] " + line));
            }
        });
    }

    private static void deliverSimple(ServerPlayer player, String tag, String reply, Throwable ex) {
        player.server.execute(() -> {
            if (ex != null) {
                player.sendSystemMessage(Component.literal(tag + " Error: " + ex.getMessage()));
                return;
            }
            if (reply == null || reply.isBlank()) {
                player.sendSystemMessage(Component.literal(tag + " (vacio)"));
                return;
            }
            for (String line : reply.split("\\R")) {
                if (line.isBlank()) continue;
                player.sendSystemMessage(Component.literal(tag + " " + line));
            }
        });
    }
}
