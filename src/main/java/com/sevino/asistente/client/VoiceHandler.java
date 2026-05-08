package com.sevino.asistente.client;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Escucha los mensajes de chat y activa el Narrador si el mensaje es de Sevino.
 */
@Mod.EventBusSubscriber(modid = SevinoAsistente.MOD_ID, value = Dist.CLIENT)
public class VoiceHandler {

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent.System event) {
        String text = event.getMessage().getString();
        
        // Si el mensaje viene con el tag de Sevino, lo narramos
        if (text.startsWith("[Sevino]")) {
            String content = text.replace("[Sevino]", "").trim();
            if (!content.isEmpty() && !content.contains("thinking...") && !content.contains("pensando...")) {
                
                if (AsistenteConfig.NARRATE_ONLY_ENGLISH.get()) {
                    // Si el mensaje tiene el formato "Ingles / Español", nos quedamos con el Ingles
                    String[] parts = content.split(" / ");
                    if (parts.length > 0) {
                        content = parts[0].trim();
                    }
                }
                
                narrate(content);
            }
        }
    }

    public static void narrate(String text) {
        // Usamos el Narrador nativo de Minecraft (1.20.1)
        Minecraft.getInstance().getNarrator().sayNow(text);
    }
}
