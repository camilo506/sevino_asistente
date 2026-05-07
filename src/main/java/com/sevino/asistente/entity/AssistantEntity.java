package com.sevino.asistente.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Entidad NPC del asistente "Sevino".
 *
 * Caracteristicas:
 *  - Pasiva, no toma daño de jugadores ni de la mayoria de fuentes (solo void/creative).
 *  - Sigue al jugador que la "vincula" (owner) usando un goal personalizado.
 *  - Click derecho con la mano vacia: abre un mensaje rapido de bienvenida y le recuerda
 *    al jugador como hablar con ella (prefijo de chat).
 */
public class AssistantEntity extends PathfinderMob {

    @Nullable
    private UUID ownerUuid;

    public AssistantEntity(EntityType<? extends AssistantEntity> type, net.minecraft.world.level.Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FollowOwnerGoal(this, 1.0D, 4.0F, 12.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public void setOwner(@Nullable Player player) {
        this.ownerUuid = player == null ? null : player.getUUID();
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Nullable
    public Player getOwnerPlayer() {
        if (this.ownerUuid == null) return null;
        return this.level().getPlayerByUUID(this.ownerUuid);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            if (this.ownerUuid == null) {
                this.setOwner(player);
                player.sendSystemMessage(Component.literal("[Sevino] Hi! I will follow you. Use !ai <message> to chat. / Hola, te voy a seguir. Usa !ai <mensaje> para hablar."));
            } else if (this.ownerUuid.equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Sevino] Tip: write '!ai how do I find iron?' / Tip: escribe '!ai how do I find iron?'"));
            } else {
                player.sendSystemMessage(Component.literal("[Sevino] I am already with someone else. / Ya estoy acompañando a otra persona."));
            }
            this.gameEvent(GameEvent.ENTITY_INTERACT);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // El asistente es invulnerable salvo a los modos especiales.
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID("OwnerUUID", this.ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUuid = tag.getUUID("OwnerUUID");
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable net.minecraft.nbt.CompoundTag dataTag) {
        this.setCustomName(Component.literal("Sevino"));
        this.setCustomNameVisible(true);
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    public static boolean checkSpawnRules(EntityType<AssistantEntity> type, LevelAccessor level,
                                          MobSpawnType reason, BlockPos pos, RandomSource random) {
        // Solo se invoca con comando, nunca por spawn natural.
        return reason == MobSpawnType.COMMAND
                || reason == MobSpawnType.MOB_SUMMONED
                || reason == MobSpawnType.SPAWN_EGG;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }
}
