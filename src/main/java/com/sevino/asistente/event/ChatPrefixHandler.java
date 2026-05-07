package com.sevino.asistente.event;

import com.sevino.asistente.chat.AssistantConversation;
import com.sevino.asistente.config.AsistenteConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Intercepta mensajes del chat que empiezan con uno de los prefijos configurados
 * y los redirige al asistente. Cancela el mensaje original para que no se publique
 * a otros jugadores.
 */
public class ChatPrefixHandler {

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        if (message == null || message.isBlank()) return;
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        String chatPrefix = AsistenteConfig.CHAT_PREFIX.get();
        String trPrefix = AsistenteConfig.TRANSLATE_PREFIX.get();
        String enPrefix = AsistenteConfig.CORRECT_PREFIX.get();

        String trimmed = message.strip();

        if (matches(trimmed, chatPrefix)) {
            String body = stripPrefix(trimmed, chatPrefix);
            if (body.isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[Sevino] Uso: " + chatPrefix + " <mensaje>"));
            } else {
                AssistantConversation.chat(player, body);
            }
            event.setCanceled(true);
            return;
        }

        if (matches(trimmed, trPrefix)) {
            String body = stripPrefix(trimmed, trPrefix);
            if (body.isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[Sevino] Uso: " + trPrefix + " <texto a traducir>"));
            } else {
                AssistantConversation.translate(player, body);
            }
            event.setCanceled(true);
            return;
        }

        if (matches(trimmed, enPrefix) && AsistenteConfig.ALLOW_AUTO_CORRECT.get()) {
            String body = stripPrefix(trimmed, enPrefix);
            if (body.isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[Sevino] Uso: " + enPrefix + " <frase en ingles>"));
            } else {
                AssistantConversation.correct(player, body);
            }
            event.setCanceled(true);
        }
    }

    private static boolean matches(String input, String prefix) {
        if (prefix == null || prefix.isBlank()) return false;
        if (input.equalsIgnoreCase(prefix)) return true;
        return input.length() > prefix.length()
                && input.regionMatches(true, 0, prefix, 0, prefix.length())
                && Character.isWhitespace(input.charAt(prefix.length()));
    }

    private static String stripPrefix(String input, String prefix) {
        if (input.length() <= prefix.length()) return "";
        return input.substring(prefix.length()).strip();
    }
}
