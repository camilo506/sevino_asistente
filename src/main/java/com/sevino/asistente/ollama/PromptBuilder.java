package com.sevino.asistente.ollama;

import com.sevino.asistente.config.AsistenteConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Construye los prompts (system + user) que se envian a Ollama.
 *
 * El prompt del sistema describe la personalidad del asistente y el nivel de
 * ingles del jugador. Si la opcion {@code includeGameContext} esta activa,
 * tambien se incluye un resumen del estado del juego (bioma, hora, hotbar,
 * bloque al que mira el jugador, etc).
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    public static String buildSystemPrompt(Player player) {
        String level = AsistenteConfig.ENGLISH_LEVEL.get();

        StringBuilder sb = new StringBuilder();
        sb.append("You are 'Sevino', a friendly in-game English tutor inside Minecraft. ");
        sb.append("Your goal is to help the player learn English while they play. ");
        sb.append("Always reply in BOTH English (first) and Spanish (second), separated by ' / '. ");
        sb.append("Keep replies SHORT (1-3 sentences max), suitable for in-game chat. ");
        sb.append("Avoid markdown, bullet lists, or code blocks. Use plain text only.\n\n");

        switch (level) {
            case "advanced" -> sb.append("The player has an ADVANCED English level. Use natural, idiomatic English. ");
            case "intermediate" -> sb.append("The player has an INTERMEDIATE English level. Use simple, clear English with common vocabulary. ");
            default -> sb.append("The player is a BEGINNER. Use very simple English (basic verbs, present tense, common words). ");
        }
        sb.append("If the player writes broken English, gently correct it and explain briefly in Spanish.\n");

        if (AsistenteConfig.INCLUDE_GAME_CONTEXT.get() && player != null) {
            sb.append("\n--- Game context ---\n");
            sb.append(buildGameContext(player));
            sb.append("\nYou can refer to this context to teach vocabulary related to what the player sees.");
        }

        return sb.toString();
    }

    public static String buildGameContext(Player player) {
        if (player == null) return "(no context)";
        Level level = player.level();

        StringBuilder sb = new StringBuilder();

        // Hora del dia
        long time = level.getDayTime() % 24000L;
        String timeOfDay;
        if (time < 6000) timeOfDay = "morning";
        else if (time < 12000) timeOfDay = "noon";
        else if (time < 18000) timeOfDay = "evening";
        else timeOfDay = "night";
        sb.append("Time: ").append(timeOfDay).append(" (tick ").append(time).append("). ");

        // Clima
        if (level.isThundering()) sb.append("Weather: thunderstorm. ");
        else if (level.isRaining()) sb.append("Weather: raining. ");
        else sb.append("Weather: clear. ");

        // Bioma
        BlockPos pos = player.blockPosition();
        Optional<ResourceKey<Biome>> biomeKey = level.getBiome(pos).unwrapKey();
        biomeKey.ifPresent(rk -> sb.append("Biome: ").append(rk.location().getPath()).append(". "));

        // Dimension
        ResourceKey<Level> dim = level.dimension();
        sb.append("Dimension: ").append(dim.location().getPath()).append(". ");

        // Salud / hambre
        sb.append("HP: ").append((int) player.getHealth()).append("/").append((int) player.getMaxHealth()).append(". ");
        sb.append("Hunger: ").append(player.getFoodData().getFoodLevel()).append("/20. ");

        // Bloque al que mira
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            BlockState bs = level.getBlockState(bhr.getBlockPos());
            ResourceKey<net.minecraft.world.level.block.Block> blockKey = level.registryAccess()
                    .registryOrThrow(Registries.BLOCK)
                    .getResourceKey(bs.getBlock())
                    .orElse(null);
            if (blockKey != null) {
                sb.append("Looking at: ").append(blockKey.location().getPath()).append(". ");
            }
        }

        // Hotbar (no spammear: solo nombres unicos)
        java.util.LinkedHashSet<String> hotbar = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceKey<net.minecraft.world.item.Item> itemKey = level.registryAccess()
                        .registryOrThrow(Registries.ITEM)
                        .getResourceKey(stack.getItem())
                        .orElse(null);
                if (itemKey != null) hotbar.add(itemKey.location().getPath());
            }
        }
        if (!hotbar.isEmpty()) {
            sb.append("Hotbar: ").append(String.join(", ", hotbar)).append(". ");
        }

        return sb.toString();
    }

    /**
     * Prompt del sistema reducido para el comando de traduccion.
     */
    public static String buildTranslateSystemPrompt() {
        return "You are a concise EN<->ES translator. Detect the language of the input. " +
                "Reply ONLY with the translation, nothing else. No quotes, no explanations.";
    }

    /**
     * Prompt del sistema reducido para correccion gramatical.
     */
    public static String buildCorrectSystemPrompt() {
        return "You are an English grammar coach. The player will give you an English sentence. " +
                "Reply with two lines:\n" +
                "Line 1: the corrected sentence in English (or 'OK' if it was already correct).\n" +
                "Line 2: a very short Spanish explanation (max 15 words) of the main fix. " +
                "If the sentence was OK, write 'Sin errores.'.\n" +
                "Do not add anything else.";
    }

    /**
     * Prompt del sistema para una palabra de vocabulario.
     */
    public static String buildVocabSystemPrompt(String topic, String level) {
        return "You generate ONE short English vocabulary card about '" + topic + "' " +
                "for a " + level + " Minecraft player. Reply in EXACTLY 3 lines, no markdown:\n" +
                "Line 1: the English word.\n" +
                "Line 2: short English example sentence (max 8 words).\n" +
                "Line 3: Spanish translation of the word + sentence.";
    }

    /**
     * Prompt del sistema para una mision corta basada en el contexto.
     */
    public static String buildQuestSystemPrompt(String level) {
        return "You design SHORT in-game English mini-quests for a " + level + " Minecraft player. " +
                "Pick a simple objective using a Minecraft item (collect/build/find/cook). " +
                "Reply in EXACTLY 2 lines, no markdown:\n" +
                "Line 1: Quest in English (max 12 words), use uppercase verbs (COLLECT, BUILD, FIND, COOK, KILL).\n" +
                "Line 2: Spanish translation.";
    }
}
