package com.chaosstream;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Centralized sound effect management for Chaos Stream Mod
 */
public class SoundEffects {

    /**
     * Play warning sound for wave approaching
     */
    public static void playWarningSound(ServerWorld world, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // Ender dragon growl - ominous warning
            player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
    }

    /**
     * Play dramatic wave spawn sound
     */
    public static void playWaveSpawnSound(ServerWorld world, BlockPos pos, int waveNumber) {
        float pitch = 0.8f + (waveNumber * 0.1f); // Pitch increases with wave number

        // Raid horn for dramatic effect
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.EVENT_RAID_HORN, SoundCategory.HOSTILE, 2.0f, pitch, 0L);

        // Add wither spawn sound for waves 4-5
        if (waveNumber >= 4) {
            world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 1.0f, 0L);
        }
    }

    /**
     * Play continuous portal ambient sound
     */
    public static void playPortalAmbient(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, 0.5f, 1.0f, 0L);
    }

    /**
     * Play cooldown ambient sound
     */
    public static void playCooldownSound(ServerWorld world, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            // Subtle portal ambient for tension
            player.playSound(SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.7f);
        }
    }

    /**
     * Play creeper spawn sound
     */
    public static void playCreeperSpawnSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0f, 1.2f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.5f, 1.5f, 0L);
    }

    /**
     * Play lootbox spawn sound
     */
    public static void playLootboxSpawnSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.AMBIENT, 0.8f, 1.0f, 0L);
    }

    /**
     * Play villager spawn sound
     */
    public static void playVillagerSpawnSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.NEUTRAL, 0.7f, 1.3f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.8f, 1.0f, 0L);
    }

    /**
     * Play lightning strike sound
     */
    public static void playLightningSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.5f, 1.0f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 1.0f, 1.0f, 0L);
    }

    /**
     * Play TNT spawn sound
     */
    public static void playTNTSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0f, 1.0f, 0L);
    }

    /**
     * Play teleport sound
     */
    public static void playTeleportSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 0.8f, 1.2f, 0L);
    }

    /**
     * Play healing sound
     */
    public static void playHealSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.5f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f, 0L);
    }

    /**
     * Play buff/positive effect sound
     */
    public static void playBuffSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.3f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.7f, 1.0f, 0L);
    }

    /**
     * Play boss mob spawn sound
     */
    public static void playBossSpawnSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0f, 0.8f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.5f, 0.7f, 0L);
    }

    /**
     * Play helper spawn sound (friendly)
     */
    public static void playHelperSpawnSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.0f, 0.9f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.NEUTRAL, 0.8f, 1.0f, 0L);
    }

    /**
     * Play weather change sound
     */
    public static void playWeatherChangeSound(ServerWorld world, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            player.playSound(SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.WEATHER, 0.8f, 1.0f);
        }
    }

    /**
     * Play anvil drop sound
     */
    public static void playAnvilDropSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.5f, 0.8f, 0L);
    }

    /**
     * Play core hit sound (when village core takes damage)
     */
    public static void playCoreHitSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.2f, 0.7f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_GENERIC_HURT, SoundCategory.HOSTILE, 0.8f, 0.5f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.6f, 0.8f, 0L);
    }

    /**
     * Play game over sound (when village core is destroyed)
     */
    public static void playGameOverSound(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 2.0f, 0.5f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 0.6f, 0L);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 1.0f, 0.8f, 0L);
    }
}
