package com.chaosstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TowerManager {
    private static final String SAVE_FILE = "tower-data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Tower> towers = new ArrayList<>();

    public TowerManager() {
        load();
    }

    /**
     * Add a new tower
     */
    public void addTower(Tower tower) {
        towers.add(tower);
        save();
        ChaosMod.LOGGER.info("Tower added at {} - Type: {}", tower.getPosition(), tower.getType().getDisplayName());
    }

    /**
     * Remove tower at position
     */
    public boolean removeTower(BlockPos pos) {
        Tower tower = getTowerAt(pos);
        if (tower != null) {
            towers.remove(tower);
            save();
            ChaosMod.LOGGER.info("Tower removed at {}", pos);
            return true;
        }
        return false;
    }

    /**
     * Remove tower by ID
     */
    public boolean removeTower(UUID id) {
        Tower tower = towers.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (tower != null) {
            towers.remove(tower);
            save();
            ChaosMod.LOGGER.info("Tower removed: {}", id);
            return true;
        }
        return false;
    }

    /**
     * Get tower at specific position
     */
    public Tower getTowerAt(BlockPos pos) {
        return towers.stream()
                .filter(t -> t.getPosition().equals(pos))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if there's a tower at position
     */
    public boolean hasTowerAt(BlockPos pos) {
        return getTowerAt(pos) != null;
    }

    /**
     * Get all towers
     */
    public List<Tower> getAllTowers() {
        return new ArrayList<>(towers);
    }

    /**
     * Get towers by type
     */
    public List<Tower> getTowersByType(TowerType type) {
        return towers.stream()
                .filter(t -> t.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Get towers by owner
     */
    public List<Tower> getTowersByOwner(UUID ownerUUID) {
        return towers.stream()
                .filter(t -> t.getOwnerUUID() != null && t.getOwnerUUID().equals(ownerUUID))
                .collect(Collectors.toList());
    }

    /**
     * Get tower count
     */
    public int getTowerCount() {
        return towers.size();
    }

    /**
     * Tick all towers (update cooldowns)
     */
    public void tick() {
        for (Tower tower : towers) {
            tower.tickCooldown();
        }
    }

    /**
     * Clear all towers
     */
    public void clearAllTowers() {
        towers.clear();
        save();
        ChaosMod.LOGGER.info("All towers cleared");
    }

    /**
     * Save tower data to file
     */
    public void save() {
        try {
            File file = new File(SAVE_FILE);
            JsonObject root = new JsonObject();
            JsonArray towerArray = new JsonArray();

            for (Tower tower : towers) {
                towerArray.add(tower.toJson());
            }

            root.add("towers", towerArray);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
                ChaosMod.LOGGER.debug("Tower data saved ({} towers)", towers.size());
            }
        } catch (IOException e) {
            ChaosMod.LOGGER.error("Failed to save tower data", e);
        }
    }

    /**
     * Load tower data from file
     */
    private void load() {
        try {
            File file = new File(SAVE_FILE);
            if (!file.exists()) {
                ChaosMod.LOGGER.info("No tower data file found, starting fresh");
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null && root.has("towers")) {
                    JsonArray towerArray = root.getAsJsonArray("towers");
                    towers.clear();

                    for (int i = 0; i < towerArray.size(); i++) {
                        JsonObject towerJson = towerArray.get(i).getAsJsonObject();
                        Tower tower = Tower.fromJson(towerJson);
                        if (tower != null) {
                            towers.add(tower);
                        }
                    }

                    ChaosMod.LOGGER.info("Tower data loaded - {} towers", towers.size());
                }
            }
        } catch (Exception e) {
            ChaosMod.LOGGER.error("Failed to load tower data", e);
        }
    }
}
