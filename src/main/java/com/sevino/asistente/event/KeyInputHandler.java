package com.sevino.asistente.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.client.AudioRecorder;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.ollama.GroqClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Gestiona la tecla de grabación y la interacción por voz en el cliente.
 */
public class KeyInputHandler {
    public static final String KEY_CATEGORY = "key.categories.sevino";
    public static final String KEY_VOICE_CHAT = "key.sevino.voice_chat";

    public static KeyMapping voiceKey;
    private static final AudioRecorder recorder = new AudioRecorder();

    @Mod.EventBusSubscriber(modid = SevinoAsistente.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            voiceKey = new KeyMapping(KEY_VOICE_CHAT, GLFW.GLFW_KEY_V, KEY_CATEGORY);
            event.register(voiceKey);
        }
    }

    @Mod.EventBusSubscriber(modid = SevinoAsistente.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (voiceKey == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            // Verificamos si la tecla pulsada es la de voz
            if (voiceKey.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode()))) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (!recorder.isRecording()) {
                        mc.player.displayClientMessage(Component.literal("§a[Grabando...]"), true);
                        recorder.start();
                    }
                } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                    if (recorder.isRecording()) {
                        byte[] audio = recorder.stop();
                        if (audio != null) {
                            mc.player.displayClientMessage(Component.literal("§e[Sevino está procesando...]"), true);
                            processVoice(audio);
                        } else {
                            // Limpiar barra si se soltó muy rápido
                            mc.player.displayClientMessage(Component.literal(""), true);
                        }
                    }
                }
            }
        }
    }

    private static void processVoice(byte[] audio) {
        GroqClient.transcribe(audio).thenAccept(text -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (text == null || text.isBlank() || text.startsWith("[Groq")) {
                mc.player.displayClientMessage(Component.literal("§c[Sevino] No entendí nada o hubo un error."), true);
                return;
            }

            mc.execute(() -> {
                // Ya no mostramos "Tú: [texto]" para evitar ver errores de pronunciación.
                String prefix = AsistenteConfig.CHAT_PREFIX.get();
                mc.player.connection.sendChat(prefix + " " + text);
            });
        });
    }
}
