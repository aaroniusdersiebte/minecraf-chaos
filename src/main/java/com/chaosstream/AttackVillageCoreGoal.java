package com.chaosstream;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Custom AI Goal that makes mobs navigate toward a village core block.
 * Mobs will attack villagers and players if they encounter them, but prioritize reaching the core.
 */
public class AttackVillageCoreGoal extends Goal {
    private final MobEntity mob;
    private final BlockPos corePos;
    private LivingEntity currentTarget;
    private int updatePathTimer;
    private int attackCooldown;

    private static final double CORE_REACH_DISTANCE = 3.0;
    private static final double ENTITY_DETECTION_RANGE = 12.0;
    private static final double ATTACK_REACH = 2.5;
    private static final int PATH_UPDATE_INTERVAL = 20; // Update path every 20 ticks (1 second)
    private static final int ATTACK_COOLDOWN_TICKS = 20; // 1 second between attacks

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
        this.attackCooldown--;

        if (this.updatePathTimer <= 0) {
            this.updatePathTimer = PATH_UPDATE_INTERVAL;

            // Check for nearby threats (villagers or players)
            LivingEntity nearestThreat = findNearestThreat();

            if (nearestThreat != null && nearestThreat.isAlive()) {
                // Attack threat if in range
                this.currentTarget = nearestThreat;
                this.mob.setTarget(nearestThreat);
                this.mob.getNavigation().startMovingTo(nearestThreat, 1.0);
            } else {
                // No threats nearby - move toward core
                this.currentTarget = null;
                this.mob.setTarget(null);
                this.mob.getNavigation().startMovingTo(corePos.getX(), corePos.getY(), corePos.getZ(), 1.0);
            }
        }

        // Look at current target (core or threat)
        if (this.currentTarget != null && this.currentTarget.isAlive()) {
            this.mob.getLookControl().lookAt(this.currentTarget, 30.0F, 30.0F);

            // Attack if close enough
            double distanceToTarget = this.mob.squaredDistanceTo(this.currentTarget);
            if (distanceToTarget <= ATTACK_REACH * ATTACK_REACH && attackCooldown <= 0) {
                performAttack(this.currentTarget);
                attackCooldown = ATTACK_COOLDOWN_TICKS;
            }
        } else {
            this.mob.getLookControl().lookAt(corePos.toCenterPos());
        }
    }

    /**
     * Führt einen Angriff auf das Ziel aus
     */
    private void performAttack(LivingEntity target) {
        // Nutze tryAttack wenn verfügbar (standard mob attack)
        this.mob.tryAttack(target);

        // Schwing-Animation
        this.mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    /**
     * Findet die nächste Bedrohung (Villager oder Spieler)
     */
    private LivingEntity findNearestThreat() {
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        // Finde Villagers
        java.util.List<VillagerEntity> villagers = this.mob.getWorld().getEntitiesByClass(
            VillagerEntity.class,
            this.mob.getBoundingBox().expand(ENTITY_DETECTION_RANGE),
            villager -> villager.isAlive()
        );

        for (VillagerEntity villager : villagers) {
            double distance = this.mob.squaredDistanceTo(villager);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = villager;
            }
        }

        // Finde Spieler
        java.util.List<PlayerEntity> players = this.mob.getWorld().getEntitiesByClass(
            PlayerEntity.class,
            this.mob.getBoundingBox().expand(ENTITY_DETECTION_RANGE),
            player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
        );

        for (PlayerEntity player : players) {
            double distance = this.mob.squaredDistanceTo(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = player;
            }
        }

        return closest;
    }
}
