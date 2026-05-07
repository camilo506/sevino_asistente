package com.sevino.asistente.entity;

import com.sevino.asistente.SevinoAsistente;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SevinoAsistente.MOD_ID);

    public static final RegistryObject<EntityType<AssistantEntity>> ASSISTANT = ENTITIES.register(
            "assistant",
            () -> EntityType.Builder.<AssistantEntity>of(AssistantEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(SevinoAsistente.MOD_ID + ":assistant")
    );

    private ModEntities() {}
}
