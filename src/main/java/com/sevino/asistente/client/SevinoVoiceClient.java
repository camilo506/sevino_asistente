package com.sevino.asistente.client;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteClientConfig;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.voice.SevinoAudioPlayer;
import com.sevino.asistente.voice.SevinoTTSClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.network.chat.Component;

/**
 * Reproduce lo que dice Sevino en el cliente: TTS (MP3) y/o narrador de Minecraft.
 * El audio TTS se aplica en el hilo del juego ({@link Minecraft#execute}) para evitar fallos silenciosos.
 */
public final class SevinoVoiceClient {

    private SevinoVoiceClient() {}

    public static void onVoicePacket(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) return;

        final Minecraft mc = Minecraft.getInstance();
        String spoken = buildNarrationText(rawReply);
        if (spoken.isBlank()) {
            spoken = rawReply.replace('\r', ' ').replace('\n', ' ').trim();
        }
        if (spoken.isBlank()) return;

        SevinoAsistente.LOGGER.info("[Sevino Voz] Paquete recibido ({} caracteres para voz).", spoken.length());

        final boolean wantStream = AsistenteClientConfig.DOWNLOAD_VOICE_MP3.get();
        final boolean narratorOn = mc.options.narrator().get() != NarratorStatus.OFF;
        final String spokenFinal = spoken;
        final String rawFinal = rawReply;

        if (wantStream) {
            SevinoTTSClient.speak(rawFinal).thenAccept(audio ->
                    mc.execute(() -> applyStreamResult(mc, audio, spokenFinal, narratorOn)));
        } else if (narratorOn) {
            mc.execute(() -> mc.getNarrator().sayNow(spokenFinal));
        } else {
            SevinoAsistente.LOGGER.warn("[Sevino Voz] downloadVoiceMp3=false y narrador apagado; intentando TTS por altavoces.");
            SevinoTTSClient.speak(rawFinal).thenAccept(audio ->
                    mc.execute(() -> applyStreamResult(mc, audio, spokenFinal, false)));
        }
    }

    private static void applyStreamResult(Minecraft mc, byte[] audio, String spoken, boolean narratorOn) {
        if (audio != null && audio.length > 0) {
            SevinoAsistente.LOGGER.info("[Sevino Voz] Audio TTS listo ({} bytes); iniciando reproduccion.", audio.length);
            SevinoAudioPlayer.playTts(audio);
        } else if (narratorOn) {
            SevinoAsistente.LOGGER.warn("[Sevino Voz] StreamElements sin audio; usando narrador.");
            mc.getNarrator().sayNow(spoken);
        } else {
            SevinoAsistente.LOGGER.error(
                    "[Sevino Voz] Sin audio: TTS fallo y el narrador esta apagado. Opciones > Accesibilidad > Narrador, o pon voice.downloadVoiceMp3=true en sevinoasistente-client.toml");
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("\u00a7e[Sevino]\u00a7r Activa el \u00a7lNarrador\u00a7r (Accesibilidad) o revisa internet / TTS."),
                        true);
            }
            try {
                mc.getNarrator().sayNow(spoken);
            } catch (Throwable ignored) {
                // ultimo intento si el narrador ignora el modo OFF en algunas plataformas
            }
        }
    }

    private static String buildNarrationText(String reply) {
        if (!AsistenteConfig.NARRATE_ONLY_ENGLISH.get()) {
            return reply.replace('\r', ' ').replace('\n', ' ').trim();
        }
        StringBuilder sb = new StringBuilder();
        for (String line : reply.split("\\R")) {
            if (line.isBlank()) continue;
            String part = line.contains(" / ") ? line.split(" / ", 2)[0].trim() : line.trim();
            if (!sb.isEmpty()) sb.append(". ");
            sb.append(part);
        }
        return sb.toString().trim();
    }
}
