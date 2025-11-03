package com.chaosstream;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Custom AI Goals für Defender-Villagers
 * Enthält verschiedene Verhaltensweisen für die Klassen
 */
public class DefenderGoals {

    /**
     * Basis-Goal: Bleibt in der Nähe des Village Cores und verteidigt ihn
     * Greift nahende Hostile Mobs an
     */
    public static class DefendCoreGoal extends Goal {
        private final VillagerEntity villager;
        private final BlockPos corePos;
        private final double maxDistanceFromCore;
        private final double attackDamage;
        private HostileEntity currentTarget;
        private int updatePathTimer;
        private int attackCooldown;
        private int hpUpdateTimer;
        private int patrolUpdateTimer;
        private BlockPos patrolTarget;

        private static final int PATH_UPDATE_INTERVAL = 10; // 0.5 Sekunden (optimiert)
        private static final double ATTACK_RANGE = 20.0;
        private static final double MELEE_REACH = 3.0;
        private static final int ATTACK_COOLDOWN_TICKS = 20; // 1 Sekunde
        private static final int HP_UPDATE_INTERVAL = 40; // 2 Sekunden
        private static final int PATROL_UPDATE_INTERVAL = 100; // 5 Sekunden
        private static final double PATROL_RADIUS = 10.0; // Blöcke um Core

        public DefendCoreGoal(VillagerEntity villager, BlockPos corePos, double maxDistanceFromCore, double attackDamage) {
            this.villager = villager;
            this.corePos = corePos;
            this.maxDistanceFromCore = maxDistanceFromCore;
            this.attackDamage = attackDamage;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return corePos != null && villager.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return corePos != null && villager.isAlive();
        }

        @Override
        public void start() {
            this.updatePathTimer = 0;
            this.currentTarget = null;
            this.hpUpdateTimer = 0;
            this.patrolUpdateTimer = 0;
            this.patrolTarget = null;
        }

        @Override
        public void tick() {
            this.updatePathTimer--;
            this.attackCooldown--;
            this.hpUpdateTimer--;
            this.patrolUpdateTimer--;

            // HP-Display periodisch updaten (alle 2 Sekunden)
            if (this.hpUpdateTimer <= 0) {
                this.hpUpdateTimer = HP_UPDATE_INTERVAL;
                DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(villager.getUuid());
                if (defender != null) {
                    DefenderManager.getInstance().updateHealthDisplay(villager, defender);
                }
            }

            if (this.updatePathTimer <= 0) {
                this.updatePathTimer = PATH_UPDATE_INTERVAL;

                double distanceToCore = villager.getPos().distanceTo(corePos.toCenterPos());

                // Finde besten Target (priorisiert Mobs näher am Core)
                HostileEntity bestTarget = findBestTarget();

                if (bestTarget != null && bestTarget.isAlive()) {
                    // Greife Mob an wenn in Reichweite und nicht zu weit vom Core
                    double distanceToMob = villager.squaredDistanceTo(bestTarget);
                    if (distanceToMob < ATTACK_RANGE * ATTACK_RANGE && distanceToCore < maxDistanceFromCore) {
                        this.currentTarget = bestTarget;
                        this.villager.getNavigation().startMovingTo(bestTarget, 1.0);
                    } else if (distanceToCore > maxDistanceFromCore) {
                        // Zu weit vom Core - kehre zurück
                        this.currentTarget = null;
                        this.villager.getNavigation().startMovingTo(corePos.getX(), corePos.getY(), corePos.getZ(), 1.0);
                    }
                } else {
                    // Keine Bedrohung - Patrol-Mode
                    this.currentTarget = null;

                    if (this.patrolUpdateTimer <= 0) {
                        this.patrolUpdateTimer = PATROL_UPDATE_INTERVAL;

                        // Generiere zufällige Patrol-Position in PATROL_RADIUS um Core
                        java.util.Random random = new java.util.Random();
                        int offsetX = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);
                        int offsetZ = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);

                        this.patrolTarget = corePos.add(offsetX, 0, offsetZ);
                        this.villager.getNavigation().startMovingTo(
                            patrolTarget.getX(),
                            patrolTarget.getY(),
                            patrolTarget.getZ(),
                            0.7
                        );
                    }
                }
            }

            // Schaue zum Ziel und greife an wenn nah genug
            if (this.currentTarget != null && this.currentTarget.isAlive()) {
                this.villager.getLookControl().lookAt(this.currentTarget, 30.0F, 30.0F);

                // Melee Attack wenn nah genug
                double distanceToTarget = villager.squaredDistanceTo(this.currentTarget);
                if (distanceToTarget <= MELEE_REACH * MELEE_REACH && attackCooldown <= 0) {
                    performMeleeAttack(this.currentTarget);
                    attackCooldown = ATTACK_COOLDOWN_TICKS;
                }
            } else if (patrolTarget != null) {
                // Schaue zur Patrol-Position
                this.villager.getLookControl().lookAt(patrolTarget.toCenterPos());
            }
        }

        /**
         * Führt Melee-Angriff aus
         */
        private void performMeleeAttack(HostileEntity target) {
            // Verursache Schaden
            boolean wasAlive = target.isAlive();
            target.damage(villager.getDamageSources().mobAttack(villager), (float) attackDamage);

            // Schwing-Animation (visueller Effekt)
            villager.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            // Trigger XP-Event wenn Mob getötet wurde
            if (wasAlive && !target.isAlive() && villager.getWorld() instanceof ServerWorld) {
                DefenderManager.getInstance().onMobKilled(villager.getUuid(), (ServerWorld) villager.getWorld());
            }
        }

        /**
         * Findet besten Target mit Score-basierter Priorität
         * Mobs näher am Core haben höhere Priorität
         * Score = distanceToVillager * 0.3 + distanceToCore * 0.7
         * Niedrigster Score = höchste Priorität
         */
        private HostileEntity findBestTarget() {
            List<HostileEntity> hostiles = this.villager.getWorld().getEntitiesByClass(
                HostileEntity.class,
                this.villager.getBoundingBox().expand(ATTACK_RANGE),
                entity -> entity.isAlive()
            );

            if (hostiles.isEmpty()) {
                return null;
            }

            HostileEntity bestTarget = null;
            double bestScore = Double.MAX_VALUE;

            Vec3d coreCenter = corePos.toCenterPos();

            for (HostileEntity hostile : hostiles) {
                double distanceToVillager = Math.sqrt(this.villager.squaredDistanceTo(hostile));
                double distanceToCore = hostile.getPos().distanceTo(coreCenter);

                // Score-Berechnung: Priorisiere Mobs näher am Core (70%) über Nähe zum Villager (30%)
                double score = (distanceToVillager * 0.3) + (distanceToCore * 0.7);

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = hostile;
                }
            }

            return bestTarget;
        }
    }

    /**
     * Ranged-Attack-Goal: Für Archer - schießt Pfeile auf Distanz
     */
    public static class RangedAttackGoal extends Goal {
        private final VillagerEntity archer;
        private final BlockPos corePos;
        private final double attackDamage;
        private final double attackRange;
        private final double preferredDistance;
        private HostileEntity currentTarget;
        private int attackCooldown;
        private int updatePathTimer;
        private int hpUpdateTimer;
        private int patrolUpdateTimer;
        private BlockPos patrolTarget;

        private static final int ATTACK_COOLDOWN_TICKS = 30; // 1.5 Sekunden
        private static final int PATH_UPDATE_INTERVAL = 10;
        private static final int HP_UPDATE_INTERVAL = 40; // 2 Sekunden
        private static final int PATROL_UPDATE_INTERVAL = 100; // 5 Sekunden
        private static final double PATROL_DISTANCE_FROM_CORE = 15.0; // Optimal für Fernkampf

        public RangedAttackGoal(VillagerEntity archer, BlockPos corePos, double attackDamage, double attackRange, double preferredDistance) {
            this.archer = archer;
            this.corePos = corePos;
            this.attackDamage = attackDamage;
            this.attackRange = attackRange;
            this.preferredDistance = preferredDistance;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return corePos != null && archer.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return corePos != null && archer.isAlive();
        }

        @Override
        public void start() {
            this.attackCooldown = 0;
            this.updatePathTimer = 0;
            this.currentTarget = null;
            this.hpUpdateTimer = 0;
            this.patrolUpdateTimer = 0;
            this.patrolTarget = null;
        }

        @Override
        public void tick() {
            this.attackCooldown--;
            this.updatePathTimer--;
            this.hpUpdateTimer--;
            this.patrolUpdateTimer--;

            // HP-Display periodisch updaten (alle 2 Sekunden)
            if (this.hpUpdateTimer <= 0) {
                this.hpUpdateTimer = HP_UPDATE_INTERVAL;
                DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(archer.getUuid());
                if (defender != null) {
                    DefenderManager.getInstance().updateHealthDisplay(archer, defender);
                }
            }

            if (this.updatePathTimer <= 0) {
                this.updatePathTimer = PATH_UPDATE_INTERVAL;

                // Finde besten Target (priorisiert Mobs näher am Core)
                HostileEntity bestTarget = findBestTarget();

                if (bestTarget != null && bestTarget.isAlive()) {
                    this.currentTarget = bestTarget;

                    double distanceToTarget = archer.squaredDistanceTo(bestTarget);
                    double preferredDistanceSq = preferredDistance * preferredDistance;

                    // Halte Distanz: zu nah → zurückweichen, zu weit → näher ran
                    if (distanceToTarget < (preferredDistance * 0.5) * (preferredDistance * 0.5)) {
                        // Zu nah - weiche zurück
                        double dx = archer.getX() - bestTarget.getX();
                        double dz = archer.getZ() - bestTarget.getZ();
                        double length = Math.sqrt(dx * dx + dz * dz);
                        dx /= length;
                        dz /= length;

                        archer.getNavigation().startMovingTo(
                            archer.getX() + dx * 10,
                            archer.getY(),
                            archer.getZ() + dz * 10,
                            1.0
                        );
                    } else if (distanceToTarget > attackRange * attackRange) {
                        // Zu weit - näher ran
                        archer.getNavigation().startMovingTo(bestTarget, 0.9);
                    }
                    // Navigation.stop() entfernt - Archer bleiben mobil
                } else {
                    // Keine Bedrohung - Patrol-Mode
                    this.currentTarget = null;

                    if (this.patrolUpdateTimer <= 0) {
                        this.patrolUpdateTimer = PATROL_UPDATE_INTERVAL;

                        // Archer patrouillieren in PATROL_DISTANCE_FROM_CORE Distanz zum Core
                        java.util.Random random = new java.util.Random();
                        double angle = random.nextDouble() * Math.PI * 2;
                        int offsetX = (int) (Math.cos(angle) * PATROL_DISTANCE_FROM_CORE);
                        int offsetZ = (int) (Math.sin(angle) * PATROL_DISTANCE_FROM_CORE);

                        this.patrolTarget = corePos.add(offsetX, 0, offsetZ);
                        archer.getNavigation().startMovingTo(
                            patrolTarget.getX(),
                            patrolTarget.getY(),
                            patrolTarget.getZ(),
                            0.7
                        );
                    }
                }
            }

            // Schieße Pfeil wenn Ziel in Reichweite
            if (this.currentTarget != null && this.currentTarget.isAlive()) {
                archer.getLookControl().lookAt(this.currentTarget, 30.0F, 30.0F);

                double distanceToTarget = Math.sqrt(archer.squaredDistanceTo(this.currentTarget));
                if (distanceToTarget <= attackRange && attackCooldown <= 0) {
                    shootArrow(this.currentTarget);
                    attackCooldown = ATTACK_COOLDOWN_TICKS;
                }
            } else if (patrolTarget != null) {
                // Schaue zur Patrol-Position
                archer.getLookControl().lookAt(patrolTarget.toCenterPos());
            }
        }

        /**
         * Schießt einen Pfeil auf das Ziel
         */
        private void shootArrow(HostileEntity target) {
            if (!(archer.getWorld() instanceof ServerWorld)) return;
            ServerWorld world = (ServerWorld) archer.getWorld();

            // Erstelle Pfeil-Entity
            net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(
                world,
                archer
            );

            // Berechne Richtung
            double dx = target.getX() - archer.getX();
            double dy = target.getBodyY(0.5) - arrow.getY();
            double dz = target.getZ() - archer.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            // Setze Velocity (Pfeilflug)
            arrow.setVelocity(dx, dy + distance * 0.15, dz, 1.6F, 1.0F);

            // Setze Schaden
            arrow.setDamage(attackDamage);

            // Setze Owner für XP-Tracking
            arrow.setOwner(archer);

            // Spawne Pfeil
            world.spawnEntity(arrow);

            // Schwing-Animation
            archer.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            // Sound-Effekt
            world.playSound(
                null,
                archer.getX(),
                archer.getY(),
                archer.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_ARROW_SHOOT,
                net.minecraft.sound.SoundCategory.NEUTRAL,
                1.0f,
                1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f)
            );
        }

        /**
         * Findet besten Target mit Score-basierter Priorität
         * Mobs näher am Core haben höhere Priorität
         * Score = distanceToArcher * 0.3 + distanceToCore * 0.7
         * Niedrigster Score = höchste Priorität
         */
        private HostileEntity findBestTarget() {
            List<HostileEntity> hostiles = this.archer.getWorld().getEntitiesByClass(
                HostileEntity.class,
                this.archer.getBoundingBox().expand(attackRange),
                entity -> entity.isAlive()
            );

            if (hostiles.isEmpty()) {
                return null;
            }

            HostileEntity bestTarget = null;
            double bestScore = Double.MAX_VALUE;

            Vec3d coreCenter = corePos.toCenterPos();

            for (HostileEntity hostile : hostiles) {
                double distanceToArcher = Math.sqrt(this.archer.squaredDistanceTo(hostile));
                double distanceToCore = hostile.getPos().distanceTo(coreCenter);

                // Score-Berechnung: Priorisiere Mobs näher am Core (70%) über Nähe zum Archer (30%)
                double score = (distanceToArcher * 0.3) + (distanceToCore * 0.7);

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = hostile;
                }
            }

            return bestTarget;
        }
    }

    /**
     * Heiler-Goal: Heilt verbündete Villagers und Spieler in der Nähe
     */
    public static class HealNearbyGoal extends Goal {
        private final VillagerEntity healer;
        private final BlockPos corePos;
        private final double healRange;
        private final int healAmount;
        private final int healCooldown;
        private int cooldownTimer;
        private int hpUpdateTimer;
        private int patrolUpdateTimer;
        private BlockPos patrolTarget;

        private static final int HP_UPDATE_INTERVAL = 40; // 2 Sekunden
        private static final int PATROL_UPDATE_INTERVAL = 100; // 5 Sekunden
        private static final double PATROL_RADIUS = 10.0; // Blöcke um Core

        public HealNearbyGoal(VillagerEntity healer, BlockPos corePos, double healRange, int healAmount, int healCooldown) {
            this.healer = healer;
            this.corePos = corePos;
            this.healRange = healRange;
            this.healAmount = healAmount;
            this.healCooldown = healCooldown;
            this.cooldownTimer = 0;
            this.hpUpdateTimer = 0;
            this.patrolUpdateTimer = 0;
            this.patrolTarget = null;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return healer.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return healer.isAlive();
        }

        @Override
        public void tick() {
            cooldownTimer--;
            hpUpdateTimer--;
            patrolUpdateTimer--;

            // HP-Display periodisch updaten (alle 2 Sekunden)
            if (hpUpdateTimer <= 0) {
                hpUpdateTimer = HP_UPDATE_INTERVAL;
                DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(healer.getUuid());
                if (defender != null) {
                    DefenderManager.getInstance().updateHealthDisplay(healer, defender);
                }
            }

            if (cooldownTimer <= 0) {
                // Versuche zu heilen
                boolean healed = healNearbyEntities();

                if (healed) {
                    cooldownTimer = healCooldown; // 5 Sekunden = 100 Ticks

                    // Trigger XP-Event in DefenderManager
                    DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(healer.getUuid());
                    if (defender != null && healer.getWorld() instanceof ServerWorld) {
                        DefenderManager.getInstance().onHealing(
                            healer.getUuid(),
                            healAmount,
                            (ServerWorld) healer.getWorld()
                        );
                    }
                } else if (corePos != null && patrolUpdateTimer <= 0) {
                    // Niemand zu heilen - Patrol-Mode
                    patrolUpdateTimer = PATROL_UPDATE_INTERVAL;

                    // Generiere zufällige Patrol-Position in PATROL_RADIUS um Core
                    java.util.Random random = new java.util.Random();
                    int offsetX = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);
                    int offsetZ = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);

                    patrolTarget = corePos.add(offsetX, 0, offsetZ);
                    healer.getNavigation().startMovingTo(
                        patrolTarget.getX(),
                        patrolTarget.getY(),
                        patrolTarget.getZ(),
                        0.6
                    );
                }
            }

            // Schaue zur Patrol-Position wenn aktiv
            if (patrolTarget != null && healer.getNavigation().isIdle()) {
                healer.getLookControl().lookAt(patrolTarget.toCenterPos());
            }
        }

        /**
         * Heilt Entities in der Nähe
         * WICHTIG: Spieler haben höchste Priorität, dann Villagers
         */
        private boolean healNearbyEntities() {
            Box searchBox = healer.getBoundingBox().expand(healRange);
            boolean healedSomeone = false;

            // PRIORITÄT 1: Heile Spieler ZUERST
            List<PlayerEntity> players = healer.getWorld().getEntitiesByClass(
                PlayerEntity.class,
                searchBox,
                p -> p.isAlive() && p.getHealth() < p.getMaxHealth()
            );

            if (!players.isEmpty()) {
                PlayerEntity player = players.get(0);
                player.heal(healAmount);
                spawnHealParticles(player);
                healedSomeone = true;
            }

            // PRIORITÄT 2: Heile Villagers (nur wenn kein Spieler geheilt wurde)
            if (!healedSomeone) {
                List<VillagerEntity> villagers = healer.getWorld().getEntitiesByClass(
                    VillagerEntity.class,
                    searchBox,
                    v -> v.isAlive() && v.getHealth() < v.getMaxHealth() && v != healer
                );

                for (VillagerEntity villager : villagers) {
                    villager.heal(healAmount);
                    spawnHealParticles(villager);
                    healedSomeone = true;
                    break; // Nur einen pro Cycle heilen
                }
            }

            return healedSomeone;
        }

        private void spawnHealParticles(LivingEntity target) {
            if (healer.getWorld() instanceof ServerWorld) {
                ServerWorld world = (ServerWorld) healer.getWorld();
                BlockPos pos = target.getBlockPos();

                // Grüne Herz-Partikel
                for (int i = 0; i < 10; i++) {
                    world.spawnParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5,
                        pos.getY() + 1.0,
                        pos.getZ() + 0.5,
                        1,
                        0.3, 0.5, 0.3,
                        0.0
                    );
                }
            }
        }
    }

    /**
     * Builder-Goal: Repariert den Village Core
     */
    public static class RepairCoreGoal extends Goal {
        private final VillagerEntity builder;
        private final BlockPos corePos;
        private final int repairAmount;
        private final int repairCooldown;
        private int cooldownTimer;
        private int hpUpdateTimer;
        private int patrolUpdateTimer;
        private BlockPos patrolTarget;

        private static final int HP_UPDATE_INTERVAL = 40; // 2 Sekunden
        private static final int PATROL_UPDATE_INTERVAL = 100; // 5 Sekunden
        private static final double PATROL_RADIUS = 8.0; // Blöcke um Core (Builder bleiben nah)

        public RepairCoreGoal(VillagerEntity builder, BlockPos corePos, int repairAmount, int repairCooldown) {
            this.builder = builder;
            this.corePos = corePos;
            this.repairAmount = repairAmount;
            this.repairCooldown = repairCooldown;
            this.cooldownTimer = 0;
            this.hpUpdateTimer = 0;
            this.patrolUpdateTimer = 0;
            this.patrolTarget = null;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return corePos != null && builder.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return corePos != null && builder.isAlive();
        }

        @Override
        public void tick() {
            cooldownTimer--;
            hpUpdateTimer--;
            patrolUpdateTimer--;

            // HP-Display periodisch updaten (alle 2 Sekunden)
            if (hpUpdateTimer <= 0) {
                hpUpdateTimer = HP_UPDATE_INTERVAL;
                DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(builder.getUuid());
                if (defender != null) {
                    DefenderManager.getInstance().updateHealthDisplay(builder, defender);
                }
            }

            double distanceToCore = builder.getPos().distanceTo(corePos.toCenterPos());

            VillageManager villageManager = ChaosMod.getVillageManager();
            int currentHP = villageManager.getCoreHP();
            int maxHP = 100;

            if (currentHP < maxHP) {
                // Core braucht Reparatur - navigiere zum Core
                if (distanceToCore > 10.0) {
                    builder.getNavigation().startMovingTo(corePos.getX(), corePos.getY(), corePos.getZ(), 0.8);
                    builder.getLookControl().lookAt(corePos.toCenterPos());
                }

                // Repariere Core wenn in Reichweite
                if (cooldownTimer <= 0 && distanceToCore <= 10.0) {
                    // Repariere
                    villageManager.repairCore(repairAmount, builder.getUuid());

                    // Spawn Partikel
                    spawnRepairParticles();

                    // Trigger XP-Event
                    DefenderVillager defender = DefenderManager.getInstance().getDefenderByEntityUUID(builder.getUuid());
                    if (defender != null && builder.getWorld() instanceof ServerWorld) {
                        DefenderManager.getInstance().onCoreRepaired(
                            builder.getUuid(),
                            repairAmount,
                            (ServerWorld) builder.getWorld()
                        );
                    }

                    cooldownTimer = repairCooldown; // 10 Sekunden = 200 Ticks
                }
            } else if (patrolUpdateTimer <= 0) {
                // Core ist voll - Patrol-Mode
                patrolUpdateTimer = PATROL_UPDATE_INTERVAL;

                // Generiere zufällige Patrol-Position in PATROL_RADIUS um Core
                java.util.Random random = new java.util.Random();
                int offsetX = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);
                int offsetZ = (int) ((random.nextDouble() - 0.5) * 2 * PATROL_RADIUS);

                patrolTarget = corePos.add(offsetX, 0, offsetZ);
                builder.getNavigation().startMovingTo(
                    patrolTarget.getX(),
                    patrolTarget.getY(),
                    patrolTarget.getZ(),
                    0.6
                );
            }

            // Schaue zur Patrol-Position wenn Core voll und aktiv
            if (currentHP >= maxHP && patrolTarget != null) {
                builder.getLookControl().lookAt(patrolTarget.toCenterPos());
            }
        }

        private void spawnRepairParticles() {
            if (builder.getWorld() instanceof ServerWorld) {
                ServerWorld world = (ServerWorld) builder.getWorld();

                // Gelbe Partikel am Core
                for (int i = 0; i < 15; i++) {
                    world.spawnParticles(
                        ParticleTypes.END_ROD,
                        corePos.getX() + 0.5,
                        corePos.getY() + 1.0,
                        corePos.getZ() + 0.5,
                        1,
                        0.3, 0.3, 0.3,
                        0.05
                    );
                }
            }
        }
    }

    /**
     * Tank-Goal: Zieht Aggro von Mobs auf sich
     * Funktioniert durch periodisches "Taunten" naher Mobs
     */
    public static class TauntGoal extends Goal {
        private final VillagerEntity tank;
        private final double tauntRange;
        private final int tauntInterval;
        private int tauntTimer;

        public TauntGoal(VillagerEntity tank, double tauntRange, int tauntInterval) {
            this.tank = tank;
            this.tauntRange = tauntRange;
            this.tauntInterval = tauntInterval;
            this.tauntTimer = 0;
            this.setControls(EnumSet.of(Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return tank.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return tank.isAlive();
        }

        @Override
        public void tick() {
            tauntTimer--;

            if (tauntTimer <= 0) {
                tauntNearbyMobs();
                tauntTimer = tauntInterval; // 3 Sekunden = 60 Ticks
            }
        }

        private void tauntNearbyMobs() {
            List<HostileEntity> hostiles = tank.getWorld().getEntitiesByClass(
                HostileEntity.class,
                tank.getBoundingBox().expand(tauntRange),
                entity -> entity.isAlive()
            );

            for (HostileEntity hostile : hostiles) {
                // 90% Chance dass Mob den Tank angreift
                if (tank.getWorld().getRandom().nextFloat() < 0.9f) {
                    hostile.setTarget(tank);
                }
            }

            // Visuelle Feedback wenn Mobs getaunted wurden
            if (!hostiles.isEmpty() && tank.getWorld() instanceof ServerWorld) {
                ServerWorld world = (ServerWorld) tank.getWorld();
                BlockPos pos = tank.getBlockPos();

                // Blaue Wellen-Partikel
                for (int i = 0; i < 20; i++) {
                    double angle = (i / 20.0) * Math.PI * 2;
                    double offsetX = Math.cos(angle) * 2.0;
                    double offsetZ = Math.sin(angle) * 2.0;

                    world.spawnParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + 0.5 + offsetX,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5 + offsetZ,
                        1,
                        0.0, 0.0, 0.0,
                        0.0
                    );
                }
            }
        }
    }
}
