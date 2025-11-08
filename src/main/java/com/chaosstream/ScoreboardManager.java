package com.chaosstream;

import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manager für Live-Scoreboard (Sidebar)
 * Zeigt Echtzeit-Infos: Chaos, Defender, Core HP, Wave Status
 */
public class ScoreboardManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosMod");
    private static final String OBJECTIVE_NAME = "chaos_stats";
    private static final String DISPLAY_TITLE = "§6§l⚔ CHAOS STREAM ⚔";

    private boolean enabled = false;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 1000; // 1 Sekunde

    /**
     * Aktiviert das Scoreboard für alle Spieler
     */
    public void enable(MinecraftServer server) {
        if (enabled) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            createScoreboardForPlayer(player);
        }

        enabled = true;
        LOGGER.info("Scoreboard aktiviert");
    }

    /**
     * Deaktiviert das Scoreboard für alle Spieler
     */
    public void disable(MinecraftServer server) {
        if (!enabled) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            removeScoreboardForPlayer(player);
        }

        enabled = false;
        LOGGER.info("Scoreboard deaktiviert");
    }

    /**
     * Togglet Scoreboard On/Off
     */
    public boolean toggle(MinecraftServer server) {
        if (enabled) {
            disable(server);
        } else {
            enable(server);
        }
        return enabled;
    }

    /**
     * Aktualisiert das Scoreboard (alle 1 Sekunde)
     */
    public void tick(MinecraftServer server) {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_INTERVAL) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateScoreboardForPlayer(player);
        }

        lastUpdateTime = now;
    }

    /**
     * Erstellt Scoreboard für einen Spieler
     */
    private void createScoreboardForPlayer(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();

        // Entferne altes Objective falls vorhanden
        ScoreboardObjective existingObjective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existingObjective != null) {
            scoreboard.removeObjective(existingObjective);
        }

        // Erstelle neues Objective
        ScoreboardObjective objective = scoreboard.addObjective(
            OBJECTIVE_NAME,
            ScoreboardCriterion.DUMMY,
            Text.literal(DISPLAY_TITLE),
            ScoreboardCriterion.RenderType.INTEGER
        );

        // Setze als Sidebar (Slot 1 = SIDEBAR)
        scoreboard.setObjectiveSlot(1, objective);

        // Initial Update
        updateScoreboardForPlayer(player);
    }

    /**
     * Entfernt Scoreboard von einem Spieler
     */
    private void removeScoreboardForPlayer(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);

        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    /**
     * Aktualisiert Scoreboard-Inhalt für einen Spieler
     */
    private void updateScoreboardForPlayer(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective(OBJECTIVE_NAME);

        if (objective == null) {
            createScoreboardForPlayer(player);
            return;
        }

        // Hole Manager
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        DefenderManager defenderManager = ChaosMod.getDefenderManager();
        VillageManager villageManager = ChaosMod.getVillageManager();

        // Cleane alte Scores - entferne alle Player aus diesem Objective
        for (String playerName : scoreboard.getKnownPlayers()) {
            scoreboard.resetPlayerScore(playerName, objective);
        }

        // Line Counter (von unten nach oben)
        int line = 15;

        // === ZEILE: Leerzeile ===
        setScore(scoreboard, objective, " ", line--);

        // === ZEILE: Chaos Level ===
        int chaosLevel = chaosManager.getChaosLevel();
        String chaosColor = getChaosColor(chaosLevel);
        setScore(scoreboard, objective, chaosColor + "⚡ Chaos: §f" + chaosLevel, line--);

        // === ZEILE: Chaos Multiplier ===
        double multiplier = chaosManager.getSpawnMultiplier();
        setScore(scoreboard, objective, "§7   Multiplier: §e" + String.format("%.1fx", multiplier), line--);

        // === ZEILE: Leerzeile ===
        setScore(scoreboard, objective, "  ", line--);

        // === ZEILE: Defender Count ===
        int defenderCount = defenderManager.getActiveDefenderCount();
        String defenderColor = defenderCount > 0 ? "§a" : "§c";
        setScore(scoreboard, objective, defenderColor + "⚔ Defender: §f" + defenderCount, line--);

        // === ZEILE: Defender Breakdown (Top 2 Klassen) ===
        String topClasses = getTopClassBreakdown(defenderManager);
        if (!topClasses.isEmpty()) {
            setScore(scoreboard, objective, "§7   " + topClasses, line--);
        }

        // === ZEILE: Leerzeile ===
        setScore(scoreboard, objective, "   ", line--);

        // === ZEILE: Village Core HP ===
        if (villageManager.hasVillageCore()) {
            int coreHP = villageManager.getCoreHP();
            int maxCoreHP = villageManager.getMaxCoreHP();
            double corePercent = (coreHP * 100.0) / maxCoreHP;
            String coreColor = getCoreHPColor(corePercent);

            setScore(scoreboard, objective, coreColor + "❤ Core: §f" + coreHP + "§7/" + maxCoreHP, line--);

            // Progress Bar
            String progressBar = getProgressBar(corePercent, 10);
            setScore(scoreboard, objective, "§7   " + progressBar, line--);
        } else {
            setScore(scoreboard, objective, "§c❤ Core: §7Nicht gesetzt", line--);
        }

        // === ZEILE: Leerzeile ===
        setScore(scoreboard, objective, "    ", line--);

        // === ZEILE: Top Defender ===
        DefenderVillager topDefender = getTopDefender(defenderManager);
        if (topDefender != null) {
            setScore(scoreboard, objective, "§e⭐ Top Defender:", line--);
            // Kürze Namen falls zu lang
            String name = topDefender.getViewerName();
            if (name.length() > 12) {
                name = name.substring(0, 12);
            }
            setScore(scoreboard, objective,
                "§7   " + name + " §f(" + topDefender.getDamageDealt() + ")",
                line--);
        }
    }

    /**
     * Setzt Score für eine Zeile
     */
    private void setScore(Scoreboard scoreboard, ScoreboardObjective objective, String text, int score) {
        ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(text, objective);
        playerScore.setScore(score);
    }

    /**
     * Gibt Farbe basierend auf Chaos-Level zurück
     */
    private String getChaosColor(int chaos) {
        if (chaos >= 300) return "§4"; // Dunkelrot
        if (chaos >= 200) return "§c"; // Rot
        if (chaos >= 100) return "§6"; // Orange
        if (chaos >= 50) return "§e";  // Gelb
        return "§a"; // Grün
    }

    /**
     * Gibt Farbe basierend auf Core HP% zurück
     */
    private String getCoreHPColor(double percent) {
        if (percent >= 70) return "§a"; // Grün
        if (percent >= 40) return "§e"; // Gelb
        if (percent >= 20) return "§6"; // Orange
        return "§c"; // Rot
    }

    /**
     * Erstellt Progress Bar (z.B. "[████████░░]")
     */
    private String getProgressBar(double percent, int length) {
        int filled = (int) ((percent / 100.0) * length);
        StringBuilder bar = new StringBuilder("§a[");

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("§a█");
            } else {
                bar.append("§7░");
            }
        }

        bar.append("§a]");
        return bar.toString();
    }

    /**
     * Gibt Top 2 Klassen mit Anzahl zurück (z.B. "Warrior: 3, Archer: 2")
     */
    private String getTopClassBreakdown(DefenderManager defenderManager) {
        Map<VillagerClass, Integer> classCounts = new HashMap<>();

        for (VillagerClass vClass : VillagerClass.values()) {
            int count = defenderManager.getDefendersByClass(vClass).size();
            if (count > 0) {
                classCounts.put(vClass, count);
            }
        }

        if (classCounts.isEmpty()) return "";

        // Sortiere nach Count
        List<Map.Entry<VillagerClass, Integer>> sortedClasses = new ArrayList<>(classCounts.entrySet());
        sortedClasses.sort(Map.Entry.<VillagerClass, Integer>comparingByValue().reversed());

        StringBuilder result = new StringBuilder();
        int limit = Math.min(2, sortedClasses.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<VillagerClass, Integer> entry = sortedClasses.get(i);
            if (i > 0) result.append(", ");
            result.append(entry.getKey().getDisplayName()).append(": ").append(entry.getValue());
        }

        return result.toString();
    }

    /**
     * Gibt Top Defender nach Damage zurück
     */
    private DefenderVillager getTopDefender(DefenderManager defenderManager) {
        return defenderManager.getAllDefenders().stream()
            .max(Comparator.comparingInt(DefenderVillager::getDamageDealt))
            .orElse(null);
    }

    // ===== Getters =====

    public boolean isEnabled() {
        return enabled;
    }
}
