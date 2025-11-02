package com.chaosstream;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Custom AI Goal that makes mobs navigate toward a village core block.
 * Mobs will attack villagers if they encounter them, but prioritize reaching the core.
 */
public class AttackVillageCoreGoal extends Goal {
    private final MobEntity mob;
    private final BlockPos corePos;
    private LivingEntity currentTarget;
    private int updatePathTimer;

    private static final double CORE_REACH_DISTANCE = 3.0;
    private static final double VILLAGER_DETECTION_RANGE = 12.0;
    private static final int PATH_UPDATE_INTERVAL = 20; // Update path every 20 ticks (1 second)

    public AttackVillageCoreGoal(MobEntity mob, BlockPos corePos) {
        this.mob = mob;
        this.corePos = corePos;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // Only start if core exists and mob is not too far away
        if (corePos == null) return false;

        double distanceToCore = mob.getPos().distanceTo(corePos.toCenterPos());

        // Don't start if already at core
        if (distanceToCore <= CORE_REACH_DISTANCE) return false;

        // Start if within reasonable range (200 blocks)
        return distanceToCore < 200.0;
    }

    @Override
    public boolean shouldContinue() {
        // Continue as long as core exists and mob is alive
        return corePos != null && mob.isAlive();
    }

    @Override
    public void start() {
        this.updatePathTimer = 0;
        this.currentTarget = null;
    }

    @Override
    public void tick() {
        // Update path periodically
        this.updatePathTimer--;

        if (this.updatePathTimer <= 0) {
            this.updatePathTimer = PATH_UPDATE_INTERVAL;

            // Check for nearby villagers (secondary target)
            LivingEntity nearestVillager = findNearestVillager();

            if (nearestVillager != null && nearestVillager.isAlive()) {
                // Attack villager if in range
                this.currentTarget = nearestVillager;
                this.mob.setTarget(nearestVillager);
                this.mob.getNavigation().startMovingTo(nearestVillager, 1.0);
            } else {
                // No villagers nearby - move toward core
                this.currentTarget = null;
                this.mob.setTarget(null);
                this.mob.getNavigation().startMovingTo(corePos.getX(), corePos.getY(), corePos.getZ(), 1.0);
            }
        }

        // Look at current target (core or villager)
        if (this.currentTarget != null && this.currentTarget.isAlive()) {
            this.mob.getLookControl().lookAt(this.currentTarget, 30.0F, 30.0F);
        } else {
            this.mob.getLookControl().lookAt(corePos.toCenterPos());
        }
    }

    private LivingEntity findNearestVillager() {
        // Find all villagers in range
        java.util.List<VillagerEntity> villagers = this.mob.getWorld().getEntitiesByClass(
            VillagerEntity.class,
            this.mob.getBoundingBox().expand(VILLAGER_DETECTION_RANGE),
            villager -> villager.isAlive()
        );

        // Find closest villager manually
        VillagerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (VillagerEntity villager : villagers) {
            double distance = this.mob.squaredDistanceTo(villager);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = villager;
            }
        }

        return closest;
    }
}
