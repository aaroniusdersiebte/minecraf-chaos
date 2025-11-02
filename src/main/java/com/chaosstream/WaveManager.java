package com.chaosstream;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

public class WaveManager {
    private static final Random RANDOM = new Random();

    // Wave configuration
    private static final int SPAWN_LOCATIONS_COUNT = 3;
    private static final int MIN_SPAWN_DISTANCE = 40;
    private static final int MAX_SPAWN_DISTANCE = 60;

    // Wave phases
    private enum WavePhase {
        INACTIVE,
        WARNING,      // Visual markers appear
        WAVE_1,       // Weak mobs
        COOLDOWN_1,
        WAVE_2,       // Medium mobs
        COOLDOWN_2,
        WAVE_3,       // Strong mobs
        COOLDOWN_3,
        WAVE_4,       // Elite mobs (high chaos only)
        COOLDOWN_4,
        WAVE_5,       // Boss wave (very high chaos only)
        FINISHED
    }

    // Mob difficulty tiers
    private static final EntityType<?>[] TIER_1_MOBS = {
        EntityType.ZOMBIE, EntityType.SPIDER, EntityType.CAVE_SPIDER
    };

    private static final EntityType<?>[] TIER_2_MOBS = {
        EntityType.SKELETON, EntityType.CREEPER, EntityType.ZOMBIE
    };

    private static final EntityType<?>[] TIER_3_MOBS = {
        EntityType.WITCH, EntityType.ENDERMAN, EntityType.CREEPER, EntityType.SKELETON
    };

    private static final EntityType<?>[] TIER_4_MOBS = {
        EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.WITCH, EntityType.ENDERMAN
    };

    // Current wave state
    private WavePhase currentPhase = WavePhase.INACTIVE;
    private long phaseStartTick = 0;
    private List<SpawnLocation> spawnLocations = new ArrayList<>();
    private Map<UUID, CommandBossBar> playerBossBars = new HashMap<>();
    private CommandBossBar coreHPBossBar = null;
    private int coreCheckTimer = 0;

    public void onServerTick(MinecraftServer server, ChaosManager chaosManager) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey() != ServerWorld.OVERWORLD) continue;

            long timeOfDay = world.getTimeOfDay() % 24000;
            boolean isNight = timeOfDay >= 13000 && timeOfDay <= 23000;

            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) continue;

            // Update core HP boss bar
            updateCoreHPBossBar(server, players);

            // Check for mobs attacking the core (every 20 ticks = 1 second)
            coreCheckTimer++;
            if (coreCheckTimer >= 20) {
                coreCheckTimer = 0;
                checkCoreAttacks(world, players);
            }

            if (isNight) {
                processNightWaves(world, players, chaosManager, timeOfDay);
            } else {
                // Reset during day
                if (currentPhase != WavePhase.INACTIVE) {
                    resetWaves(server);
                }
            }
        }
    }

    private void processNightWaves(ServerWorld world, List<ServerPlayerEntity> players,
                                   ChaosManager chaosManager, long timeOfDay) {
        int chaosLevel = chaosManager.getChaosLevel();
        // Allow waves to start even at chaos 0 (minimum difficulty)
        if (chaosLevel < 0) chaosLevel = 0;

        long currentTick = world.getTime();

        // Initialize spawn locations at night start (larger window: 13000-13500 = 25 seconds)
        if (timeOfDay >= 13000 && timeOfDay < 13500 && currentPhase == WavePhase.INACTIVE) {
            startWaveNight(world, players);
            currentPhase = WavePhase.WARNING;
            phaseStartTick = currentTick;
            return;
        }

        // Process wave phases
        long ticksSincePhaseStart = currentTick - phaseStartTick;

        switch (currentPhase) {
            case WARNING:
                updateWarningPhase(world, players, ticksSincePhaseStart);
                if (ticksSincePhaseStart >= 600) { // 30 seconds
                    startWave(world, players, 1, chaosLevel);
                    currentPhase = WavePhase.WAVE_1;
                    phaseStartTick = currentTick;
                }
                break;

            case WAVE_1:
                if (ticksSincePhaseStart >= 100) { // 5 seconds spawn duration
                    currentPhase = WavePhase.COOLDOWN_1;
                    phaseStartTick = currentTick;
                }
                break;

            case COOLDOWN_1:
                updateBossBar(players, "Next wave in...", ticksSincePhaseStart, 1200);
                if (ticksSincePhaseStart >= 1200) { // 60 seconds
                    startWave(world, players, 2, chaosLevel);
                    currentPhase = WavePhase.WAVE_2;
                    phaseStartTick = currentTick;
                }
                break;

            case WAVE_2:
                if (ticksSincePhaseStart >= 100) {
                    currentPhase = WavePhase.COOLDOWN_2;
                    phaseStartTick = currentTick;
                }
                break;

            case COOLDOWN_2:
                updateBossBar(players, "Next wave in...", ticksSincePhaseStart, 1200);
                if (ticksSincePhaseStart >= 1200) {
                    startWave(world, players, 3, chaosLevel);
                    currentPhase = WavePhase.WAVE_3;
                    phaseStartTick = currentTick;
                }
                break;

            case WAVE_3:
                if (ticksSincePhaseStart >= 100) {
                    if (chaosLevel >= 100) {
                        currentPhase = WavePhase.COOLDOWN_3;
                    } else {
                        currentPhase = WavePhase.FINISHED;
                        clearBossBars();
                    }
                    phaseStartTick = currentTick;
                }
                break;

            case COOLDOWN_3:
                updateBossBar(players, "Elite wave incoming...", ticksSincePhaseStart, 1200);
                if (ticksSincePhaseStart >= 1200) {
                    startWave(world, players, 4, chaosLevel);
                    currentPhase = WavePhase.WAVE_4;
                    phaseStartTick = currentTick;
                }
                break;

            case WAVE_4:
                if (ticksSincePhaseStart >= 100) {
                    if (chaosLevel >= 200) {
                        currentPhase = WavePhase.COOLDOWN_4;
                    } else {
                        currentPhase = WavePhase.FINISHED;
                        clearBossBars();
                    }
                    phaseStartTick = currentTick;
                }
                break;

            case COOLDOWN_4:
                updateBossBar(players, "BOSS WAVE INCOMING!", ticksSincePhaseStart, 1200);
                if (ticksSincePhaseStart >= 1200) {
                    startWave(world, players, 5, chaosLevel);
                    currentPhase = WavePhase.WAVE_5;
                    phaseStartTick = currentTick;
                }
                break;

            case WAVE_5:
                if (ticksSincePhaseStart >= 100) {
                    currentPhase = WavePhase.FINISHED;
                    clearBossBars();
                    phaseStartTick = currentTick;
                }
                break;
        }

        // Update spawn location effects
        if (currentPhase != WavePhase.INACTIVE && currentPhase != WavePhase.FINISHED) {
            updateSpawnLocationEffects(world);
        }
    }

    private void startWaveNight(ServerWorld world, List<ServerPlayerEntity> players) {
        spawnLocations.clear();

        // Check if village core is set
        VillageManager villageManager = ChaosMod.getVillageManager();
        if (!villageManager.hasVillageCore()) {
            // No village core - warn admins but don't spawn
            for (ServerPlayerEntity player : players) {
                if (player.hasPermissionLevel(2)) { // Op level
                    player.sendMessage(Text.literal("§c§l[TD] No village core set! Use '/chaos setvillage' to set spawn target."), false);
                }
            }
            return;
        }

        // Check if game is over
        if (villageManager.isGameOver()) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.literal("§c§l[TD] Village core destroyed! Use '/chaos resetvillage' to start a new round."), false);
            }
            return;
        }

        BlockPos villageCorePos = villageManager.getVillageCorePos();

        // Create spawn locations around village core (not per player)
        for (int i = 0; i < SPAWN_LOCATIONS_COUNT; i++) {
            BlockPos spawnPos = findRandomSpawnLocation(world, villageCorePos);
            // Use first player's UUID as owner (tower defense is cooperative)
            SpawnLocation location = new SpawnLocation(spawnPos, players.get(0).getUuid());
            spawnLocations.add(location);

            // Create initial visual marker
            createSpawnMarker(world, spawnPos, true);
        }

        // Notify all players
        for (ServerPlayerEntity player : players) {
            player.sendMessage(Text.literal("§c§l⚠ MONSTER WAVE APPROACHING! ⚠"), false);
            player.sendMessage(Text.literal("§e§lDefend the village core at " + villageCorePos.toShortString() + "!"), false);
            player.sendMessage(Text.literal("§e" + SPAWN_LOCATIONS_COUNT + " spawn points marked around the village!"), false);
        }

        // Play warning sound
        SoundEffects.playWarningSound(world, players);
    }

    private void updateWarningPhase(ServerWorld world, List<ServerPlayerEntity> players, long ticks) {
        // Update boss bar
        float progress = 1.0f - (ticks / 600.0f);
        updateBossBar(players, "§c⚠ Wave 1 approaching...", ticks, 600);
    }

    private void startWave(ServerWorld world, List<ServerPlayerEntity> players, int waveNumber, int chaosLevel) {
        String waveName = getWaveName(waveNumber);
        String waveColor = getWaveColor(waveNumber);

        for (ServerPlayerEntity player : players) {
            player.sendMessage(Text.literal(waveColor + "§l⚔ " + waveName + " WAVE SPAWNING! ⚔"), false);
        }

        // Spawn mobs at each location
        int mobsPerLocation = getMobCount(waveNumber, chaosLevel);
        EntityType<?>[] mobTypes = getMobTypes(waveNumber);

        for (SpawnLocation loc : spawnLocations) {
            spawnWaveMobs(world, loc.pos, mobTypes, mobsPerLocation);

            // Dramatic spawn effects
            createSpawnEffect(world, loc.pos, players);

            // Play wave spawn sound at each location
            SoundEffects.playWaveSpawnSound(world, loc.pos, waveNumber);
        }
    }

    private void spawnWaveMobs(ServerWorld world, BlockPos center, EntityType<?>[] mobTypes, int count) {
        for (int i = 0; i < count; i++) {
            // Random position around spawn center
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = 3 + RANDOM.nextDouble() * 5;

            int x = center.getX() + (int)(Math.cos(angle) * distance);
            int z = center.getZ() + (int)(Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos spawnPos = new BlockPos(x, y, z);
            EntityType<?> mobType = mobTypes[RANDOM.nextInt(mobTypes.length)];

            SpawnUtils.spawnMob(world, mobType, spawnPos);
        }
    }

    private void createSpawnMarker(ServerWorld world, BlockPos pos, boolean initial) {
        // Create obsidian portal ring
        if (initial) {
            // Small obsidian circle at spawn point
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                        if (Math.abs(x) + Math.abs(z) <= 3) {
                            BlockPos markerPos = pos.add(x, -1, z);
                            if (world.getBlockState(markerPos).isReplaceable()) {
                                world.setBlockState(markerPos, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                            }
                        }
                    }
                }
            }

            // Add some glowstone for visibility
            BlockPos glowPos = pos.add(0, 0, 0);
            if (world.getBlockState(glowPos).isReplaceable()) {
                world.setBlockState(glowPos, net.minecraft.block.Blocks.GLOWSTONE.getDefaultState());
            }
        }
    }

    private void createSpawnEffect(ServerWorld world, BlockPos pos, List<ServerPlayerEntity> players) {
        // Spawn visual-only lightning strike
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos));
            lightning.setCosmetic(true); // Visual only - no damage or fire
            world.spawnEntity(lightning);
        }

        // Play lightning sound
        SoundEffects.playLightningSound(world, pos);

        // Dramatic particle effects
        for (int i = 0; i < 50; i++) {
            double offsetX = (RANDOM.nextDouble() - 0.5) * 8;
            double offsetZ = (RANDOM.nextDouble() - 0.5) * 8;
            double offsetY = RANDOM.nextDouble() * 3;

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                1, 0, 0.5, 0, 0.05);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                1, 0, 0.5, 0, 0.02);
        }
    }

    private void updateSpawnLocationEffects(ServerWorld world) {
        for (SpawnLocation loc : spawnLocations) {
            // Periodic particle effects
            if (world.getTime() % 10 == 0) {
                world.spawnParticles(ParticleTypes.PORTAL,
                    loc.pos.getX(), loc.pos.getY() + 1, loc.pos.getZ(),
                    5, 0.5, 1, 0.5, 0.1);
            }

            // Periodic portal ambient sound (every 5 seconds)
            if (world.getTime() % 100 == 0) {
                SoundEffects.playPortalAmbient(world, loc.pos);
            }
        }
    }

    private void updateBossBar(List<ServerPlayerEntity> players, String title, long currentTicks, long maxTicks) {
        float progress = Math.max(0, 1.0f - (currentTicks / (float)maxTicks));

        for (ServerPlayerEntity player : players) {
            CommandBossBar bossBar = playerBossBars.computeIfAbsent(player.getUuid(), uuid -> {
                Identifier id = new Identifier("chaosstream", "wave_" + uuid);
                CommandBossBar bar = new CommandBossBar(id, Text.literal(title));
                bar.setColor(BossBar.Color.RED);
                bar.setStyle(BossBar.Style.NOTCHED_10);
                bar.addPlayer(player);
                return bar;
            });

            bossBar.setName(Text.literal(title));
            bossBar.setPercent(progress);
        }
    }

    private void clearBossBars() {
        for (CommandBossBar bar : playerBossBars.values()) {
            bar.clearPlayers();
        }
        playerBossBars.clear();

        // Also clear core HP boss bar
        if (coreHPBossBar != null) {
            coreHPBossBar.clearPlayers();
            coreHPBossBar = null;
        }
    }

    private void updateCoreHPBossBar(MinecraftServer server, List<ServerPlayerEntity> players) {
        VillageManager villageManager = ChaosMod.getVillageManager();

        // Remove boss bar if no village core
        if (!villageManager.hasVillageCore()) {
            if (coreHPBossBar != null) {
                coreHPBossBar.clearPlayers();
                coreHPBossBar = null;
            }
            return;
        }

        // Create or get existing boss bar
        if (coreHPBossBar == null) {
            Identifier id = new Identifier("chaosstream", "core_hp");
            // Try to get existing bar first (prevents duplicates on reload)
            coreHPBossBar = server.getBossBarManager().get(id);
            if (coreHPBossBar == null) {
                coreHPBossBar = server.getBossBarManager().add(id, Text.literal("Village Core"));
            }
        }

        // Update boss bar
        int currentHP = villageManager.getCoreHP();
        int maxHP = villageManager.getMaxCoreHP();
        float percent = (float) currentHP / (float) maxHP;

        // Color based on HP percentage
        BossBar.Color color;
        if (percent > 0.66f) {
            color = BossBar.Color.GREEN;
        } else if (percent > 0.33f) {
            color = BossBar.Color.YELLOW;
        } else {
            color = BossBar.Color.RED;
        }

        String statusIcon = villageManager.isGameOver() ? "§c✖" : "§a❤";
        coreHPBossBar.setName(Text.literal(statusIcon + " §eVillage Core: §f" + currentHP + "/" + maxHP));
        coreHPBossBar.setPercent(percent);
        coreHPBossBar.setColor(color);

        // Add all players to boss bar
        for (ServerPlayerEntity player : players) {
            coreHPBossBar.addPlayer(player);
        }
    }

    private void resetWaves(MinecraftServer server) {
        currentPhase = WavePhase.INACTIVE;
        spawnLocations.clear();
        clearBossBars();
        ChaosMod.LOGGER.info("Wave system reset (day time)");
    }

    private BlockPos findRandomSpawnLocation(ServerWorld world, BlockPos centerPos) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = MIN_SPAWN_DISTANCE + RANDOM.nextDouble() * (MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE);

            int x = centerPos.getX() + (int)(Math.cos(angle) * distance);
            int z = centerPos.getZ() + (int)(Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos pos = new BlockPos(x, y, z);

            // Check if position is valid (not in water, not in lava)
            if (world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos;
            }
        }

        // Fallback
        return centerPos.add(MIN_SPAWN_DISTANCE, 0, 0);
    }

    private int getMobCount(int waveNumber, int chaosLevel) {
        int baseCount = 3;

        switch (waveNumber) {
            case 1: baseCount = 4; break;
            case 2: baseCount = 6; break;
            case 3: baseCount = 8; break;
            case 4: baseCount = 10; break;
            case 5: baseCount = 12; break;
        }

        // Scale with chaos level
        if (chaosLevel <= 50) return baseCount;
        if (chaosLevel <= 100) return (int)(baseCount * 1.5);
        if (chaosLevel <= 150) return baseCount * 2;
        return (int)(baseCount * 2.5);
    }

    private EntityType<?>[] getMobTypes(int waveNumber) {
        switch (waveNumber) {
            case 1: return TIER_1_MOBS;
            case 2: return TIER_2_MOBS;
            case 3: return TIER_3_MOBS;
            case 4:
            case 5: return TIER_4_MOBS;
            default: return TIER_1_MOBS;
        }
    }

    private String getWaveName(int waveNumber) {
        switch (waveNumber) {
            case 1: return "FIRST";
            case 2: return "SECOND";
            case 3: return "THIRD";
            case 4: return "ELITE";
            case 5: return "BOSS";
            default: return "WAVE " + waveNumber;
        }
    }

    private String getWaveColor(int waveNumber) {
        switch (waveNumber) {
            case 1: return "§e"; // Yellow
            case 2: return "§6"; // Gold
            case 3: return "§c"; // Red
            case 4: return "§5"; // Purple
            case 5: return "§4§l"; // Dark Red Bold
            default: return "§f";
        }
    }

    public void forceStartWave(ServerWorld world, ServerPlayerEntity player, int chaosLevel) {
        spawnLocations.clear();

        // Check if village core is set
        VillageManager villageManager = ChaosMod.getVillageManager();
        if (!villageManager.hasVillageCore()) {
            player.sendMessage(Text.literal("§c§l[TD] No village core set! Use '/chaos setvillage' first."), false);
            return;
        }

        BlockPos villageCorePos = villageManager.getVillageCorePos();

        // Create spawn locations around village core
        for (int i = 0; i < SPAWN_LOCATIONS_COUNT; i++) {
            BlockPos spawnPos = findRandomSpawnLocation(world, villageCorePos);
            SpawnLocation location = new SpawnLocation(spawnPos, player.getUuid());
            spawnLocations.add(location);
            createSpawnMarker(world, spawnPos, true);
        }

        // Start wave 1 immediately
        currentPhase = WavePhase.WAVE_1;
        phaseStartTick = world.getTime();
        startWave(world, List.of(player), 1, chaosLevel);
    }

    private void checkCoreAttacks(ServerWorld world, List<ServerPlayerEntity> players) {
        VillageManager villageManager = ChaosMod.getVillageManager();

        // Only check if core exists and game is not over
        if (!villageManager.hasVillageCore() || villageManager.isGameOver()) {
            return;
        }

        BlockPos corePos = villageManager.getVillageCorePos();
        double attackRange = 3.0;

        // Find all hostile mobs near the core (any alive mob in range)
        List<MobEntity> nearbyMobs = world.getEntitiesByClass(
            MobEntity.class,
            new net.minecraft.util.math.Box(corePos).expand(attackRange),
            mob -> mob.isAlive() && mob.getType().getSpawnGroup().isPeaceful() == false
        );

        // Process each mob attacking the core
        for (MobEntity mob : nearbyMobs) {
            // Calculate damage based on mob type
            int damage = getMobCoreDamage(mob.getType());

            // Apply damage to core
            villageManager.damageCore(damage);

            // Visual and sound effects
            createCoreHitEffect(world, corePos);

            // Broadcast damage message
            for (ServerPlayerEntity player : players) {
                player.sendMessage(
                    Text.literal("§c§l[!] Core attacked by " + mob.getType().getName().getString() +
                                 "! HP: §e" + villageManager.getCoreHP() + "/" + villageManager.getMaxCoreHP()),
                    false
                );
            }

            // Remove mob after attack
            mob.discard();

            // Check for game over
            if (villageManager.isGameOver()) {
                handleGameOver(world, players);
                return;
            }
        }
    }

    private int getMobCoreDamage(EntityType<?> mobType) {
        // Different mobs deal different damage to the core
        if (mobType == EntityType.CREEPER) return 10;
        if (mobType == EntityType.RAVAGER) return 8;
        if (mobType == EntityType.WITHER_SKELETON) return 5;
        if (mobType == EntityType.BLAZE) return 5;
        if (mobType == EntityType.WITCH) return 4;
        if (mobType == EntityType.ENDERMAN) return 3;
        if (mobType == EntityType.SKELETON) return 2;
        if (mobType == EntityType.ZOMBIE) return 2;
        if (mobType == EntityType.SPIDER) return 1;
        if (mobType == EntityType.CAVE_SPIDER) return 1;
        return 2; // Default damage
    }

    private void createCoreHitEffect(ServerWorld world, BlockPos corePos) {
        // Explosion particles
        world.spawnParticles(
            ParticleTypes.EXPLOSION,
            corePos.getX() + 0.5,
            corePos.getY() + 1.0,
            corePos.getZ() + 0.5,
            5, 0.5, 0.5, 0.5, 0.1
        );

        // Soul fire flames
        world.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            corePos.getX() + 0.5,
            corePos.getY() + 1.0,
            corePos.getZ() + 0.5,
            10, 0.3, 0.3, 0.3, 0.05
        );

        // Damage sound
        SoundEffects.playCoreHitSound(world, corePos);
    }

    private void handleGameOver(ServerWorld world, List<ServerPlayerEntity> players) {
        // Clear all wave state
        currentPhase = WavePhase.INACTIVE;
        spawnLocations.clear();
        clearBossBars();

        // Broadcast game over message
        for (ServerPlayerEntity player : players) {
            player.sendMessage(Text.literal(""), false);
            player.sendMessage(Text.literal("§4§l═══════════════════════════════"), false);
            player.sendMessage(Text.literal("§c§l         VILLAGE DESTROYED!"), false);
            player.sendMessage(Text.literal("§e    The monsters have won..."), false);
            player.sendMessage(Text.literal("§7  Use §e/chaos resetvillage §7to try again"), false);
            player.sendMessage(Text.literal("§4§l═══════════════════════════════"), false);
            player.sendMessage(Text.literal(""), false);
        }

        // Massive explosion effect at core
        VillageManager villageManager = ChaosMod.getVillageManager();
        BlockPos corePos = villageManager.getVillageCorePos();

        world.spawnParticles(
            ParticleTypes.EXPLOSION_EMITTER,
            corePos.getX() + 0.5,
            corePos.getY() + 1.0,
            corePos.getZ() + 0.5,
            3, 0, 0, 0, 0
        );

        // Game over sound
        SoundEffects.playGameOverSound(world, corePos);

        ChaosMod.LOGGER.info("GAME OVER - Village core destroyed at {}", corePos);
    }

    private static class SpawnLocation {
        final BlockPos pos;
        final UUID playerUuid;

        SpawnLocation(BlockPos pos, UUID playerUuid) {
            this.pos = pos;
            this.playerUuid = playerUuid;
        }
    }
}
