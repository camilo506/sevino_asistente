package com.sevino.asistente;

import com.mojang.logging.LogUtils;
import com.sevino.asistente.client.AssistantRenderer;
import com.sevino.asistente.command.SevinoCommands;
import com.sevino.asistente.config.AsistenteClientConfig;
import com.sevino.asistente.config.AsistenteConfig;
import com.sevino.asistente.entity.AssistantEntity;
import com.sevino.asistente.entity.ModEntities;
import com.sevino.asistente.event.ChatPrefixHandler;
import com.sevino.asistente.network.SevinoNetworking;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SevinoAsistente.MOD_ID)
public class SevinoAsistente {
    public static final String MOD_ID = "sevinoasistente";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SevinoAsistente() {
        // Forzar IPv4 para evitar timeouts de conexion en Windows (comun en Java)
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Registros
        ModEntities.ENTITIES.register(modBus);

        // Lifecycle
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onSpawnPlacement);

        // Eventos de Forge (chat, comandos)
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ChatPrefixHandler());
        MinecraftForge.EVENT_BUS.register(new SevinoCommands());

        // Configuracion
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AsistenteConfig.SPEC, "sevinoasistente-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AsistenteClientConfig.SPEC, "sevinoasistente-client.toml");

        LOGGER.info("[SevinoAsistente] Mod inicializado.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SevinoNetworking::register);
        LOGGER.info("[SevinoAsistente] commonSetup ejecutado.");
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ASSISTANT.get(), AssistantEntity.createAttributes().build());
    }

    private void onSpawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(
                ModEntities.ASSISTANT.get(),
                SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AssistantEntity::checkSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.ASSISTANT.get(), AssistantRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterLayers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                    com.sevino.asistente.client.ModModelLayers.ASSISTANT,
                    com.sevino.asistente.client.AssistantModel::createBodyLayer
            );
        }
    }
}
