package com.chaosstream;

import com.google.gson.JsonObject;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Datenklasse für einen Defender-Villager
 * Speichert alle Stats, Level, XP und verknüpft mit Minecraft-Entity
 */
public class DefenderVillager {
    // Identifikation
    private UUID uuid;
    private UUID entityUUID;  // UUID der Minecraft VillagerEntity
    private String viewerName;
    private VillagerClass villagerClass;

    // Level & Progression
    private int level;
    private int xp;
    private static final int[] XP_THRESHOLDS = {0, 10, 30, 60, 100}; // XP für Level 1-5

    // Stats
    private int kills;
    private int damageDealt;
    private int wavesCompleted;
    private int healingDone;
    private int coreRepaired;
    private long spawnTime;
    private BlockPos lastPosition;

    // Linked Entity (nicht persistent)
    private transient VillagerEntity linkedEntity;

    /**
     * Konstruktor für neue Defender
     */
    public DefenderVillager(String viewerName, VillagerClass villagerClass) {
        this.uuid = UUID.randomUUID();
        this.viewerName = viewerName;
        this.villagerClass = villagerClass;
        this.level = 1;
        this.xp = 0;
        this.kills = 0;
        this.damageDealt = 0;
        this.wavesCompleted = 0;
        this.healingDone = 0;
        this.coreRepaired = 0;
        this.spawnTime = System.currentTimeMillis();
    }

    /**
     * Konstruktor für Laden aus JSON
     */
    public DefenderVillager(JsonObject json) {
        this.uuid = UUID.fromString(json.get("uuid").getAsString());
        this.viewerName = json.get("name").getAsString();
        this.villagerClass = VillagerClass.fromString(json.get("class").getAsString());
        this.level = json.get("level").getAsInt();
        this.xp = json.get("xp").getAsInt();

        JsonObject stats = json.getAsJsonObject("stats");
        this.kills = stats.get("kills").getAsInt();
        this.damageDealt = stats.get("damage_dealt").getAsInt();
        this.wavesCompleted = stats.get("waves_survived").getAsInt();
        this.healingDone = stats.get("healing_done").getAsInt();
        this.coreRepaired = stats.get("core_repaired").getAsInt();

        this.spawnTime = json.get("spawn_time").getAsLong();

        if (json.has("last_position")) {
            JsonObject pos = json.getAsJsonObject("last_position");
            this.lastPosition = new BlockPos(
                pos.get("x").getAsInt(),
                pos.get("y").getAsInt(),
                pos.get("z").getAsInt()
            );
        }

        if (json.has("entity_uuid")) {
            this.entityUUID = UUID.fromString(json.get("entity_uuid").getAsString());
        }
    }

    /**
     * Konvertiert zu JSON für Speicherung
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.addProperty("name", viewerName);
        json.addProperty("class", villagerClass.name());
        json.addProperty("level", level);
        json.addProperty("xp", xp);

        JsonObject stats = new JsonObject();
        stats.addProperty("kills", kills);
        stats.addProperty("damage_dealt", damageDealt);
        stats.addProperty("waves_survived", wavesCompleted);
        stats.addProperty("healing_done", healingDone);
        stats.addProperty("core_repaired", coreRepaired);
        json.add("stats", stats);

        json.addProperty("spawn_time", spawnTime);

        if (lastPosition != null) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", lastPosition.getX());
            pos.addProperty("y", lastPosition.getY());
            pos.addProperty("z", lastPosition.getZ());
            json.add("last_position", pos);
        }

        if (entityUUID != null) {
            json.addProperty("entity_uuid", entityUUID.toString());
        }

        return json;
    }

    /**
     * Fügt XP hinzu und checkt Level-Up
     * @return true wenn Level-Up erfolgt ist
     */
    public boolean addXP(int amount) {
        if (level >= 5) return false; // Max Level erreicht

        int oldLevel = level;
        xp += amount;

        // Check Level-Up
        while (level < 5 && xp >= getXPForNextLevel()) {
            level++;
        }

        return level > oldLevel; // Level-Up erfolgt?
    }

    /**
     * Gibt XP für nächstes Level zurück
     */
    public int getXPForNextLevel() {
        if (level >= 5) return Integer.MAX_VALUE;
        return XP_THRESHOLDS[level]; // level = aktuelles Level (1-5), array index = next level - 1
    }

    /**
     * Gibt XP-Progress als Prozent zurück (0.0 - 1.0)
     */
    public double getXPProgress() {
        if (level >= 5) return 1.0;
        if (level == 1) {
            return (double) xp / XP_THRESHOLDS[1];
        }
        int currentThreshold = XP_THRESHOLDS[level - 1];
        int nextThreshold = XP_THRESHOLDS[level];
        return (double) (xp - currentThreshold) / (nextThreshold - currentThreshold);
    }

    /**
     * Erhöht Kill-Counter und gibt XP
     */
    public boolean addKill() {
        kills++;
        return addXP(1); // 1 XP pro Kill
    }

    /**
     * Fügt Damage hinzu
     */
    public void addDamage(int damage) {
        damageDealt += damage;
    }

    /**
     * Erhöht Wave-Counter und gibt XP
     */
    public boolean completeWave() {
        wavesCompleted++;
        return addXP(10); // 10 XP pro Wave
    }

    /**
     * Fügt Healing hinzu und gibt XP
     */
    public boolean addHealing(int amount) {
        healingDone += amount;
        // 1 XP pro 10 HP geheilt
        if (healingDone % 10 == 0) {
            return addXP(1);
        }
        return false;
    }

    /**
     * Fügt Core-Repair hinzu und gibt XP
     */
    public boolean addCoreRepair(int amount) {
        coreRepaired += amount;
        // 1 XP pro 5 HP repariert
        if (coreRepaired % 5 == 0) {
            return addXP(1);
        }
        return false;
    }

    /**
     * Gibt den formatierten Namen für Display zurück
     * Format: §6[Lvl 5] ViewerName §7(Warrior)
     */
    public String getFormattedName() {
        String levelColor = getLevelColor();
        return levelColor + "[Lvl " + level + "] " + villagerClass.getColorCode() + viewerName + " §7(" + villagerClass.getDisplayName() + ")";
    }

    /**
     * Gibt die Farbe basierend auf Level zurück
     */
    private String getLevelColor() {
        switch (level) {
            case 1: return "§7"; // Grau
            case 2: return "§a"; // Grün
            case 3: return "§b"; // Blau
            case 4: return "§d"; // Lila
            case 5: return "§6"; // Gold
            default: return "§f"; // Weiß
        }
    }

    /**
     * Gibt HP basierend auf Klasse und Level zurück
     */
    public double getCurrentHealth() {
        return villagerClass.getHealthForLevel(level);
    }

    /**
     * Gibt Damage basierend auf Klasse und Level zurück
     */
    public double getCurrentDamage() {
        return villagerClass.getDamageForLevel(level);
    }

    // Getters & Setters
    public UUID getUuid() {
        return uuid;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public void setEntityUUID(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }

    public String getViewerName() {
        return viewerName;
    }

    public VillagerClass getVillagerClass() {
        return villagerClass;
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    public int getKills() {
        return kills;
    }

    public int getDamageDealt() {
        return damageDealt;
    }

    public int getWavesCompleted() {
        return wavesCompleted;
    }

    public int getHealingDone() {
        return healingDone;
    }

    public int getCoreRepaired() {
        return coreRepaired;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public BlockPos getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(BlockPos pos) {
        this.lastPosition = pos;
    }

    public VillagerEntity getLinkedEntity() {
        return linkedEntity;
    }

    public void setLinkedEntity(VillagerEntity entity) {
        this.linkedEntity = entity;
        if (entity != null) {
            this.entityUUID = entity.getUuid();
        }
    }

    /**
     * Gibt eine Stats-Zusammenfassung zurück
     */
    public String getStatsString() {
        return String.format(
            "§b%s §7- Level %d (%d XP)\n" +
            "§7Klasse: %s%s\n" +
            "§7Kills: §e%d §7| Damage: §e%d §7| Waves: §e%d\n" +
            "§7Healing: §a%d §7| Core Repair: §a%d",
            viewerName, level, xp,
            villagerClass.getColorCode(), villagerClass.getDisplayName(),
            kills, damageDealt, wavesCompleted,
            healingDone, coreRepaired
        );
    }
}
