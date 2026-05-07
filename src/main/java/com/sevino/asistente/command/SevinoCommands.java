package com.sevino.asistente.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sevino.asistente.chat.AssistantConversation;
import com.sevino.asistente.chat.ChatHistory;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.entity.AssistantEntity;
import com.sevino.asistente.entity.ModEntities;
import com.sevino.asistente.quest.QuestManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Registra los comandos /sevino del mod.
 *
 * /sevino spawn        -> invoca al asistente al lado del jugador
 * /sevino despawn      -> elimina al asistente del jugador
 * /sevino tr <texto>   -> traduce
 * /sevino fix <frase>  -> corrige una frase en ingles
 * /sevino level <lv>   -> cambia el nivel de ingles del jugador
 * /sevino vocab        -> palabra de vocabulario
 * /sevino vocab <tema> -> palabra del tema indicado
 * /sevino quest        -> nueva mision
 * /sevino reset        -> limpia el historial de chat
 */
public class SevinoCommands {

    private static final SuggestionProvider<CommandSourceStack> LEVELS = (ctx, builder) -> {
        for (String s : List.of("beginner", "intermediate", "advanced")) builder.suggest(s);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TOPICS = (ctx, builder) -> {
        for (Object o : AsistenteConfig.VOCAB_TOPICS.get()) {
            if (o instanceof String s) builder.suggest(s);
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sevino")
                .requires(src -> true);

        root.then(Commands.literal("spawn").executes(SevinoCommands::cmdSpawn));
        root.then(Commands.literal("despawn").executes(SevinoCommands::cmdDespawn));

        root.then(Commands.literal("tr").then(
                Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AssistantConversation.translate(p, StringArgumentType.getString(ctx, "text"));
                            return 1;
                        })
        ));

        root.then(Commands.literal("fix").then(
                Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AssistantConversation.correct(p, StringArgumentType.getString(ctx, "text"));
                            return 1;
                        })
        ));

        root.then(Commands.literal("level").then(
                Commands.argument("level", StringArgumentType.word()).suggests(LEVELS)
                        .executes(ctx -> {
                            String lv = StringArgumentType.getString(ctx, "level");
                            if (!lv.equals("beginner") && !lv.equals("intermediate") && !lv.equals("advanced")) {
                                ctx.getSource().sendFailure(Component.literal("Nivel invalido. Usa: beginner, intermediate, advanced."));
                                return 0;
                            }
                            AsistenteConfig.ENGLISH_LEVEL.set(lv);
                            AsistenteConfig.ENGLISH_LEVEL.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("[Sevino] Nivel de ingles cambiado a: " + lv), false);
                            return 1;
                        })
        ));

        root.then(Commands.literal("vocab")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String topic = randomTopic();
                    AssistantConversation.vocab(p, topic);
                    return 1;
                })
                .then(Commands.argument("topic", StringArgumentType.word()).suggests(TOPICS)
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String topic = StringArgumentType.getString(ctx, "topic");
                            AssistantConversation.vocab(p, topic);
                            return 1;
                        })));

        root.then(Commands.literal("quest")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    QuestManager.requestNewQuest(p);
                    return 1;
                }));

        root.then(Commands.literal("reset")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ChatHistory.clear(p.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal("[Sevino] Historial de conversacion limpiado."), false);
                    return 1;
                }));

        dispatcher.register(root);

        // Aliases cortos
        dispatcher.register(Commands.literal("ai").redirect(dispatcher.getRoot().getChild("sevino")));
    }

    private static int cmdSpawn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();

            AssistantEntity ent = ModEntities.ASSISTANT.get().create(level);
            if (ent == null) {
                ctx.getSource().sendFailure(Component.literal("[Sevino] No se pudo crear la entidad."));
                return 0;
            }
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 2.0D;
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 2.0D;
            ent.moveTo(x, player.getY(), z, player.getYRot(), 0F);
            ent.setOwner(player);
            ent.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()),
                    MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(ent);

            ctx.getSource().sendSuccess(() -> Component.literal("[Sevino] Listo, te acompañare. Habla con !ai <mensaje>."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[Sevino] Error al invocar: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdDespawn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            int removed = 0;
            for (AssistantEntity a : level.getEntitiesOfClass(AssistantEntity.class,
                    player.getBoundingBox().inflate(64.0D))) {
                if (player.getUUID().equals(a.getOwnerUuid())) {
                    a.discard();
                    removed++;
                }
            }
            int finalRemoved = removed;
            ctx.getSource().sendSuccess(() -> Component.literal("[Sevino] Asistentes eliminados: " + finalRemoved), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[Sevino] " + e.getMessage()));
            return 0;
        }
    }

    private static String randomTopic() {
        List<? extends String> topics = AsistenteConfig.VOCAB_TOPICS.get();
        if (topics.isEmpty()) return "minecraft";
        return topics.get((int) (Math.random() * topics.size()));
    }
}
