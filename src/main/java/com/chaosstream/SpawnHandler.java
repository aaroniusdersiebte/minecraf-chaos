package com.chaosstream;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnHandler {
    private static final int SPAWN_RADIUS = 20;
    private static final int SPAWN_CHECK_INTERVAL = 200; // Every 10 seconds (200 ticks)
    private static final Random RANDOM = new Random();

    private int tickCounter = 0;
    private final ConcurrentLinkedQueue<SpawnCommand> spawnQueue = new ConcurrentLinkedQueue<>();
    private final WaveManager waveManager = new WaveManager();

    // Mob types for chaos spawning
    private static final EntityType<?>[] CHAOS_MOBS = {
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.ENDERMAN,
        EntityType.WITCH
    };

    public void onServerTick(MinecraftServer server, ChaosManager chaosManager) {
        tickCounter++;

        // Process spawn queue every tick
        processSpawnQueue(server);

        // Process wave-based spawning
        waveManager.onServerTick(server, chaosManager);

        // Check for chaos spawning every SPAWN_CHECK_INTERVAL ticks
        if (tickCounter >= SPAWN_CHECK_INTERVAL) {
            tickCounter = 0;
            chaosManager.tick(); // Process chaos decay
        }
    }

    public WaveManager getWaveManager() {
        return waveManager;
    }

    /**
     * Process queued spawn commands
     */
    private void processSpawnQueue(MinecraftServer server) {
        SpawnCommand command;
        while ((command = spawnQueue.poll()) != null) {
            try {
                command.execute(server);
            } catch (Exception e) {
                ChaosMod.LOGGER.error("Error executing spawn command", e);
            }
        }
    }


    /**
     * Queue a creeper spawn
     */
    public void queueCreeperSpawn(String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = findSafeSpawnPosition(world, player.getBlockPos(), 5);

                // Spawn visual-only lightning
                LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                if (lightning != null) {
                    lightning.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos));
                    lightning.setCosmetic(true);
                    world.spawnEntity(lightning);
                }

                // Play creeper spawn sound
                SoundEffects.playCreeperSpawnSound(world, pos);

                // Spawn dramatic particles
                for (int i = 0; i < 30; i++) {
                    double offsetX = (RANDOM.nextDouble() - 0.5) * 3;
                    double offsetZ = (RANDOM.nextDouble() - 0.5) * 3;
                    double offsetY = RANDOM.nextDouble() * 2;

                    world.spawnParticles(ParticleTypes.EXPLOSION,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0.3, 0, 0.05);
                }

                CreeperEntity creeper = EntityType.CREEPER.create(world);
                if (creeper != null) {
                    creeper.refreshPositionAndAngles(pos, 0, 0);
                    creeper.initialize(world, world.getLocalDifficulty(pos), SpawnReason.COMMAND, null, null);
                    world.spawnEntity(creeper);

                    player.sendMessage(Text.literal("§c§lA Creeper has been spawned by the chaos!"), false);
                    ChaosMod.LOGGER.info("Spawned creeper near player {}", player.getName().getString());
                }
            }
        });
    }

    /**
     * Queue a lootbox spawn
     */
    public void queueLootboxSpawn(String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = findSafeSpawnPosition(world, player.getBlockPos(), 3);

                // Play lootbox spawn sound
                SoundEffects.playLootboxSpawnSound(world, pos);

                // Spawn golden sparkle particles
                for (int i = 0; i < 50; i++) {
                    double offsetX = (RANDOM.nextDouble() - 0.5) * 2;
                    double offsetZ = (RANDOM.nextDouble() - 0.5) * 2;
                    double offsetY = RANDOM.nextDouble() * 3;

                    world.spawnParticles(ParticleTypes.END_ROD,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0.5, 0, 0.1);
                    world.spawnParticles(ParticleTypes.ENCHANT,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0.5, 0, 0.1);
                }

                // Firework explosion particles
                world.spawnParticles(ParticleTypes.FIREWORK,
                    pos.getX(), pos.getY() + 1, pos.getZ(),
                    20, 0.5, 0.5, 0.5, 0.15);

                // Spawn a chest with loot
                world.setBlockState(pos, net.minecraft.block.Blocks.CHEST.getDefaultState());

                // Add random loot
                if (world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                    addRandomLoot(chest, world);
                }

                player.sendMessage(Text.literal("§e§lA Lootbox has appeared!"), false);
                ChaosMod.LOGGER.info("Spawned lootbox for player {}", player.getName().getString());
            }
        });
    }

    /**
     * Queue a villager spawn with custom name
     */
    public void queueVillagerSpawn(String villagerName, String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = findSafeSpawnPosition(world, player.getBlockPos(), 3);

                // Play villager spawn sound
                SoundEffects.playVillagerSpawnSound(world, pos);

                // Spawn totem and happy villager particles
                for (int i = 0; i < 40; i++) {
                    double offsetX = (RANDOM.nextDouble() - 0.5) * 2;
                    double offsetZ = (RANDOM.nextDouble() - 0.5) * 2;
                    double offsetY = RANDOM.nextDouble() * 2;

                    world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0.5, 0, 0.1);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                        1, 0, 0.3, 0, 0.05);
                }

                VillagerEntity villager = EntityType.VILLAGER.create(world);
                if (villager != null) {
                    villager.refreshPositionAndAngles(pos, 0, 0);
                    villager.initialize(world, world.getLocalDifficulty(pos), SpawnReason.COMMAND, null, null);
                    villager.setCustomName(Text.literal("§b" + villagerName));
                    villager.setCustomNameVisible(true);
                    world.spawnEntity(villager);

                    player.sendMessage(Text.literal("§b§l" + villagerName + " has joined as a villager!"), false);
                    ChaosMod.LOGGER.info("Spawned villager '{}' for player {}", villagerName, player.getName().getString());
                }
            }
        });
    }

    /**
     * Queue a defender villager spawn (new system with classes and leveling)
     */
    public void queueDefenderSpawn(String viewerName, String className, String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();

                // Parse Klasse
                VillagerClass villagerClass = VillagerClass.fromString(className);

                // Hole Village Core Position oder spawne beim Spieler
                VillageManager villageManager = ChaosMod.getVillageManager();
                BlockPos spawnPos;

                if (villageManager.getVillageCorePos() != null) {
                    // Spawne in Core-Nähe (5-10 Blöcke entfernt)
                    BlockPos corePos = villageManager.getVillageCorePos();
                    int offsetX = RANDOM.nextInt(10) - 5;
                    int offsetZ = RANDOM.nextInt(10) - 5;
                    spawnPos = findSafeSpawnPosition(world, corePos.add(offsetX, 0, offsetZ), 3);
                } else {
                    // Kein Core - spawne beim Spieler
                    spawnPos = findSafeSpawnPosition(world, player.getBlockPos(), 3);
                }

                // Spawne Defender via DefenderManager
                DefenderManager defenderManager = DefenderManager.getInstance();
                DefenderVillager defender = defenderManager.spawnDefender(world, spawnPos, viewerName, villagerClass);

                if (defender != null && defender.getLinkedEntity() != null) {
                    // Füge AI Goals hinzu
                    addDefenderAI(defender.getLinkedEntity(), defender, villageManager.getVillageCorePos());

                    // Nachricht an Spieler
                    String message = String.format("§b✦ %s hat sich als %s%s §bangeschlossen! ✦",
                        viewerName,
                        villagerClass.getColorCode(),
                        villagerClass.getDisplayName());
                    player.sendMessage(Text.literal(message), false);

                    ChaosMod.LOGGER.info("Spawned defender '{}' (Class: {}) for viewer {} at {}",
                        viewerName, villagerClass.getDisplayName(), viewerName, spawnPos);
                } else {
                    ChaosMod.LOGGER.error("Failed to spawn defender for viewer {}", viewerName);
                }
            }
        });
    }

    /**
     * Fügt klassen-spezifische AI Goals zu Defender-Villager hinzu
     */
    private void addDefenderAI(VillagerEntity villager, DefenderVillager defender, BlockPos corePos) {
        VillagerClass vClass = defender.getVillagerClass();
        double attackDamage = defender.getCurrentDamage();

        // WICHTIG: Lösche alle Standard-Villager-AI-Goals!
        // Villager haben standardmäßig viele Goals (WalkToTargetGoal, etc.) die unsere Custom-Goals blockieren
        villager.goalSelector.clear(goal -> true); // Löscht alle Goals

        // HÖCHSTE PRIORITÄT: Patrol-Goal (gilt für alle Klassen)
        // Nur aktiv wenn Patrol-Position gesetzt ist UND nicht im Follow-Modus
        villager.goalSelector.add(0, new DefenderGoals.PatrolGoal(
            villager,
            defender,
            0.6 // Bewegungsgeschwindigkeit
        ));

        // Klassen-spezifische Goals
        switch (vClass) {
            case WARRIOR:
                // Warrior: Melee-Kämpfer mit DefendCoreGoal
                if (corePos != null) {
                    villager.goalSelector.add(1, new DefenderGoals.DefendCoreGoal(
                        villager,
                        corePos,
                        40.0,           // Max Distanz vom Core
                        attackDamage    // Damage basierend auf Level
                    ));
                }
                break;

            case ARCHER:
                // Archer: Fernkämpfer mit RangedAttackGoal
                if (corePos != null) {
                    villager.goalSelector.add(1, new DefenderGoals.RangedAttackGoal(
                        villager,
                        corePos,
                        attackDamage,   // Damage basierend auf Level
                        20.0,           // Attack Range (20 Blöcke)
                        15.0            // Preferred Distance (15 Blöcke)
                    ));
                }
                break;

            case HEALER:
                // Heiler heilt Verbündete (Spieler haben Priorität!)
                villager.goalSelector.add(1, new DefenderGoals.HealNearbyGoal(
                    villager,
                    corePos,   // Core-Position für Patrol
                    10.0,  // 10 Blöcke Reichweite
                    2,     // 2 HP pro Heal
                    100    // 5 Sekunden Cooldown (100 Ticks)
                ));
                // Heiler kann sich auch verteidigen (geringere Priorität)
                if (corePos != null) {
                    villager.goalSelector.add(2, new DefenderGoals.DefendCoreGoal(
                        villager,
                        corePos,
                        30.0,
                        2.0  // Schwacher Angriff
                    ));
                }
                break;

            case BUILDER:
                // Builder repariert Core
                if (corePos != null) {
                    villager.goalSelector.add(1, new DefenderGoals.RepairCoreGoal(
                        villager,
                        corePos,
                        1,     // 1 HP pro Reparatur
                        200    // 10 Sekunden Cooldown (200 Ticks)
                    ));
                    // Builder kann sich auch verteidigen (geringere Priorität)
                    villager.goalSelector.add(2, new DefenderGoals.DefendCoreGoal(
                        villager,
                        corePos,
                        30.0,
                        attackDamage
                    ));
                }
                break;

            case TANK:
                // Tank: Melee + Taunt
                if (corePos != null) {
                    villager.goalSelector.add(1, new DefenderGoals.DefendCoreGoal(
                        villager,
                        corePos,
                        50.0,           // Größere Range für Tank
                        attackDamage
                    ));
                }
                // Tank taunted Mobs
                villager.goalSelector.add(0, new DefenderGoals.TauntGoal(
                    villager,
                    15.0,  // 15 Blöcke Taunt-Reichweite
                    60     // 3 Sekunden Taunt-Intervall (60 Ticks)
                ));
                break;
        }
    }

    /**
     * Queue TNT spawn
     */
    public void queueTNTSpawn(String playerName, int count, int fuseTicks) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos playerPos = player.getBlockPos();

                // Play TNT sound
                SoundEffects.playTNTSound(world, playerPos);

                // Spawn TNT entities
                int actualCount = Math.min(count, 5); // Max 5 TNT
                for (int i = 0; i < actualCount; i++) {
                    BlockPos pos = findSafeSpawnPosition(world, playerPos.up(5), 2);

                    net.minecraft.entity.TntEntity tnt = new net.minecraft.entity.TntEntity(world,
                        pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player);
                    tnt.setFuse(fuseTicks);
                    world.spawnEntity(tnt);

                    // Smoke particles
                    world.spawnParticles(ParticleTypes.SMOKE,
                        pos.getX(), pos.getY(), pos.getZ(),
                        10, 0.5, 0.5, 0.5, 0.02);
                }

                player.sendMessage(Text.literal("§c§lTNT incoming from the chaos!"), false);
                ChaosMod.LOGGER.info("Spawned {} TNT near player {}", actualCount, player.getName().getString());
            }
        });
    }

    /**
     * Queue random teleport
     */
    public void queueRandomTeleport(String playerName, int radius) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos currentPos = player.getBlockPos();

                // Find random safe location
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                int distance = 50 + RANDOM.nextInt(radius - 50);

                int x = currentPos.getX() + (int)(Math.cos(angle) * distance);
                int z = currentPos.getZ() + (int)(Math.sin(angle) * distance);
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

                BlockPos newPos = new BlockPos(x, y, z);

                // Play teleport sound at old position
                SoundEffects.playTeleportSound(world, currentPos);

                // Teleport player
                player.teleport(world, newPos.getX() + 0.5, newPos.getY(), newPos.getZ() + 0.5,
                    player.getYaw(), player.getPitch());

                // Play teleport sound at new position
                SoundEffects.playTeleportSound(world, newPos);

                // Particle effects at both locations
                world.spawnParticles(ParticleTypes.PORTAL,
                    currentPos.getX(), currentPos.getY() + 1, currentPos.getZ(),
                    50, 0.5, 1, 0.5, 0.5);
                world.spawnParticles(ParticleTypes.PORTAL,
                    newPos.getX(), newPos.getY() + 1, newPos.getZ(),
                    50, 0.5, 1, 0.5, 0.5);

                player.sendMessage(Text.literal("§5§lYou've been teleported by the chaos!"), false);
                ChaosMod.LOGGER.info("Teleported player {} to {}", player.getName().getString(), newPos);
            }
        });
    }

    /**
     * Queue weather change
     */
    public void queueWeatherChange(String weatherType) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                for (ServerWorld world : server.getWorlds()) {
                    if (world.getRegistryKey() != ServerWorld.OVERWORLD) continue;

                    List<ServerPlayerEntity> players = world.getPlayers();
                    if (players.isEmpty()) continue;

                    // Play weather change sound
                    SoundEffects.playWeatherChangeSound(world, players);

                    switch (weatherType.toLowerCase()) {
                        case "clear":
                            world.setWeather(6000, 0, false, false);
                            for (ServerPlayerEntity player : players) {
                                player.sendMessage(Text.literal("§e§lThe chaos has cleared the skies!"), false);
                            }
                            break;
                        case "rain":
                            world.setWeather(0, 6000, true, false);
                            for (ServerPlayerEntity player : players) {
                                player.sendMessage(Text.literal("§9§lThe chaos brings rain!"), false);
                            }
                            break;
                        case "thunder":
                            world.setWeather(0, 6000, true, true);
                            for (ServerPlayerEntity player : players) {
                                player.sendMessage(Text.literal("§c§lThe chaos summons a thunderstorm!"), false);
                            }
                            break;
                    }

                    ChaosMod.LOGGER.info("Weather changed to: {}", weatherType);
                }
            }
        });
    }

    /**
     * Queue lightning strike at player position
     */
    public void queueLightningStrike(String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = player.getBlockPos();

                // Spawn visual-only lightning
                LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                if (lightning != null) {
                    lightning.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos));
                    lightning.setCosmetic(true);
                    world.spawnEntity(lightning);
                }

                // Play lightning sound
                SoundEffects.playLightningSound(world, pos);

                // Explosion particles
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.getX(), pos.getY(), pos.getZ(),
                    1, 0, 0, 0, 0);

                player.sendMessage(Text.literal("§e§lThe chaos strikes with lightning!"), false);
                ChaosMod.LOGGER.info("Lightning struck at player {}", player.getName().getString());
            }
        });
    }

    /**
     * Queue helper mob spawn (Iron Golem or Wolf)
     */
    public void queueHelperSpawn(String helperType, String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = findSafeSpawnPosition(world, player.getBlockPos(), 3);

                // Play helper spawn sound
                SoundEffects.playHelperSpawnSound(world, pos);

                // Spawn helper based on type
                EntityType<?> helperEntityType = helperType.equalsIgnoreCase("wolf") ?
                    EntityType.WOLF : EntityType.IRON_GOLEM;

                var helper = helperEntityType.create(world);
                if (helper != null) {
                    helper.refreshPositionAndAngles(pos, 0, 0);

                    // Make wolf tamed if it's a wolf
                    if (helper instanceof net.minecraft.entity.passive.WolfEntity wolf) {
                        wolf.setOwner(player);
                        wolf.setTamed(true);
                    }

                    world.spawnEntity(helper);

                    // Friendly particles
                    world.spawnParticles(ParticleTypes.HEART,
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        15, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        20, 0.5, 0.5, 0.5, 0.05);

                    player.sendMessage(Text.literal("§a§lA " + helperType + " has come to help!"), false);
                    ChaosMod.LOGGER.info("Spawned {} helper for player {}", helperType, player.getName().getString());
                }
            }
        });
    }

    /**
     * Queue buff effect
     */
    public void queueBuffEffect(String effectType, String playerName, int duration, int amplifier) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = player.getBlockPos();

                // Play buff sound
                SoundEffects.playBuffSound(world, pos);

                // Apply effect
                net.minecraft.entity.effect.StatusEffect effect = null;
                String effectName = "";

                switch (effectType.toLowerCase()) {
                    case "speed":
                        effect = net.minecraft.entity.effect.StatusEffects.SPEED;
                        effectName = "Speed";
                        break;
                    case "regeneration":
                        effect = net.minecraft.entity.effect.StatusEffects.REGENERATION;
                        effectName = "Regeneration";
                        break;
                    case "resistance":
                        effect = net.minecraft.entity.effect.StatusEffects.RESISTANCE;
                        effectName = "Resistance";
                        break;
                    case "strength":
                        effect = net.minecraft.entity.effect.StatusEffects.STRENGTH;
                        effectName = "Strength";
                        break;
                    case "jump":
                        effect = net.minecraft.entity.effect.StatusEffects.JUMP_BOOST;
                        effectName = "Jump Boost";
                        break;
                }

                if (effect != null) {
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        effect, duration * 20, amplifier));

                    // Magical particles
                    world.spawnParticles(ParticleTypes.ENCHANT,
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        30, 0.5, 1, 0.5, 0.5);
                    world.spawnParticles(ParticleTypes.END_ROD,
                        pos.getX(), pos.getY() + 1, pos.getZ(),
                        15, 0.5, 1, 0.5, 0.1);

                    player.sendMessage(Text.literal("§a§l" + effectName + " granted by the chaos!"), false);
                    ChaosMod.LOGGER.info("Applied {} buff to player {}", effectName, player.getName().getString());
                }
            }
        });
    }

    /**
     * Queue food spawn
     */
    public void queueFoodSpawn(String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = player.getBlockPos();

                // Random food items
                ItemStack[] foods = {
                    new ItemStack(Items.COOKED_BEEF, 5 + RANDOM.nextInt(5)),
                    new ItemStack(Items.GOLDEN_CARROT, 3 + RANDOM.nextInt(3)),
                    new ItemStack(Items.COOKED_PORKCHOP, 5 + RANDOM.nextInt(5)),
                    new ItemStack(Items.BREAD, 8 + RANDOM.nextInt(8))
                };

                ItemStack selectedFood = foods[RANDOM.nextInt(foods.length)];
                player.giveItemStack(selectedFood);

                // Sparkle particles
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX(), pos.getY() + 1, pos.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);

                player.sendMessage(Text.literal("§a§lFood has appeared in your inventory!"), false);
                ChaosMod.LOGGER.info("Gave food to player {}", player.getName().getString());
            }
        });
    }

    /**
     * Queue player heal
     */
    public void queueHealPlayer(String playerName) {
        spawnQueue.add(new SpawnCommand() {
            @Override
            public void execute(MinecraftServer server) {
                ServerPlayerEntity player = getPlayer(server, playerName);
                if (player == null) return;

                ServerWorld world = player.getServerWorld();
                BlockPos pos = player.getBlockPos();

                // Play heal sound
                SoundEffects.playHealSound(world, pos);

                // Heal player
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);

                // Healing particles
                world.spawnParticles(ParticleTypes.HEART,
                    pos.getX(), pos.getY() + 1, pos.getZ(),
                    30, 0.5, 1, 0.5, 0.1);
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    pos.getX(), pos.getY() + 1, pos.getZ(),
                    20, 0.5, 1, 0.5, 0.2);

                player.sendMessage(Text.literal("§a§lYou've been healed by the chaos!"), false);
                ChaosMod.LOGGER.info("Healed player {}", player.getName().getString());
            }
        });
    }

    /**
     * Find a safe spawn position near the target position
     */
    private BlockPos findSafeSpawnPosition(ServerWorld world, BlockPos target, int radius) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = target.getX() + RANDOM.nextInt(radius * 2) - radius;
            int z = target.getZ() + RANDOM.nextInt(radius * 2) - radius;
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isAir() || world.getBlockState(pos).isReplaceable()) {
                return pos;
            }
        }
        return target.up();
    }

    /**
     * Get a player by name, or return first player if name is null
     */
    private ServerPlayerEntity getPlayer(MinecraftServer server, String playerName) {
        if (playerName != null && !playerName.isEmpty()) {
            return server.getPlayerManager().getPlayer(playerName);
        }

        // Get first available player
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        return players.isEmpty() ? null : players.get(0);
    }

    /**
     * Add random loot to a chest
     */
    private void addRandomLoot(net.minecraft.block.entity.ChestBlockEntity chest, ServerWorld world) {
        // Simple random loot (you can make this more sophisticated)
        List<ItemStack> loot = Arrays.asList(
            new ItemStack(Items.DIAMOND, 1 + RANDOM.nextInt(3)),
            new ItemStack(Items.EMERALD, 1 + RANDOM.nextInt(5)),
            new ItemStack(Items.GOLDEN_APPLE, 1 + RANDOM.nextInt(2)),
            new ItemStack(Items.ENCHANTED_BOOK, 1),
            new ItemStack(Items.ENDER_PEARL, 1 + RANDOM.nextInt(4))
        );

        // Shuffle and add 2-4 items
        Collections.shuffle(loot);
        int itemCount = 2 + RANDOM.nextInt(3);

        for (int i = 0; i < Math.min(itemCount, loot.size()); i++) {
            chest.setStack(RANDOM.nextInt(27), loot.get(i));
        }
    }

    /**
     * Interface for queued spawn commands
     */
    private interface SpawnCommand {
        void execute(MinecraftServer server);
    }
}
