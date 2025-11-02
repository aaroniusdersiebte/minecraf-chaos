package com.chaosstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ChaosManager {
    private static final String SAVE_FILE = "chaos-data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int chaosLevel = 0;
    private int totalChaos = 0;
    private long lastDecayTime = System.currentTimeMillis();

    // Chaos decay settings (optional - chaos slowly decreases over time)
    private static final long DECAY_INTERVAL = 60000; // 1 minute
    private static final int DECAY_AMOUNT = 1;

    public ChaosManager() {
        load();
    }

    /**
     * Add chaos points
     */
    public void addChaos(int amount) {
        chaosLevel += amount;
        totalChaos += amount;
        ChaosMod.LOGGER.info("Chaos increased by {}. Current level: {}", amount, chaosLevel);
    }

    /**
     * Get current chaos level
     */
    public int getChaosLevel() {
        return chaosLevel;
    }

    /**
     * Get total accumulated chaos
     */
    public int getTotalChaos() {
        return totalChaos;
    }

    /**
     * Calculate spawn multiplier based on chaos level
     * Returns a value between 1.0 and 5.0
     */
    public double getSpawnMultiplier() {
        if (chaosLevel <= 0) return 1.0;
        if (chaosLevel <= 50) return 1.0 + (chaosLevel / 50.0);
        if (chaosLevel <= 100) return 2.0 + (chaosLevel - 50) / 50.0;
        if (chaosLevel <= 200) return 3.0 + (chaosLevel - 100) / 100.0;
        return Math.min(5.0, 4.0 + (chaosLevel - 200) / 200.0);
    }

    /**
     * Get number of mobs to spawn based on chaos
     */
    public int getMobSpawnCount() {
        if (chaosLevel <= 0) return 0;
        if (chaosLevel <= 25) return 1;
        if (chaosLevel <= 50) return 2;
        if (chaosLevel <= 100) return 3;
        if (chaosLevel <= 150) return 4;
        return 5;
    }

    /**
     * Process chaos decay over time
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDecayTime >= DECAY_INTERVAL) {
            if (chaosLevel > 0) {
                chaosLevel = Math.max(0, chaosLevel - DECAY_AMOUNT);
                ChaosMod.LOGGER.debug("Chaos decayed to: {}", chaosLevel);
            }
            lastDecayTime = currentTime;
        }
    }

    /**
     * Reset chaos to zero
     */
    public void reset() {
        chaosLevel = 0;
        ChaosMod.LOGGER.info("Chaos level reset to 0");
    }

    /**
     * Save chaos data to file
     */
    public void save() {
        try {
            File file = new File(SAVE_FILE);
            ChaosData data = new ChaosData(chaosLevel, totalChaos);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
                ChaosMod.LOGGER.info("Chaos data saved");
            }
        } catch (IOException e) {
            ChaosMod.LOGGER.error("Failed to save chaos data", e);
        }
    }

    /**
     * Load chaos data from file
     */
    private void load() {
        try {
            File file = new File(SAVE_FILE);
            if (!file.exists()) {
                ChaosMod.LOGGER.info("No chaos data file found, starting fresh");
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                ChaosData data = GSON.fromJson(reader, ChaosData.class);
                if (data != null) {
                    this.chaosLevel = data.chaosLevel;
                    this.totalChaos = data.totalChaos;
                    ChaosMod.LOGGER.info("Chaos data loaded - Level: {}, Total: {}", chaosLevel, totalChaos);
                }
            }
        } catch (IOException e) {
            ChaosMod.LOGGER.error("Failed to load chaos data", e);
        }
    }

    /**
     * Data class for JSON serialization
     */
    private static class ChaosData {
        int chaosLevel;
        int totalChaos;

        ChaosData(int chaosLevel, int totalChaos) {
            this.chaosLevel = chaosLevel;
            this.totalChaos = totalChaos;
        }
    }
}
