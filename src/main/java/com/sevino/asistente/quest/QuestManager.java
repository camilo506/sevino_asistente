package com.sevino.asistente.quest;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.ollama.OllamaClient;
import com.sevino.asistente.ollama.PromptBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Misiones cortas en ingles generadas por Ollama. Soportamos un patron de mision
 * "COLLECT N item" como demo: cuando el LLM emite ese formato, se trackea el
 * progreso del jugador y se le notifica al completarla.
 */
public final class QuestManager {

    private static final Pattern COLLECT_PATTERN =
            Pattern.compile("(?i)\\bCOLLECT\\s+(\\d+)\\s+([a-z_][a-z_0-9]*)");

    private static final Map<UUID, Quest> ACTIVE = new ConcurrentHashMap<>();

    private QuestManager() {}

    public record Quest(String questEN, String questES, String itemId, int target) {}

    public static void requestNewQuest(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Sevino] thinking of a quest... / pensando en una mision..."));
        String level = AsistenteConfig.ENGLISH_LEVEL.get();
        String system = PromptBuilder.buildQuestSystemPrompt(level);
        String userCtx = "Player context: " + PromptBuilder.buildGameContext(player);

        OllamaClient.chat(system, userCtx).whenComplete((reply, ex) -> {
            player.server.execute(() -> {
                if (ex != null || reply == null || reply.isBlank()) {
                    player.sendSystemMessage(Component.literal(
                            "[Sevino quest] Error al generar mision: "
                                    + (ex != null ? ex.getMessage() : "vacio")));
                    return;
                }
                handleQuest(player, reply);
            });
        });
    }

    private static void handleQuest(ServerPlayer player, String reply) {
        String[] lines = reply.split("\\R", -1);
        String en = lines.length > 0 ? lines[0].trim() : "";
        String es = lines.length > 1 ? lines[1].trim() : "";

        if (en.isBlank()) {
            player.sendSystemMessage(Component.literal("[Sevino quest] " + reply));
            return;
        }

        Matcher m = COLLECT_PATTERN.matcher(en);
        if (m.find()) {
            int target = Integer.parseInt(m.group(1));
            String itemHint = m.group(2).toLowerCase();
            String itemId = resolveItem(itemHint);
            Quest q = new Quest(en, es, itemId, target);
            ACTIVE.put(player.getUUID(), q);
            player.sendSystemMessage(Component.literal("[Sevino quest] " + en));
            if (!es.isBlank()) player.sendSystemMessage(Component.literal("[Sevino quest] " + es));
            player.sendSystemMessage(Component.literal(
                    "[Sevino quest] Trackeando: " + target + "x " + itemId
                            + ". Use /sevino quest progress."));
        } else {
            player.sendSystemMessage(Component.literal("[Sevino quest] " + en));
            if (!es.isBlank()) player.sendSystemMessage(Component.literal("[Sevino quest] " + es));
            ACTIVE.remove(player.getUUID());
        }
    }

    /**
     * Llamado cada cierto tiempo (por ejemplo desde un PlayerTickEvent) para verificar
     * si la mision activa esta completa segun el inventario del jugador.
     */
    public static void tick(ServerPlayer player) {
        Quest q = ACTIVE.get(player.getUUID());
        if (q == null) return;

        int count = countItem(player, q.itemId());
        if (count >= q.target()) {
            ACTIVE.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("[Sevino quest] DONE! / Hecho! +1 XP del idioma :)"));
            player.sendSystemMessage(Component.literal("[Sevino quest] " + q.questEN()));
        }
    }

    private static int countItem(ServerPlayer player, String itemId) {
        net.minecraft.resources.ResourceLocation rl = parseId(itemId);
        if (rl == null) return 0;
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return 0;
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static String resolveItem(String hint) {
        net.minecraft.resources.ResourceLocation rl = parseId(hint);
        if (rl == null) {
            // tratar como nombre simple en namespace minecraft
            rl = new net.minecraft.resources.ResourceLocation("minecraft", hint);
        }
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            SevinoAsistente.LOGGER.warn("[Sevino quest] item desconocido del LLM: {}", hint);
            return "minecraft:dirt";
        }
        return rl.toString();
    }

    private static net.minecraft.resources.ResourceLocation parseId(String s) {
        try {
            return new net.minecraft.resources.ResourceLocation(s.contains(":") ? s : "minecraft:" + s);
        } catch (Exception e) {
            return null;
        }
    }
}
