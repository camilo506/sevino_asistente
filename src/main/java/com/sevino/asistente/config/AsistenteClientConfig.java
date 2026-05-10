package com.sevino.asistente.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuracion solo del cliente ({@code config/sevinoasistente-client.toml}).
 * La voz depende del PC del jugador; asi no queda bloqueada por valores viejos en common.toml.
 */
public final class AsistenteClientConfig {

    public static final ForgeConfigSpec SPEC;
    /** Si true, descarga MP3 (internet) y reproduce por altavoces. */
    public static final ForgeConfigSpec.BooleanValue DOWNLOAD_VOICE_MP3;
    /**
     * Voz StreamElements (solo si Google TTS falla). Ejemplos: Miguel, Enrique, Brian, etc.
     */
    public static final ForgeConfigSpec.ConfigValue<String> STREAM_ELEMENTS_VOICE;
    /**
     * Codigo de idioma BCP-47 para Google translate_tts (principal): {@code es} para español.
     */
    public static final ForgeConfigSpec.ConfigValue<String> GOOGLE_TTS_LANG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Voz de Sevino (solo en tu maquina).").push("voice");
        DOWNLOAD_VOICE_MP3 = b
                .comment(
                        "true = descargar y reproducir voz MP3 (internet).",
                        "false = solo narrador de Minecraft (activa Accesibilidad > Narrador o Ctrl+B).")
                .define("downloadVoiceMp3", true);
        STREAM_ELEMENTS_VOICE = b
                .comment("Voz StreamElements (respaldo). El audio principal sale de Google TTS con googleTtsLang.")
                .define("streamElementsVoice", "Miguel");
        GOOGLE_TTS_LANG = b
                .comment("Idioma principal del TTS (Google). Recomendado es para español.")
                .define("googleTtsLang", "es");
        b.pop();
        SPEC = b.build();
    }

    private AsistenteClientConfig() {}
}
