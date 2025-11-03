package com.chaosstream;

import com.google.gson.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager für alle Defender-Villagers
 * Verwaltet Spawning, Stats, Level-System und Persistenz
 */
public class DefenderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosMod");
    private static DefenderManager instance;

    private final Map<UUID, DefenderVillager> defenders = new ConcurrentHashMap<>();
    private final Map<UUID, DefenderVillager> entityToDefender = new ConcurrentHashMap<>(); // Entity UUID -> Defender
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private DefenderManager() {
        this.dataFile = new File("defender-data.json");
        loadDefenders();
    }

    public static DefenderManager getInstance() {
        if (instance == null) {
            instance = new DefenderManager();
        }
        return instance;
    }

    /**
     * Spawnt einen neuen Defender-Villager
     */
    public DefenderVillager spawnDefender(ServerWorld world, BlockPos position, String viewerName, VillagerClass villagerClass) {
        // Erstelle Defender-Daten
        DefenderVillager defender = new DefenderVillager(viewerName, villagerClass);

        // Spawne Villager-Entity
        VillagerEntity villager = EntityType.VILLAGER.create(world);
        if (villager == null) {
            LOGGER.error("Konnte VillagerEntity nicht erstellen!");
            return null;
        }

        // Setze Position
        villager.refreshPositionAndAngles(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);

        // Setze Namen
        villager.setCustomName(Text.literal(defender.getFormattedName()));
        villager.setCustomNameVisible(true);

        // Setze als Persistent
        villager.setPersistent();

        // Verknüpfe Defender mit Entity
        defender.setLinkedEntity(villager);
        defender.setLastPosition(position);

        // Registriere Defender
        defenders.put(defender.getUuid(), defender);
        entityToDefender.put(villager.getUuid(), defender);

        // Wende Klassen-Stats an
        applyClassStats(villager, defender);

        // Spawne Entity in Welt
        world.spawnEntity(villager);

        // Spawn-Effekte
        playSpawnEffects(world, position, villagerClass);

        // Speichere
        saveDefenders();

        LOGGER.info("Defender {} ({}) gespawnt für Viewer {} an {}",
            defender.getUuid(), villagerClass.getDisplayName(), viewerName, position);

        return defender;
    }

    /**
     * Wendet Klassen-spezifische Stats, Equipment und AI an
     */
    private void applyClassStats(VillagerEntity villager, DefenderVillager defender) {
        VillagerClass vClass = defender.getVillagerClass();
        int level = defender.getLevel();

        // Setze HP
        double health = vClass.getHealthForLevel(level);
        villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
        villager.setHealth((float) health);

        // Setze Movement Speed
        villager.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(vClass.getBaseSpeed() * 0.25);

        // Equipment
        ItemStack weapon = vClass.getMainWeapon(level);
        if (!weapon.isEmpty()) {
            villager.equipStack(EquipmentSlot.MAINHAND, weapon);
        }

        // Armor
        ItemStack[] armor = vClass.getArmor(level);
        villager.equipStack(EquipmentSlot.FEET, armor[0]);
        villager.equipStack(EquipmentSlot.LEGS, armor[1]);
        villager.equipStack(EquipmentSlot.CHEST, armor[2]);
        villager.equipStack(EquipmentSlot.HEAD, armor[3]);

        // Klassen-spezifische Effekte
        if (vClass == VillagerClass.TANK) {
            // Tank bekommt permanente Resistance
            villager.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE,
                Integer.MAX_VALUE,
                1, // Level II
                false,
                false
            ));
        }

        if (level >= 3) {
            // Ab Level 3: Speed-Buff
            villager.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                Integer.MAX_VALUE,
                0, // Level I
                false,
                false
            ));
        }

        if (level >= 5) {
            // Level 5: Glowing-Effekt
            villager.addStatusEffect(new StatusEffectInstance(
                StatusEffects.GLOWING,
                Integer.MAX_VALUE,
                0,
                false,
                false
            ));
        }

        // Update HP-Display im Namen
        updateHealthDisplay(villager, defender);
    }

    /**
     * Aktualisiert die HP-Anzeige im Namen des Villagers
     * Format: [❤ 35/40] Krieger - ViewerName
     * Farbe basierend auf HP%: Grün >70%, Gelb 40-70%, Rot <40%
     */
    public void updateHealthDisplay(VillagerEntity villager, DefenderVillager defender) {
        if (villager == null || !villager.isAlive()) return;

        float currentHP = villager.getHealth();
        float maxHP = villager.getMaxHealth();
        float hpPercent = (currentHP / maxHP) * 100;

        // HP-Farbe basierend auf Prozentsatz
        String hpColor;
        if (hpPercent > 70) {
            hpColor = "§a"; // Grün
        } else if (hpPercent > 40) {
            hpColor = "§e"; // Gelb
        } else {
            hpColor = "§c"; // Rot
        }

        // Klassen-Farbe und Name
        VillagerClass vClass = defender.getVillagerClass();
        String className = vClass.getDisplayName();
        String classColor = vClass.getColorCode();

        // Formatierter Name: [❤ HP] Klasse - Name
        String formattedName = String.format("%s[❤ %.0f/%.0f] %s%s §7- §f%s",
            hpColor, currentHP, maxHP,
            classColor, className,
            defender.getViewerName());

        villager.setCustomName(Text.literal(formattedName));
        villager.setCustomNameVisible(true);
    }

    /**
     * Spawn-Effekte je nach Klasse
     */
    private void playSpawnEffects(ServerWorld world, BlockPos pos, VillagerClass vClass) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // Partikel je nach Klasse
        switch (vClass) {
            case WARRIOR:
                // Rote Flammen
                for (int i = 0; i < 30; i++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0.3, 0.5, 0.3, 0.05);
                }
                break;
            case ARCHER:
                // Grüne Pfeile
                for (int i = 0; i < 30; i++) {
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.3, 0.5, 0.3, 0.05);
                }
                break;
            case HEALER:
                // Rosa Herzen
                for (int i = 0; i < 40; i++) {
                    world.spawnParticles(ParticleTypes.HEART, x, y, z, 1, 0.3, 0.5, 0.3, 0.05);
                }
                break;
            case BUILDER:
                // Gelbe Sterne
                for (int i = 0; i < 30; i++) {
                    world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.3, 0.5, 0.3, 0.05);
                }
                break;
            case TANK:
                // Blaue Explosion
                for (int i = 0; i < 40; i++) {
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.3, 0.5, 0.3, 0.05);
                }
                break;
        }

        // Totem-Partikel für alle
        for (int i = 0; i < 20; i++) {
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 1, 0.5, 0.5, 0.5, 0.1);
        }

        // Sound
        SoundEffects.playTotemUse(world, pos);
        SoundEffects.playBell(world, pos);
    }

    /**
     * Level-Up-Effekte
     */
    public void playLevelUpEffects(ServerWorld world, VillagerEntity villager, DefenderVillager defender) {
        BlockPos pos = villager.getBlockPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;

        // Feuerwerk-Partikel
        for (int i = 0; i < 50; i++) {
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.5, 0.5, 0.5, 0.15);
        }

        // XP-Orbs
        for (int i = 0; i < 30; i++) {
            world.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.3, 0.5, 0.3, 0.1);
        }

        // Sound
        world.playSound(
            null,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            SoundEvents.ENTITY_PLAYER_LEVELUP,
            net.minecraft.sound.SoundCategory.NEUTRAL,
            1.0f,
            1.0f
        );

        // Re-Apply Stats (inkl. HP-Display-Update)
        applyClassStats(villager, defender);

        // Broadcast an alle Spieler
        String message = String.format("§6✦ %s hat Level %d erreicht! ✦",
            defender.getViewerName(),
            defender.getLevel());

        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(Text.literal(message), false);
        }

        LOGGER.info("{} hat Level {} erreicht!", defender.getViewerName(), defender.getLevel());
    }

    /**
     * Event: Mob wurde von Defender getötet
     */
    public void onMobKilled(UUID villagerUUID, ServerWorld world) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        boolean leveledUp = defender.addKill();

        if (leveledUp && defender.getLinkedEntity() != null) {
            playLevelUpEffects(world, defender.getLinkedEntity(), defender);
        }

        saveDefenders();
    }

    /**
     * Event: Wave wurde completed
     */
    public void onWaveCompleted(ServerWorld world) {
        for (DefenderVillager defender : defenders.values()) {
            VillagerEntity entity = defender.getLinkedEntity();

            // Nur XP geben wenn Villager noch lebt
            if (entity != null && entity.isAlive()) {
                boolean leveledUp = defender.completeWave();

                if (leveledUp) {
                    playLevelUpEffects(world, entity, defender);
                }
            }
        }

        saveDefenders();
    }

    /**
     * Event: Healing durchgeführt
     */
    public void onHealing(UUID villagerUUID, int amount, ServerWorld world) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        boolean leveledUp = defender.addHealing(amount);

        if (leveledUp && defender.getLinkedEntity() != null) {
            playLevelUpEffects(world, defender.getLinkedEntity(), defender);
        }

        saveDefenders();
    }

    /**
     * Event: Core wurde repariert
     */
    public void onCoreRepaired(UUID villagerUUID, int amount, ServerWorld world) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        boolean leveledUp = defender.addCoreRepair(amount);

        if (leveledUp && defender.getLinkedEntity() != null) {
            playLevelUpEffects(world, defender.getLinkedEntity(), defender);
        }

        saveDefenders();
    }

    /**
     * Event: Villager ist gestorben
     */
    public void onDefenderDeath(UUID villagerUUID) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        LOGGER.info("Defender {} ({}) ist gestorben. (Kills: {}, Waves: {})",
            defender.getViewerName(),
            defender.getVillagerClass().getDisplayName(),
            defender.getKills(),
            defender.getWavesCompleted());

        // Später: Permadeath-System oder Respawn
        // Momentan: Defender bleibt in Liste für Respawn bei Server-Neustart
        defender.setLinkedEntity(null);
        saveDefenders();
    }

    /**
     * Respawnt alle gespeicherten Defender bei Server-Start
     */
    public void respawnAllDefenders(ServerWorld world, BlockPos villageCorePos) {
        if (villageCorePos == null) {
            LOGGER.warn("Kein Village Core gefunden - kann Defender nicht respawnen!");
            return;
        }

        int respawnCount = 0;
        for (DefenderVillager defender : defenders.values()) {
            // Respawne nur wenn noch keine Entity verknüpft ist
            if (defender.getLinkedEntity() == null || !defender.getLinkedEntity().isAlive()) {
                BlockPos spawnPos = defender.getLastPosition() != null ?
                    defender.getLastPosition() : villageCorePos;

                // Spawne Villager
                VillagerEntity villager = EntityType.VILLAGER.create(world);
                if (villager != null) {
                    villager.refreshPositionAndAngles(
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5,
                        0, 0
                    );

                    villager.setCustomName(Text.literal(defender.getFormattedName()));
                    villager.setCustomNameVisible(true);
                    villager.setPersistent();

                    defender.setLinkedEntity(villager);
                    entityToDefender.put(villager.getUuid(), defender);

                    applyClassStats(villager, defender);
                    applyDefenderAI(villager, defender, villageCorePos); // AI-Goals hinzufügen
                    world.spawnEntity(villager);

                    // Sanfte Respawn-Effekte
                    playSpawnEffects(world, spawnPos, defender.getVillagerClass());

                    respawnCount++;
                }
            }
        }

        if (respawnCount > 0) {
            LOGGER.info("{} Defender erfolgreich respawned!", respawnCount);
        }
    }

    /**
     * Gibt Defender anhand Entity-UUID zurück
     */
    public DefenderVillager getDefenderByEntityUUID(UUID entityUUID) {
        return entityToDefender.get(entityUUID);
    }

    /**
     * Gibt alle aktiven Defender zurück
     */
    public Collection<DefenderVillager> getAllDefenders() {
        return defenders.values();
    }

    /**
     * Fügt klassen-spezifische AI Goals zu Defender hinzu
     */
    private void applyDefenderAI(VillagerEntity villager, DefenderVillager defender, BlockPos corePos) {
        VillagerClass vClass = defender.getVillagerClass();
        double attackDamage = defender.getCurrentDamage();

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
     * Lädt Defender aus JSON
     */
    private void loadDefenders() {
        if (!dataFile.exists()) {
            LOGGER.info("Keine defender-data.json gefunden - starte mit leerem Defender-Pool");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray defenderArray = root.getAsJsonArray("defenders");

            for (JsonElement element : defenderArray) {
                DefenderVillager defender = new DefenderVillager(element.getAsJsonObject());
                defenders.put(defender.getUuid(), defender);
            }

            LOGGER.info("{} Defender aus defender-data.json geladen", defenders.size());
        } catch (IOException e) {
            LOGGER.error("Fehler beim Laden von defender-data.json", e);
        }
    }

    /**
     * Speichert Defender zu JSON
     */
    public void saveDefenders() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            JsonObject root = new JsonObject();
            JsonArray defenderArray = new JsonArray();

            for (DefenderVillager defender : defenders.values()) {
                // Update Position wenn Entity existiert
                if (defender.getLinkedEntity() != null && defender.getLinkedEntity().isAlive()) {
                    defender.setLastPosition(defender.getLinkedEntity().getBlockPos());
                }

                defenderArray.add(defender.toJson());
            }

            root.add("defenders", defenderArray);
            gson.toJson(root, writer);

            LOGGER.debug("{} Defender in defender-data.json gespeichert", defenders.size());
        } catch (IOException e) {
            LOGGER.error("Fehler beim Speichern von defender-data.json", e);
        }
    }

    /**
     * Cleanup bei Server-Shutdown
     */
    public void shutdown() {
        saveDefenders();
        LOGGER.info("DefenderManager heruntergefahren - {} Defender gespeichert", defenders.size());
    }
}
