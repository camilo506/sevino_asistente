package com.sevino.asistente.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.EnumSet;

/**
 * Goal sencillo que hace que el asistente siga a su owner.
 * Inspirado en TamableAnimal.FollowOwnerGoal de vanilla, pero adaptado a nuestra entidad.
 */
public class FollowOwnerGoal extends Goal {

    private final AssistantEntity assistant;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;
    private final PathNavigation navigation;
    private LivingEntity owner;
    private int timeToRecalc;
    private float oldWaterCost;

    public FollowOwnerGoal(AssistantEntity assistant, double speed, float start, float stop) {
        this.assistant = assistant;
        this.speed = speed;
        this.startDistance = start;
        this.stopDistance = stop;
        this.navigation = assistant.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player owner = assistant.getOwnerPlayer();
        if (owner == null || owner.isSpectator()) return false;
        if (assistant.distanceToSqr(owner) < (double) (startDistance * startDistance)) return false;
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (navigation.isDone()) return false;
        if (this.owner == null) return false;
        return assistant.distanceToSqr(this.owner) > (double) (stopDistance * stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalc = 0;
        this.oldWaterCost = assistant.getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER);
        assistant.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        assistant.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;
        assistant.getLookControl().setLookAt(this.owner, 10.0F, (float) assistant.getMaxHeadXRot());
        if (--this.timeToRecalc <= 0) {
            this.timeToRecalc = this.adjustedTickDelay(10);
            if (assistant.distanceToSqr(this.owner) >= 144.0D) {
                this.teleportToOwner();
            } else {
                this.navigation.moveTo(this.owner, this.speed);
            }
        }
    }

    private void teleportToOwner() {
        BlockPos ownerPos = this.owner.blockPosition();
        for (int i = 0; i < 10; ++i) {
            int x = randomIntInclusive(-3, 3);
            int y = randomIntInclusive(-1, 1);
            int z = randomIntInclusive(-3, 3);
            if (this.maybeTeleportTo(ownerPos.getX() + x, ownerPos.getY() + y, ownerPos.getZ() + z)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (Math.abs((double) x - this.owner.getX()) < 2.0D
                && Math.abs((double) z - this.owner.getZ()) < 2.0D) {
            return false;
        }
        if (!this.canTeleportTo(new BlockPos(x, y, z))) return false;
        assistant.moveTo((double) x + 0.5D, (double) y, (double) z + 0.5D, assistant.getYRot(), assistant.getXRot());
        this.navigation.stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos pos) {
        LevelReader level = assistant.level();
        var path = WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable());
        if (path != net.minecraft.world.level.pathfinder.BlockPathTypes.WALKABLE) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos).is(BlockTags.LEAVES) == false;
    }

    private int randomIntInclusive(int min, int max) {
        return assistant.getRandom().nextInt(max - min + 1) + min;
    }
}
