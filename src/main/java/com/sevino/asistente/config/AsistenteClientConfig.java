package com.sevino.asistente.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuracion solo del cliente ({@code config/sevinoasistente-client.toml}).
 * La voz depende del PC del jugador; asi no queda bloqueada por valores viejos en common.toml.
 */
public final class AsistenteClientConfig {

    public static final ForgeConfigSpec SPEC;
    /** Si true, descarga MP3 (StreamElements + respaldo) y reproduce por altavoces. */
    public static final ForgeConfigSpec.BooleanValue DOWNLOAD_VOICE_MP3;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Voz de Sevino (solo en tu maquina).").push("voice");
        DOWNLOAD_VOICE_MP3 = b
                .comment(
                        "true = descargar y reproducir voz MP3 (internet).",
                        "false = solo narrador de Minecraft (activa Accesibilidad > Narrador o Ctrl+B).")
                .define("downloadVoiceMp3", true);
        b.pop();
        SPEC = b.build();
    }

    private AsistenteClientConfig() {}
}
