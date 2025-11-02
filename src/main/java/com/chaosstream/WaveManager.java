package com.chaosstream;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
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
    private static final int MIN_SPAWN_DISTANCE = 35;
    private static final int MAX_SPAWN_DISTANCE = 55;

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

    public void onServerTick(MinecraftServer server, ChaosManager chaosManager) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey() != ServerWorld.OVERWORLD) continue;

            long timeOfDay = world.getTimeOfDay() % 24000;
            boolean isNight = timeOfDay >= 13000 && timeOfDay <= 23000;

            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) continue;

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
        if (chaosLevel <= 0) return;

        long currentTick = world.getTime();

        // Initialize spawn locations at night start
        if (timeOfDay >= 13000 && timeOfDay < 13100 && currentPhase == WavePhase.INACTIVE) {
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

        for (ServerPlayerEntity player : players) {
            // Create 3 spawn locations per player
            for (int i = 0; i < SPAWN_LOCATIONS_COUNT; i++) {
                BlockPos spawnPos = findRandomSpawnLocation(world, player.getBlockPos());
                SpawnLocation location = new SpawnLocation(spawnPos, player.getUuid());
                spawnLocations.add(location);

                // Create initial visual marker
                createSpawnMarker(world, spawnPos, true);
            }

            player.sendMessage(Text.literal("§c§l⚠ CHAOS NIGHT BEGINS! ⚠"), false);
            player.sendMessage(Text.literal("§e" + SPAWN_LOCATIONS_COUNT + " spawn points have been marked!"), false);
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
    }

    private void resetWaves(MinecraftServer server) {
        currentPhase = WavePhase.INACTIVE;
        spawnLocations.clear();
        clearBossBars();
        ChaosMod.LOGGER.info("Wave system reset (day time)");
    }

    private BlockPos findRandomSpawnLocation(ServerWorld world, BlockPos playerPos) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = MIN_SPAWN_DISTANCE + RANDOM.nextDouble() * (MAX_SPAWN_DISTANCE - MIN_SPAWN_DISTANCE);

            int x = playerPos.getX() + (int)(Math.cos(angle) * distance);
            int z = playerPos.getZ() + (int)(Math.sin(angle) * distance);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos pos = new BlockPos(x, y, z);

            // Check if position is valid (not in water, not in lava)
            if (world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                return pos;
            }
        }

        // Fallback
        return playerPos.add(MIN_SPAWN_DISTANCE, 0, 0);
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

        // Create spawn locations
        for (int i = 0; i < SPAWN_LOCATIONS_COUNT; i++) {
            BlockPos spawnPos = findRandomSpawnLocation(world, player.getBlockPos());
            SpawnLocation location = new SpawnLocation(spawnPos, player.getUuid());
            spawnLocations.add(location);
            createSpawnMarker(world, spawnPos, true);
        }

        // Start wave 1 immediately
        currentPhase = WavePhase.WAVE_1;
        phaseStartTick = world.getTime();
        startWave(world, List.of(player), 1, chaosLevel);
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
