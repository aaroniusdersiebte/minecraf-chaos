package com.chaosstream;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class TowerAttackLogic {
    private final TowerManager towerManager;

    public TowerAttackLogic(TowerManager towerManager) {
        this.towerManager = towerManager;
    }

    /**
     * Process attacks for all towers
     * Called every server tick
     */
    public void tick(ServerWorld world) {
        for (Tower tower : towerManager.getAllTowers()) {
            // Tick cooldown
            tower.tickCooldown();

            // Check if tower can attack
            if (!tower.canAttack()) {
                continue;
            }

            // Find target
            HostileEntity target = findNearestTarget(world, tower);
            if (target == null) {
                continue;
            }

            // Attack target
            attackTarget(world, tower, target);

            // Reset cooldown
            tower.resetCooldown();
        }
    }

    /**
     * Find nearest hostile mob in range
     */
    private HostileEntity findNearestTarget(ServerWorld world, Tower tower) {
        BlockPos towerPos = tower.getPosition();
        double range = tower.getType().getRange();

        // Create search box
        Box searchBox = new Box(towerPos).expand(range);

        // Find all hostile entities in range
        List<HostileEntity> hostiles = world.getEntitiesByClass(
                HostileEntity.class,
                searchBox,
                entity -> entity.isAlive() && !entity.isRemoved()
        );

        // Find nearest
        HostileEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (HostileEntity hostile : hostiles) {
            double distance = hostile.squaredDistanceTo(
                    towerPos.getX() + 0.5,
                    towerPos.getY() + 2.0, // Aim from top of tower
                    towerPos.getZ() + 0.5
            );

            if (distance < nearestDistance && distance <= range * range) {
                nearest = hostile;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Attack a target
     */
    private void attackTarget(ServerWorld world, Tower tower, HostileEntity target) {
        TowerType type = tower.getType();

        switch (type) {
            case ARCHER:
                fireArrow(world, tower, target);
                break;
            case CANNON:
                fireCannonball(world, tower, target);
                break;
        }
    }

    /**
     * Fire an arrow from archer tower
     */
    private void fireArrow(ServerWorld world, Tower tower, HostileEntity target) {
        BlockPos towerPos = tower.getPosition();
        Vec3d startPos = new Vec3d(
                towerPos.getX() + 0.5,
                towerPos.getY() + 4.0, // Fire from above tower (fixes self-shooting bug)
                towerPos.getZ() + 0.5
        );

        // Create arrow
        ArrowEntity arrow = new ArrowEntity(world, startPos.x, startPos.y, startPos.z);
        arrow.setDamage(tower.getType().getDamage());
        arrow.setCritical(false);
        arrow.pickupType = ArrowEntity.PickupPermission.DISALLOWED;

        // Calculate direction to target
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0);
        Vec3d direction = targetPos.subtract(startPos).normalize();

        // Set arrow velocity
        double speed = 2.0;
        arrow.setVelocity(direction.x * speed, direction.y * speed, direction.z * speed);

        // Spawn arrow
        world.spawnEntity(arrow);

        // Shooting particles (enchant + crit)
        for (int i = 0; i < 5; i++) {
            world.spawnParticles(
                    ParticleTypes.ENCHANT,
                    startPos.x,
                    startPos.y,
                    startPos.z,
                    1, 0.2, 0.2, 0.2, 0.1
            );
        }

        world.spawnParticles(
                ParticleTypes.CRIT,
                startPos.x,
                startPos.y,
                startPos.z,
                3, 0.1, 0.1, 0.1, 0.1
        );

        // Sound effect
        world.playSound(
                null,
                towerPos,
                SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.BLOCKS,
                1.0f,
                1.0f
        );
    }

    /**
     * Fire a cannonball (small fireball) from cannon tower
     */
    private void fireCannonball(ServerWorld world, Tower tower, HostileEntity target) {
        BlockPos towerPos = tower.getPosition();
        Vec3d startPos = new Vec3d(
                towerPos.getX() + 0.5,
                towerPos.getY() + 4.0, // Fire from above tower
                towerPos.getZ() + 0.5
        );

        // Calculate direction to target
        Vec3d targetPos = target.getPos().add(0, target.getHeight() / 2, 0);
        Vec3d direction = targetPos.subtract(startPos).normalize();

        // Create small fireball
        SmallFireballEntity fireball = new SmallFireballEntity(
                world,
                startPos.x,
                startPos.y,
                startPos.z,
                direction.x,
                direction.y,
                direction.z
        );

        // Spawn fireball
        world.spawnEntity(fireball);

        // On impact, deal damage and AoE
        // Note: SmallFireball has built-in explosion on impact
        // We'll deal direct damage via a custom system

        // Schedule damage application (after a short delay for travel time)
        world.getServer().execute(() -> {
            dealCannonDamage(world, tower, target);
        });

        // Shooting particles (smoke + flame)
        for (int i = 0; i < 8; i++) {
            world.spawnParticles(
                    ParticleTypes.SMOKE,
                    startPos.x,
                    startPos.y - 0.5,
                    startPos.z,
                    1, 0.3, 0.1, 0.3, 0.05
            );
        }

        for (int i = 0; i < 5; i++) {
            world.spawnParticles(
                    ParticleTypes.FLAME,
                    startPos.x,
                    startPos.y - 0.5,
                    startPos.z,
                    1, 0.2, 0.1, 0.2, 0.03
            );
        }

        // Sound effects
        world.playSound(
                null,
                towerPos,
                SoundEvents.ENTITY_GHAST_SHOOT,
                SoundCategory.BLOCKS,
                1.0f,
                0.8f
        );

        world.playSound(
                null,
                towerPos,
                SoundEvents.BLOCK_FIRE_AMBIENT,
                SoundCategory.BLOCKS,
                0.5f,
                1.2f
        );
    }

    /**
     * Deal cannon damage (with AoE)
     */
    private void dealCannonDamage(ServerWorld world, Tower tower, HostileEntity primaryTarget) {
        if (!primaryTarget.isAlive() || primaryTarget.isRemoved()) {
            return;
        }

        TowerType type = tower.getType();
        float damage = type.getDamage();

        // Damage primary target
        primaryTarget.damage(world.getDamageSources().explosion(null, null), damage);

        // AoE damage
        if (type.hasAoE()) {
            double aoeRadius = type.getAoeRadius();
            Box aoeBox = primaryTarget.getBoundingBox().expand(aoeRadius);

            List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
                    LivingEntity.class,
                    aoeBox,
                    entity -> entity.isAlive() && !entity.isRemoved() && entity != primaryTarget
            );

            for (LivingEntity entity : nearbyEntities) {
                if (entity instanceof HostileEntity) {
                    entity.damage(world.getDamageSources().explosion(null, null), damage * 0.5f);
                }
            }

            // AoE visual effect
            Vec3d targetPos = primaryTarget.getPos();
            world.spawnParticles(
                    ParticleTypes.EXPLOSION,
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    1, 0, 0, 0, 0
            );

            world.playSound(
                    null,
                    BlockPos.ofFloored(targetPos),
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.HOSTILE,
                    1.0f,
                    1.0f
            );
        }
    }
}
