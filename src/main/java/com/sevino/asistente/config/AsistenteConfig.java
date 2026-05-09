package com.sevino.asistente.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Configuracion del mod almacenada en config/sevinoasistente-common.toml
 *
 * Aqui se definen los parametros para conectar con Ollama, el prefijo de chat,
 * el nivel de ingles del jugador, y otras opciones del asistente.
 */
public final class AsistenteConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> GROQ_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> WHISPER_MODEL;

    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_URL;
    public static final ForgeConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ForgeConfigSpec.IntValue OLLAMA_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue OLLAMA_MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue OLLAMA_TEMPERATURE;

    public static final ForgeConfigSpec.ConfigValue<String> CHAT_PREFIX;
    public static final ForgeConfigSpec.ConfigValue<String> TRANSLATE_PREFIX;
    public static final ForgeConfigSpec.ConfigValue<String> CORRECT_PREFIX;

    public static final ForgeConfigSpec.ConfigValue<String> ENGLISH_LEVEL;
    public static final ForgeConfigSpec.BooleanValue INCLUDE_GAME_CONTEXT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_AUTO_CORRECT;
    public static final ForgeConfigSpec.BooleanValue NARRATE_ONLY_ENGLISH;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VOCAB_TOPICS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Configuracion general de IA.").push("general");
        AI_PROVIDER = b
                .comment("Proveedor de IA principal. Opciones: 'ollama' (local) o 'groq' (nube).")
                .define("aiProvider", "groq");
        b.pop();

        b.comment("Configuracion de Groq (Nube - Alta velocidad).").push("groq");
        GROQ_API_KEY = b
                .comment("Tu API Key de Groq (gsk_...).")
                .define("apiKey", "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");
        
        GROQ_MODEL = b
                .comment("Modelo de lenguaje de Groq. Ej: llama-3.1-8b-instant, llama-3.3-70b-versatile.")
                .define("model", "llama-3.1-8b-instant");

        WHISPER_MODEL = b
                .comment("Modelo de Speech-to-Text de Groq. Recomendado: whisper-large-v3-turbo.")
                .define("whisperModel", "whisper-large-v3-turbo");
        b.pop();

        b.comment("Conexion con Ollama (IA local).").push("ollama");

        OLLAMA_URL = b
                .comment("URL base del servidor Ollama. Por defecto el servidor local.",
                         "Se usa 127.0.0.1 en lugar de 'localhost' para forzar IPv4 y evitar problemas",
                         "cuando Ollama solo escucha en IPv4 mientras Java intenta IPv6 (::1).")
                .define("url", "http://127.0.0.1:11434");

        OLLAMA_MODEL = b
                .comment("Modelo a usar. RECOMENDADOS: llama3.1 (8B), llama3.2 (3B), qwen2.5 (7B).")
                .define("model", "llama3.1");

        OLLAMA_TIMEOUT_SECONDS = b
                .comment("Timeout en segundos para cada peticion HTTP a Ollama.")
                .defineInRange("timeoutSeconds", 60, 5, 600);

        OLLAMA_MAX_TOKENS = b
                .comment("Maximo de tokens en la respuesta del modelo (num_predict).")
                .defineInRange("maxTokens", 256, 16, 4096);

        OLLAMA_TEMPERATURE = b
                .comment("Temperatura del modelo (0.0 = deterministico, 1.0 = creativo).")
                .defineInRange("temperature", 0.7, 0.0, 2.0);

        b.pop();

        b.comment("Prefijos para interactuar con el asistente desde el chat.").push("chat");

        CHAT_PREFIX = b
                .comment("Prefijo de chat para conversar con el asistente. Ej: '!ai hola'.")
                .define("prefix", "!ai");

        TRANSLATE_PREFIX = b
                .comment("Prefijo de chat para traducir rapidamente. Ej: '!tr cat'.")
                .define("translatePrefix", "!tr");

        CORRECT_PREFIX = b
                .comment("Prefijo de chat para que el asistente corrija una frase en ingles. Ej: '!en I has a cat'.")
                .define("correctPrefix", "!en");

        b.pop();

        b.comment("Comportamiento del asistente.").push("assistant");

        ENGLISH_LEVEL = b
                .comment("Nivel de ingles del jugador. Valores: beginner, intermediate, advanced.")
                .define("englishLevel", "beginner",
                        o -> o instanceof String s && (s.equals("beginner") || s.equals("intermediate") || s.equals("advanced")));

        INCLUDE_GAME_CONTEXT = b
                .comment("Si es true, el asistente recibe informacion del estado del juego (bioma, hotbar, bloque mirado, hora).")
                .define("includeGameContext", true);

        ALLOW_AUTO_CORRECT = b
                .comment("Si es true, el asistente puede sugerir correcciones cuando detecte errores en mensajes en ingles.")
                .define("allowAutoCorrect", true);

        NARRATE_ONLY_ENGLISH = b
                .comment("Si es true, el narrador solo leera la parte en ingles de la respuesta.")
                .define("narrateOnlyEnglish", false);

        VOCAB_TOPICS = b
                .comment("Temas para el vocabulario del dia.")
                .defineList("vocabTopics",
                        Arrays.asList("mining", "farming", "animals", "food", "weather", "directions", "tools", 
                                      "combat", "building", "travel", "cooking", "emotions", "technology", "village"),
                        o -> o instanceof String s && !s.isBlank());

        b.pop();

        SPEC = b.build();
    }

    private AsistenteConfig() {}
}
