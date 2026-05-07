package com.sevino.asistente.event;

import com.sevino.asistente.SevinoAsistente;
import com.sevino.asistente.chat.ChatHistory;
import com.sevino.asistente.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SevinoAsistente.MOD_ID)
public class PlayerEvents {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (++tickCounter < 20) return; // ~1 vez por segundo
        tickCounter = 0;
        QuestManager.tick(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            ChatHistory.clear(event.getEntity().getUUID());
        }
    }
}
