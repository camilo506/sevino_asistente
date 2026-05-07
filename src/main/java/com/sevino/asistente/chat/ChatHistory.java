package com.sevino.asistente.chat;

import com.sevino.asistente.ollama.OllamaClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene un historial corto por jugador para conversaciones multi-turn con el asistente.
 * Solo guarda los ultimos N mensajes para no inflar el prompt.
 */
public final class ChatHistory {

    private static final int MAX_TURNS = 6; // 6 pares user/assistant

    private static final Map<UUID, Deque<OllamaClient.Message>> HISTORY = new ConcurrentHashMap<>();

    private ChatHistory() {}

    public static List<OllamaClient.Message> get(UUID player) {
        Deque<OllamaClient.Message> deque = HISTORY.get(player);
        if (deque == null) return List.of();
        return new ArrayList<>(deque);
    }

    public static void append(UUID player, OllamaClient.Message msg) {
        Deque<OllamaClient.Message> deque = HISTORY.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(msg);
            while (deque.size() > MAX_TURNS * 2) {
                deque.pollFirst();
            }
        }
    }

    public static void clear(UUID player) {
        HISTORY.remove(player);
    }
}
