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
    /** Nombre de voz en la API StreamElements (Enrique/Miguel = masculino español; Lupe/Conchita = femenino). */
    public static final ForgeConfigSpec.ConfigValue<String> STREAM_ELEMENTS_VOICE;
    /** Codigo BCP-47 corto para el respaldo Google translate_tts (es, en, …). */
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
                .comment(
                        "Voz StreamElements (kappa v2/speech). Por defecto Enrique (masculino, castellano).",
                        "Miguel = masculino espanol US. Si suena a mujer, suele ser el respaldo Google: revisa el log [Sevino TTS].")
                .define("streamElementsVoice", "Enrique");
        GOOGLE_TTS_LANG = b
                .comment(
                        "Idioma del respaldo Google si StreamElements falla.",
                        "Con es se prueba es-us y luego es (Google no permite elegir sexo; solo una voz por idioma).")
                .define("googleTtsLang", "es");
        b.pop();
        SPEC = b.build();
    }

    private AsistenteClientConfig() {}
}
