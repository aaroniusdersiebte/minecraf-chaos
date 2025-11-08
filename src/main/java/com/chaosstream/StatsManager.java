package com.chaosstream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager für Statistiken, Leaderboards und JSON-Export für OBS
 * Sammelt Daten von DefenderManager, ChaosManager, WaveManager
 */
public class StatsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosMod");
    private static StatsManager instance;

    private final File exportFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Caching für Performance
    private JsonObject lastExportedData;
    private long lastExportTime = 0;
    private static final long EXPORT_INTERVAL = 1000; // 1 Sekunde

    // Stats
    private int totalWavesCompleted = 0;
    private int totalDefendersSpawned = 0;
    private int totalDefendersFallen = 0;
    private long sessionStartTime;

    private StatsManager() {
        this.exportFile = new File("stream-stats.json");
        this.sessionStartTime = System.currentTimeMillis();
        LOGGER.info("StatsManager initialisiert - Export nach: {}", exportFile.getAbsolutePath());
    }

    public static StatsManager getInstance() {
        if (instance == null) {
            instance = new StatsManager();
        }
        return instance;
    }

    /**
     * Exportiert aktuelle Stats als JSON für OBS Browser-Source
     * Wird jede Sekunde gecached für Performance
     */
    public void exportStatsToJson(MinecraftServer server) {
        long now = System.currentTimeMillis();

        // Cache Check - nur alle 1 Sekunde neu exportieren
        if (now - lastExportTime < EXPORT_INTERVAL && lastExportedData != null) {
            return;
        }

        try {
            JsonObject root = new JsonObject();

            // ===== Basis-Informationen =====
            root.addProperty("timestamp", now);
            root.addProperty("session_duration", formatDuration(now - sessionStartTime));

            // ===== Chaos-Daten =====
            ChaosManager chaosManager = ChaosMod.getChaosManager();
            JsonObject chaosData = new JsonObject();
            chaosData.addProperty("current", chaosManager.getChaosLevel());
            chaosData.addProperty("total", chaosManager.getTotalChaos());
            chaosData.addProperty("multiplier", String.format("%.1fx", chaosManager.getSpawnMultiplier()));
            root.add("chaos", chaosData);

            // ===== Wave-Daten =====
            JsonObject waveData = new JsonObject();
            waveData.addProperty("current_wave", 0); // TODO: Von WaveManager holen wenn public
            waveData.addProperty("total_completed", totalWavesCompleted);
            waveData.addProperty("is_active", false); // TODO: Von WaveManager holen
            root.add("wave", waveData);

            // ===== Village Core Daten =====
            VillageManager villageManager = ChaosMod.getVillageManager();
            JsonObject coreData = new JsonObject();
            if (villageManager.hasVillageCore()) {
                coreData.addProperty("health", villageManager.getCoreHP());
                coreData.addProperty("max_health", villageManager.getMaxCoreHP());
                coreData.addProperty("health_percentage",
                    (villageManager.getCoreHP() * 100.0) / villageManager.getMaxCoreHP());
            } else {
                coreData.addProperty("health", 0);
                coreData.addProperty("max_health", 0);
                coreData.addProperty("health_percentage", 0);
            }
            root.add("village_core", coreData);

            // ===== Defender-Statistiken =====
            DefenderManager defenderManager = ChaosMod.getDefenderManager();
            JsonObject defenderStats = new JsonObject();
            defenderStats.addProperty("alive", defenderManager.getActiveDefenderCount());
            defenderStats.addProperty("total_spawned", totalDefendersSpawned);
            defenderStats.addProperty("fallen", totalDefendersFallen);

            // Klassen-Verteilung
            JsonObject classDistribution = new JsonObject();
            for (VillagerClass vClass : VillagerClass.values()) {
                int count = defenderManager.getDefendersByClass(vClass).size();
                classDistribution.addProperty(vClass.name().toLowerCase(), count);
            }
            defenderStats.add("class_distribution", classDistribution);

            root.add("defenders", defenderStats);

            // ===== Top 5 Defender (Leaderboard) =====
            JsonArray topDefenders = new JsonArray();
            List<DefenderVillager> allDefenders = defenderManager.getAllDefenders();

            // Sortiere nach Damage Dealt
            List<DefenderVillager> topByDamage = allDefenders.stream()
                .sorted(Comparator.comparingInt(DefenderVillager::getDamageDealt).reversed())
                .limit(5)
                .collect(Collectors.toList());

            for (int i = 0; i < topByDamage.size(); i++) {
                DefenderVillager defender = topByDamage.get(i);
                JsonObject defenderJson = new JsonObject();
                defenderJson.addProperty("rank", i + 1);
                defenderJson.addProperty("name", defender.getViewerName());
                defenderJson.addProperty("class", defender.getVillagerClass().getDisplayName());
                defenderJson.addProperty("level", defender.getLevel());
                defenderJson.addProperty("damage", defender.getDamageDealt());
                defenderJson.addProperty("kills", defender.getKills());
                defenderJson.addProperty("waves", defender.getWavesCompleted());
                defenderJson.addProperty("healing", defender.getHealingDone());
                topDefenders.add(defenderJson);
            }
            root.add("top_defenders", topDefenders);

            // ===== Spieler-Info =====
            JsonArray playersArray = new JsonArray();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                JsonObject playerJson = new JsonObject();
                playerJson.addProperty("name", player.getName().getString());
                playerJson.addProperty("health", (int) player.getHealth());
                playerJson.addProperty("max_health", (int) player.getMaxHealth());
                playerJson.addProperty("food_level", player.getHungerManager().getFoodLevel());
                playersArray.add(playerJson);
            }
            root.add("players", playersArray);

            // Schreibe in Datei
            try (FileWriter writer = new FileWriter(exportFile)) {
                gson.toJson(root, writer);
            }

            lastExportedData = root;
            lastExportTime = now;

        } catch (IOException e) {
            LOGGER.error("Fehler beim Exportieren der Stats: {}", e.getMessage());
        }
    }

    /**
     * Gibt Top N Defender nach bestimmter Metrik zurück
     */
    public List<DefenderVillager> getTopDefenders(int limit, LeaderboardType type) {
        List<DefenderVillager> allDefenders = ChaosMod.getDefenderManager().getAllDefenders();

        Comparator<DefenderVillager> comparator;
        switch (type) {
            case DAMAGE:
                comparator = Comparator.comparingInt(DefenderVillager::getDamageDealt).reversed();
                break;
            case KILLS:
                comparator = Comparator.comparingInt(DefenderVillager::getKills).reversed();
                break;
            case LEVEL:
                comparator = Comparator.comparingInt(DefenderVillager::getLevel)
                    .thenComparingInt(DefenderVillager::getXp)
                    .reversed();
                break;
            case WAVES:
                comparator = Comparator.comparingInt(DefenderVillager::getWavesCompleted).reversed();
                break;
            case HEALING:
                comparator = Comparator.comparingInt(DefenderVillager::getHealingDone).reversed();
                break;
            default:
                comparator = Comparator.comparingInt(DefenderVillager::getDamageDealt).reversed();
        }

        return allDefenders.stream()
            .sorted(comparator)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Zeigt Leaderboard im Chat an
     */
    public void showLeaderboard(ServerPlayerEntity player, LeaderboardType type, int limit) {
        List<DefenderVillager> topDefenders = getTopDefenders(limit, type);

        if (topDefenders.isEmpty()) {
            player.sendMessage(Text.literal("§cKeine Defender vorhanden!"), false);
            return;
        }

        // Header
        player.sendMessage(Text.literal("§6§l=== TOP " + limit + " DEFENDER (" + type.getDisplayName() + ") ==="), false);

        // Liste
        for (int i = 0; i < topDefenders.size(); i++) {
            DefenderVillager defender = topDefenders.get(i);
            String rank = getRankEmoji(i + 1);
            String className = defender.getVillagerClass().getColorCode() + defender.getVillagerClass().getDisplayName();

            String statValue;
            switch (type) {
                case DAMAGE:
                    statValue = "§e" + defender.getDamageDealt() + " Schaden";
                    break;
                case KILLS:
                    statValue = "§e" + defender.getKills() + " Kills";
                    break;
                case LEVEL:
                    statValue = "§e" + defender.getLevel() + " (" + defender.getXp() + " XP)";
                    break;
                case WAVES:
                    statValue = "§e" + defender.getWavesCompleted() + " Waves";
                    break;
                case HEALING:
                    statValue = "§a" + defender.getHealingDone() + " HP";
                    break;
                default:
                    statValue = "§e???";
            }

            player.sendMessage(Text.literal(
                rank + " §b" + defender.getViewerName() + " §7(" + className + "§7) §f- " + statValue
            ), false);
        }

        player.sendMessage(Text.literal("§6§l" + "=".repeat(40)), false);
    }

    /**
     * Gibt Emoji für Rang zurück
     */
    private String getRankEmoji(int rank) {
        switch (rank) {
            case 1: return "§6🥇";
            case 2: return "§7🥈";
            case 3: return "§c🥉";
            default: return "§7#" + rank;
        }
    }

    /**
     * Formatiert Millisekunden zu lesbarer Dauer
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // ===== Event Callbacks (von anderen Managern aufgerufen) =====

    public void onWaveCompleted() {
        totalWavesCompleted++;
    }

    public void onDefenderSpawned() {
        totalDefendersSpawned++;
    }

    public void onDefenderFallen() {
        totalDefendersFallen++;
    }

    // ===== Getters =====

    public int getTotalWavesCompleted() {
        return totalWavesCompleted;
    }

    public int getTotalDefendersSpawned() {
        return totalDefendersSpawned;
    }

    public int getTotalDefendersFallen() {
        return totalDefendersFallen;
    }

    /**
     * Enum für Leaderboard-Typen
     */
    public enum LeaderboardType {
        DAMAGE("Schaden"),
        KILLS("Kills"),
        LEVEL("Level"),
        WAVES("Waves überlebt"),
        HEALING("Heilung");

        private final String displayName;

        LeaderboardType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static LeaderboardType fromString(String str) {
            try {
                return valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DAMAGE; // Default
            }
        }
    }
}
