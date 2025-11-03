package com.chaosstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class VillageManager {
    private static final String SAVE_FILE = "village-data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int MAX_CORE_HP = 100;

    private BlockPos villageCorePos = null;
    private int coreHP = MAX_CORE_HP;
    private boolean gameOver = false;

    public VillageManager() {
        load();
    }

    /**
     * Set village core position
     */
    public void setVillageCore(BlockPos pos) {
        this.villageCorePos = pos;
        this.coreHP = MAX_CORE_HP;
        this.gameOver = false;
        save();
        ChaosMod.LOGGER.info("Village core set at: {}", pos);
    }

    /**
     * Check if village core is set
     */
    public boolean hasVillageCore() {
        return villageCorePos != null;
    }

    /**
     * Get village core position
     */
    public BlockPos getVillageCorePos() {
        return villageCorePos;
    }

    /**
     * Damage the core
     */
    public void damageCore(int damage) {
        if (!hasVillageCore() || gameOver) return;

        coreHP = Math.max(0, coreHP - damage);
        ChaosMod.LOGGER.info("Village core damaged! HP: {}/{}", coreHP, MAX_CORE_HP);

        if (coreHP <= 0) {
            gameOver = true;
            ChaosMod.LOGGER.info("GAME OVER - Village core destroyed!");
        }

        save();
    }

    /**
     * Repair the core (für Builder-Villagers)
     */
    public void repairCore(int amount, java.util.UUID builderUUID) {
        if (!hasVillageCore() || gameOver) return;

        int oldHP = coreHP;
        coreHP = Math.min(MAX_CORE_HP, coreHP + amount);

        if (coreHP > oldHP) {
            ChaosMod.LOGGER.debug("Village core repaired by {} HP (Builder: {}). HP: {}/{}",
                amount, builderUUID, coreHP, MAX_CORE_HP);
            save();
        }
    }

    /**
     * Get current core HP
     */
    public int getCoreHP() {
        return coreHP;
    }

    /**
     * Get max core HP
     */
    public int getMaxCoreHP() {
        return MAX_CORE_HP;
    }

    /**
     * Check if game is over
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * Reset village (remove core)
     */
    public void resetVillage() {
        villageCorePos = null;
        coreHP = MAX_CORE_HP;
        gameOver = false;
        save();
        ChaosMod.LOGGER.info("Village reset");
    }

    /**
     * Reset only HP (for new round)
     */
    public void resetHP() {
        coreHP = MAX_CORE_HP;
        gameOver = false;
        save();
        ChaosMod.LOGGER.info("Village core HP reset to {}", MAX_CORE_HP);
    }

    /**
     * Save village data to file
     */
    public void save() {
        try {
            File file = new File(SAVE_FILE);
            VillageData data = new VillageData(villageCorePos, coreHP, gameOver);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
                ChaosMod.LOGGER.debug("Village data saved");
            }
        } catch (IOException e) {
            ChaosMod.LOGGER.error("Failed to save village data", e);
        }
    }

    /**
     * Load village data from file
     */
    private void load() {
        try {
            File file = new File(SAVE_FILE);
            if (!file.exists()) {
                ChaosMod.LOGGER.info("No village data file found, starting fresh");
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                VillageData data = GSON.fromJson(reader, VillageData.class);
                if (data != null) {
                    this.villageCorePos = data.villageCorePos;
                    this.coreHP = data.coreHP;
                    this.gameOver = data.gameOver;
                    ChaosMod.LOGGER.info("Village data loaded - Core: {}, HP: {}", villageCorePos, coreHP);
                }
            }
        } catch (IOException e) {
            ChaosMod.LOGGER.error("Failed to load village data", e);
        }
    }

    /**
     * Data class for JSON serialization
     */
    private static class VillageData {
        BlockPos villageCorePos;
        int coreHP;
        boolean gameOver;

        VillageData(BlockPos villageCorePos, int coreHP, boolean gameOver) {
            this.villageCorePos = villageCorePos;
            this.coreHP = coreHP;
            this.gameOver = gameOver;
        }
    }
}
