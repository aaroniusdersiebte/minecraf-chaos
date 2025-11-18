package com.chaosstream;

import com.google.gson.*;
import net.minecraft.block.Blocks;
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
import net.minecraft.sound.SoundCategory;
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
import java.util.stream.Collectors;

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
        // WICHTIG: lastPosition bleibt null beim Spawnen - kein automatisches Patrol!
        // Position wird nur gesetzt wenn explizit ein Patrol-Command kommt
        defender.setLastPosition(null);

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
     * Aktualisiert die HP/Damage-Anzeige im Namen des Villagers
     * Format: [❤ 35] [💥 450] Krieger - ViewerName
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

        // Damage-Counter
        int totalDamage = defender.getDamageDealt();

        // Formatierter Name: [❤ HP] [💥 Damage] Klasse - Name
        String formattedName = String.format("%s[❤ %.0f] §e[💥 %d] %s%s §7- §f%s",
            hpColor, currentHP,
            totalDamage,
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
     * Event: Damage wurde verursacht
     * Tracked Total Damage für Statistiken
     */
    public void onDamageDealt(UUID villagerUUID, int damage, ServerWorld world) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        defender.addDamage(damage);

        // HP-Display updaten (um neuen Damage-Counter zu zeigen)
        if (defender.getLinkedEntity() != null && defender.getLinkedEntity().isAlive()) {
            updateHealthDisplay(defender.getLinkedEntity(), defender);
        }

        // Speichere alle 50 Damage (um nicht zu oft zu speichern)
        if (defender.getDamageDealt() % 50 == 0) {
            saveDefenders();
        }
    }

    /**
     * Event: Villager ist gestorben
     * Permadeath-System: Defender wird permanent entfernt
     */
    public void onDefenderDeath(UUID villagerUUID, ServerWorld world) {
        DefenderVillager defender = entityToDefender.get(villagerUUID);
        if (defender == null) return;

        VillagerEntity entity = defender.getLinkedEntity();
        if (entity == null) return;
        BlockPos deathPos = entity.getBlockPos();

        // 1. Broadcast-Nachricht
        String message = String.format("§c💀 Defender %s ist gefallen! 💀",
            defender.getViewerName());
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(Text.literal(message), false);
        }

        // 2. Spawne Grab-Marker (Skeleton Skull)
        world.setBlockState(deathPos, Blocks.SKELETON_SKULL.getDefaultState());

        // 3. Death-Partikel
        world.spawnParticles(ParticleTypes.SOUL,
            deathPos.getX() + 0.5, deathPos.getY() + 1.0, deathPos.getZ() + 0.5,
            30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
            deathPos.getX() + 0.5, deathPos.getY() + 1.0, deathPos.getZ() + 0.5,
            20, 0.5, 0.5, 0.5, 0.1);

        // 4. Death-Sound
        world.playSound(null, deathPos.getX(), deathPos.getY(), deathPos.getZ(),
            SoundEvents.ENTITY_WITHER_DEATH,
            SoundCategory.NEUTRAL, 1.0f, 0.5f);

        // 5. Entferne aus Maps (PERMANENTER TOD!)
        defenders.remove(defender.getUuid());
        entityToDefender.remove(villagerUUID);

        // 6. Speichere (ohne toten Defender)
        saveDefenders();

        LOGGER.info("Defender {} ist permanent gestorben (Kills: {}, Damage: {})",
            defender.getViewerName(), defender.getKills(), defender.getDamageDealt());
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
     * Gibt alle aktiven Defender zurück (als List für Sorting)
     */
    public List<DefenderVillager> getAllDefenders() {
        return new ArrayList<>(defenders.values());
    }

    /**
     * Gibt Anzahl lebender Defender zurück
     */
    public int getActiveDefenderCount() {
        return (int) defenders.values().stream()
            .filter(d -> d.getLinkedEntity() != null && d.getLinkedEntity().isAlive())
            .count();
    }

    /**
     * Gibt alle Defender einer bestimmten Klasse zurück
     */
    public List<DefenderVillager> getDefendersByClass(VillagerClass vClass) {
        return defenders.values().stream()
            .filter(d -> d.getVillagerClass() == vClass)
            .collect(Collectors.toList());
    }

    /**
     * Löscht alle Defender (Admin-Command)
     */
    public void clearAllDefenders() {
        defenders.clear();
        entityToDefender.clear();
        saveDefenders(); // Speichere leere Liste
        LOGGER.info("All defenders cleared");
    }

    /**
     * Fügt klassen-spezifische AI Goals zu Defender hinzu
     */
    private void applyDefenderAI(VillagerEntity villager, DefenderVillager defender, BlockPos corePos) {
        VillagerClass vClass = defender.getVillagerClass();
        double attackDamage = defender.getCurrentDamage();

        // WICHTIG: Lösche alle Standard-Villager-AI-Goals!
        // Villager haben standardmäßig viele Goals (WalkToTargetGoal, etc.) die unsere Custom-Goals blockieren
        villager.goalSelector.clear(goal -> true); // Löscht alle Goals

        // WICHTIG: Wenn Follow-Modus aktiv ist, füge KEINE Movement-Goals hinzu!
        // FollowPlayerGoal wird von setDefenderFollowMode() separat hinzugefügt
        if (defender.isFollowing()) {
            LOGGER.info("Follow-Modus aktiv für {} - KEINE klassen-spezifischen Goals hinzugefügt",
                defender.getViewerName());
            return; // Keine weiteren Goals hinzufügen!
        }

        // Klassen-spezifische Goals (nur wenn NICHT im Patrol/Follow-Modus)
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
                        30.0,           // Attack Range (30 Blöcke - erhöht für bessere Reichweite)
                        20.0            // Preferred Distance (20 Blöcke - erhöht für besseres Kiting)
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

    // ===== DEFENDER MANAGEMENT METHODS =====

    /**
     * Gibt Defender anhand Defender-UUID zurück
     */
    public DefenderVillager getDefender(UUID uuid) {
        return defenders.get(uuid);
    }

    /**
     * Upgraded Equipment eines Defenders gegen Items aus Spieler-Inventar
     * @return Feedback-Nachricht (startet mit §a bei Success, sonst Error)
     */
    public String upgradeDefenderEquipment(ServerPlayerEntity player, DefenderVillager defender) {
        if (defender.getLinkedEntity() == null || !defender.getLinkedEntity().isAlive()) {
            return "§c[Defender] Defender ist nicht mehr am Leben!";
        }

        int currentLevel = defender.getLevel();

        // Max Level-Check
        if (currentLevel >= 5) {
            return "§c[Defender] Dieser Defender hat bereits das maximale Level!";
        }

        // Kosten basierend auf Level
        ItemStack requiredItem;
        int requiredAmount;

        switch (currentLevel) {
            case 1:
                requiredItem = new ItemStack(Items.IRON_INGOT, 8);
                requiredAmount = 8;
                break;
            case 2:
                requiredItem = new ItemStack(Items.IRON_INGOT, 16);
                requiredAmount = 16;
                break;
            case 3:
                requiredItem = new ItemStack(Items.DIAMOND, 8);
                requiredAmount = 8;
                break;
            case 4:
                requiredItem = new ItemStack(Items.NETHERITE_INGOT, 4);
                requiredAmount = 4;
                break;
            default:
                return "§c[Defender] Upgrade nicht möglich!";
        }

        // Check ob Spieler genug Items hat
        if (!player.getInventory().contains(requiredItem)) {
            return "§c[Defender] Nicht genug Items! Benötigt: " + requiredAmount + "x " + requiredItem.getItem().getName().getString();
        }

        // Entferne Items aus Inventar
        if (!player.isCreative()) {
            int remaining = requiredAmount;
            for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == requiredItem.getItem()) {
                    int toRemove = Math.min(remaining, stack.getCount());
                    stack.decrement(toRemove);
                    remaining -= toRemove;
                }
            }
        }

        // Upgrade Equipment (reapply class stats mit aktuellem Level)
        VillagerEntity villager = defender.getLinkedEntity();
        VillagerClass vClass = defender.getVillagerClass();

        // Update Equipment (weapon + armor)
        ItemStack weapon = vClass.getMainWeapon(currentLevel + 1);
        if (!weapon.isEmpty()) {
            villager.equipStack(EquipmentSlot.MAINHAND, weapon);
        }

        ItemStack[] armor = vClass.getArmor(currentLevel + 1);
        villager.equipStack(EquipmentSlot.FEET, armor[0]);
        villager.equipStack(EquipmentSlot.LEGS, armor[1]);
        villager.equipStack(EquipmentSlot.CHEST, armor[2]);
        villager.equipStack(EquipmentSlot.HEAD, armor[3]);

        // Visual + Sound Effects
        BlockPos pos = villager.getBlockPos();
        ServerWorld world = player.getServerWorld();

        // Particles
        for (int i = 0; i < 30; i++) {
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                1, 0.3, 0.5, 0.3, 0.1
            );
        }

        // Sound
        world.playSound(
            null,
            pos,
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.NEUTRAL,
            1.0f,
            1.2f
        );

        // Update HP-Display
        updateHealthDisplay(villager, defender);

        // Speichern
        saveDefenders();

        return "§a[Defender] Equipment von §f" + defender.getViewerName() + " §aupgraded! (Level " + currentLevel + " → Bessere Ausrüstung)";
    }

    /**
     * Heilt einen Defender gegen Items aus Spieler-Inventar
     * @return Feedback-Nachricht (startet mit §a bei Success, sonst Error)
     */
    public String healDefender(ServerPlayerEntity player, DefenderVillager defender) {
        VillagerEntity villager = defender.getLinkedEntity();

        if (villager == null || !villager.isAlive()) {
            return "§c[Defender] Defender ist nicht mehr am Leben!";
        }

        float currentHP = villager.getHealth();
        float maxHP = villager.getMaxHealth();

        if (currentHP >= maxHP) {
            return "§c[Defender] Defender hat bereits volle HP!";
        }

        // Kosten-Optionen: 1x Golden Apple (heilt 50%) ODER 3x Emerald (heilt 100%)
        boolean hasGoldenApple = player.getInventory().contains(new ItemStack(Items.GOLDEN_APPLE));
        boolean hasEmeralds = player.getInventory().count(Items.EMERALD) >= 3;

        if (!hasGoldenApple && !hasEmeralds) {
            return "§c[Defender] Nicht genug Items! Benötigt: 1x Golden Apple ODER 3x Emerald";
        }

        float healAmount;
        String costDesc;

        // Priorisiere Emeralds (volle Heilung)
        if (hasEmeralds) {
            // Entferne 3 Emeralds
            if (!player.isCreative()) {
                int remaining = 3;
                for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.getItem() == Items.EMERALD) {
                        int toRemove = Math.min(remaining, stack.getCount());
                        stack.decrement(toRemove);
                        remaining -= toRemove;
                    }
                }
            }
            healAmount = maxHP; // Volle Heilung
            costDesc = "3x Emerald";
        } else {
            // Entferne 1 Golden Apple
            if (!player.isCreative()) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.getItem() == Items.GOLDEN_APPLE) {
                        stack.decrement(1);
                        break;
                    }
                }
            }
            healAmount = maxHP * 0.5f; // 50% Heilung
            costDesc = "1x Golden Apple";
        }

        // Heile Defender
        villager.setHealth(Math.min(currentHP + healAmount, maxHP));

        // Visual + Sound Effects
        BlockPos pos = villager.getBlockPos();
        ServerWorld world = player.getServerWorld();

        // Heart Particles
        for (int i = 0; i < 20; i++) {
            world.spawnParticles(
                ParticleTypes.HEART,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                1, 0.3, 0.5, 0.3, 0.05
            );
        }

        // Totem Particles
        for (int i = 0; i < 15; i++) {
            world.spawnParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                1, 0.3, 0.5, 0.3, 0.1
            );
        }

        // Sound
        world.playSound(
            null,
            pos,
            SoundEvents.BLOCK_BEACON_POWER_SELECT,
            SoundCategory.NEUTRAL,
            1.0f,
            1.5f
        );

        // Update HP-Display
        updateHealthDisplay(villager, defender);

        return String.format("§a[Defender] §f%s §ageheilt! (+%.0f HP für %s)",
            defender.getViewerName(), healAmount, costDesc);
    }

    /**
     * Public Wrapper für applyDefenderAI - ermöglicht Neuanwendung der AI von außen
     */
    public void reapplyDefenderAI(VillagerEntity villager, DefenderVillager defender, BlockPos corePos) {
        LOGGER.info("reapplyDefenderAI für {} - lastPosition: {}, following: {}",
            defender.getViewerName(), defender.getLastPosition(), defender.isFollowing());
        applyDefenderAI(villager, defender, corePos);
    }

    /**
     * Setzt den Follow-Modus eines Defenders
     */
    public void setDefenderFollowMode(DefenderVillager defender, ServerPlayerEntity player, boolean following) {
        LOGGER.info("setDefenderFollowMode für {} - following: {}", defender.getViewerName(), following);

        defender.setFollowing(following);
        // WICHTIG: Wenn following = true, setze lastPosition auf null (kein Patrol mehr)
        if (following) {
            defender.setLastPosition(null);
        }

        VillagerEntity villager = defender.getLinkedEntity();
        if (villager == null || !villager.isAlive()) {
            LOGGER.warn("setDefenderFollowMode: Villager für {} ist null oder tot!", defender.getViewerName());
            return;
        }

        // WICHTIG: NICHT clear() verwenden! Das bricht den GoalSelector!
        // Stattdessen: Stoppe Navigation und füge FollowPlayerGoal mit höchster Priority hinzu
        villager.getNavigation().stop();

        if (following) {
            // Prüfe ob Villager AI haben kann
            if (villager.isAiDisabled()) {
                villager.setAiDisabled(false);
            }

            // Füge FollowPlayerGoal mit HÖCHSTER PRIORITY hinzu (überschreibt andere Goals)
            DefenderGoals.FollowPlayerGoal followGoal = new DefenderGoals.FollowPlayerGoal(
                villager,
                player,
                1.2,  // Speed
                5.0,  // Min Distance
                20.0  // Max Distance
            );
            villager.goalSelector.add(0, followGoal); // Priority 0 = HÖCHSTE!

            LOGGER.info("FollowPlayerGoal hinzugefügt für {} (folgt {})",
                defender.getViewerName(), player.getName().getString());

            // Visual Effect
            BlockPos pos = villager.getBlockPos();
            ServerWorld world = player.getServerWorld();

            for (int i = 0; i < 15; i++) {
                world.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5,
                    1, 0.3, 0.5, 0.3, 0.05
                );
            }

            world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_VILLAGER_YES,
                SoundCategory.NEUTRAL,
                1.0f,
                1.2f
            );
        } else {
            // Patrouille-Modus: Wende normale AI wieder an
            BlockPos corePos = ChaosMod.getVillageManager().getVillageCorePos();
            applyDefenderAI(villager, defender, corePos);

            // Visual Effect
            BlockPos pos = villager.getBlockPos();
            ServerWorld world = player.getServerWorld();

            for (int i = 0; i < 15; i++) {
                world.spawnParticles(
                    ParticleTypes.CLOUD,
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5,
                    1, 0.3, 0.5, 0.3, 0.05
                );
            }

            world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_VILLAGER_NO,
                SoundCategory.NEUTRAL,
                1.0f,
                1.0f
            );
        }

        // Speichern
        saveDefenders();
    }

    /**
     * Entfernt einen Defender permanent (inkl. Entity)
     */
    public void removeDefender(DefenderVillager defender) {
        // Entferne Entity aus Welt
        VillagerEntity villager = defender.getLinkedEntity();
        if (villager != null && villager.isAlive()) {
            villager.discard();

            // Death Effects
            BlockPos pos = villager.getBlockPos();
            ServerWorld world = (ServerWorld) villager.getWorld();

            // Soul Particles
            for (int i = 0; i < 30; i++) {
                world.spawnParticles(
                    ParticleTypes.SOUL,
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5,
                    1, 0.3, 0.5, 0.3, 0.05
                );
            }

            // Sound
            world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_VILLAGER_DEATH,
                SoundCategory.NEUTRAL,
                1.0f,
                0.8f
            );
        }

        // Entferne aus Maps
        defenders.remove(defender.getUuid());
        if (defender.getEntityUUID() != null) {
            entityToDefender.remove(defender.getEntityUUID());
        }

        // Speichern
        saveDefenders();

        LOGGER.info("Defender {} ({}) wurde entlassen", defender.getViewerName(), defender.getUuid());
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
            JsonElement element = JsonParser.parseReader(reader);

            // Check if file is empty or invalid
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("defender-data.json ist leer oder ungültig - starte mit leerem Defender-Pool");
                return;
            }

            JsonObject root = element.getAsJsonObject();
            JsonArray defenderArray = root.getAsJsonArray("defenders");

            if (defenderArray == null) {
                LOGGER.warn("Kein 'defenders' Array in defender-data.json gefunden");
                return;
            }

            for (JsonElement arrayElement : defenderArray) {
                DefenderVillager defender = new DefenderVillager(arrayElement.getAsJsonObject());
                defenders.put(defender.getUuid(), defender);
            }

            LOGGER.info("{} Defender aus defender-data.json geladen", defenders.size());
        } catch (IOException e) {
            LOGGER.error("Fehler beim Laden von defender-data.json", e);
        } catch (Exception e) {
            LOGGER.error("Fehler beim Parsen von defender-data.json - Datei ist möglicherweise korrupt", e);
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
                // WICHTIG: lastPosition ist die PATROL-ZIEL-Position, NICHT die aktuelle Position!
                // Wir updaten sie NICHT automatisch beim Save!
                // lastPosition wird nur gesetzt wenn explizit ein Patrol-Command kommt

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
     * Upgraded Equipment eines Defenders (konsumiert Items aus Spieler-Inventar)
     */
    public void upgradeDefenderEquipment(DefenderVillager defender, ServerPlayerEntity player) {
        VillagerEntity entity = defender.getLinkedEntity();
        if (entity == null || !entity.isAlive()) {
            player.sendMessage(Text.literal("§c✗ Defender ist nicht verfügbar!"), false);
            return;
        }

        // Berechne benötigte Items basierend auf Level
        int level = defender.getLevel();
        ItemStack requiredItem;
        int requiredCount;

        if (level == 1) {
            requiredItem = new ItemStack(Items.IRON_INGOT);
            requiredCount = 8;
        } else if (level == 2) {
            requiredItem = new ItemStack(Items.IRON_INGOT);
            requiredCount = 16;
        } else if (level == 3) {
            requiredItem = new ItemStack(Items.DIAMOND);
            requiredCount = 8;
        } else if (level == 4) {
            requiredItem = new ItemStack(Items.NETHERITE_INGOT);
            requiredCount = 4;
        } else {
            player.sendMessage(Text.literal("§c✗ Defender hat bereits maximales Equipment!"), false);
            return;
        }

        // Prüfe ob Spieler genug Items hat
        int itemCount = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == requiredItem.getItem()) {
                itemCount += stack.getCount();
            }
        }

        if (itemCount < requiredCount) {
            player.sendMessage(Text.literal(
                String.format("§c✗ Benötigt: %dx %s (Du hast: %d)",
                    requiredCount, requiredItem.getName().getString(), itemCount)
            ), false);
            return;
        }

        // Konsumiere Items aus Spieler-Inventar
        int remaining = requiredCount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == requiredItem.getItem()) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }

        // Upgrade Equipment (Stats werden nicht verändert, nur Equipment wird neu angewandt)
        // Temporär Level erhöhen für Equipment-Berechnung (aber nicht im Defender-Objekt speichern!)
        ItemStack weapon = defender.getVillagerClass().getMainWeapon(level + 1);
        if (!weapon.isEmpty()) {
            entity.equipStack(EquipmentSlot.MAINHAND, weapon);
        }
        ItemStack[] armor = defender.getVillagerClass().getArmor(level + 1);
        entity.equipStack(EquipmentSlot.FEET, armor[0]);
        entity.equipStack(EquipmentSlot.LEGS, armor[1]);
        entity.equipStack(EquipmentSlot.CHEST, armor[2]);
        entity.equipStack(EquipmentSlot.HEAD, armor[3]);

        // Visual Effekte
        ServerWorld world = (ServerWorld) entity.getWorld();
        world.spawnParticles(ParticleTypes.ENCHANT,
            entity.getX(), entity.getY() + 1, entity.getZ(),
            30, 0.3, 0.5, 0.3, 0.1);
        world.playSound(null, entity.getBlockPos(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 1.0f, 1.0f);

        player.sendMessage(Text.literal(
            String.format("§a✓ %s Equipment upgraded! (Level %d → %d)",
                defender.getViewerName(), level, level + 1)
        ), false);

        saveDefenders();
    }

    /**
     * Heilt einen Defender (konsumiert Items aus Spieler-Inventar)
     */
    public void healDefender(DefenderVillager defender, ServerPlayerEntity player) {
        VillagerEntity entity = defender.getLinkedEntity();
        if (entity == null || !entity.isAlive()) {
            player.sendMessage(Text.literal("§c✗ Defender ist nicht verfügbar!"), false);
            return;
        }

        float currentHealth = entity.getHealth();
        float maxHealth = entity.getMaxHealth();

        if (currentHealth >= maxHealth) {
            player.sendMessage(Text.literal("§c✗ Defender hat bereits volle HP!"), false);
            return;
        }

        // Option 1: Golden Apple (heilt 50%)
        boolean hasGoldenApple = player.getInventory().contains(new ItemStack(Items.GOLDEN_APPLE));
        // Option 2: 3x Emerald (heilt 100%)
        int emeraldCount = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.EMERALD) {
                emeraldCount += stack.getCount();
            }
        }
        boolean hasEmeralds = emeraldCount >= 3;

        if (!hasGoldenApple && !hasEmeralds) {
            player.sendMessage(Text.literal(
                "§c✗ Benötigt: 1x Golden Apple (50% HP) ODER 3x Emerald (100% HP)"
            ), false);
            return;
        }

        float healAmount;
        String itemUsed;

        // Priorität: Emeralds für volle Heilung
        if (hasEmeralds && currentHealth < maxHealth * 0.5f) {
            // Konsumiere 3x Emerald
            int remaining = 3;
            for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == Items.EMERALD) {
                    int toRemove = Math.min(remaining, stack.getCount());
                    stack.decrement(toRemove);
                    remaining -= toRemove;
                }
            }
            healAmount = maxHealth; // Volle Heilung
            itemUsed = "3x Emerald";
        } else if (hasGoldenApple) {
            // Konsumiere Golden Apple
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == Items.GOLDEN_APPLE) {
                    stack.decrement(1);
                    break;
                }
            }
            healAmount = maxHealth * 0.5f; // 50% Heilung
            itemUsed = "Golden Apple";
        } else {
            player.sendMessage(Text.literal("§c✗ Keine Items zum Heilen verfügbar!"), false);
            return;
        }

        // Heile Defender
        entity.setHealth(Math.min(currentHealth + healAmount, maxHealth));

        // Visual Effekte
        ServerWorld world = (ServerWorld) entity.getWorld();
        world.spawnParticles(ParticleTypes.HEART,
            entity.getX(), entity.getY() + 1, entity.getZ(),
            20, 0.3, 0.5, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
            entity.getX(), entity.getY() + 1, entity.getZ(),
            10, 0.2, 0.3, 0.2, 0.1);
        world.playSound(null, entity.getBlockPos(),
            SoundEvents.BLOCK_BEACON_POWER_SELECT,
            SoundCategory.PLAYERS, 1.0f, 1.5f);

        player.sendMessage(Text.literal(
            String.format("§a✓ %s geheilt mit %s! HP: %.1f/%.1f",
                defender.getViewerName(), itemUsed, entity.getHealth(), maxHealth)
        ), false);
    }

    /**
     * Gibt Item vom Spieler zum Defender-Inventar
     */
    public void giveItemToDefender(DefenderVillager defender, ItemStack stack, int slot) {
        if (stack.isEmpty() || slot < 0 || slot >= 9) {
            return;
        }

        defender.getInventory().setStack(slot, stack);
        saveDefenders();

        LOGGER.debug("Item gegeben an Defender {}: {} in Slot {}",
            defender.getViewerName(), stack.getName().getString(), slot);
    }

    /**
     * Nimmt Item vom Defender-Inventar zum Spieler
     */
    public void takeItemFromDefender(DefenderVillager defender, ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= 9) {
            return;
        }

        ItemStack stack = defender.getInventory().getStack(slot);
        if (stack.isEmpty()) {
            player.sendMessage(Text.literal("§c✗ Slot ist leer!"), false);
            return;
        }

        // Gib Item zum Spieler
        player.getInventory().offerOrDrop(stack.copy());
        defender.getInventory().setStack(slot, ItemStack.EMPTY);
        saveDefenders();

        player.sendMessage(Text.literal(
            String.format("§a✓ Erhalten: %s", stack.getName().getString())
        ), false);

        LOGGER.debug("Item genommen von Defender {}: {} aus Slot {}",
            defender.getViewerName(), stack.getName().getString(), slot);
    }

    /**
     * Cleanup bei Server-Shutdown
     */
    public void shutdown() {
        saveDefenders();
        LOGGER.info("DefenderManager heruntergefahren - {} Defender gespeichert", defenders.size());
    }
}
