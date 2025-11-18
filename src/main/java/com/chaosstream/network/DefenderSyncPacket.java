package com.chaosstream.network;

import com.chaosstream.VillagerClass;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client Packet für Defender-Synchronisation
 *
 * Sendet alle Defender-Daten zum Client für Darstellung im Command Center GUI
 * und auf JourneyMap-Overlays.
 *
 * Update-Rate: 1x pro Sekunde (per Tick-Event im Server)
 */
public class DefenderSyncPacket {
    public static final Identifier ID = new Identifier("chaosstream", "defender_sync");

    private final List<DefenderData> defenders;
    private final BlockPos villageCorePos;
    private final int coreHealth;
    private final int coreMaxHealth;
    private final List<BlockPos> activeSpawnLocations;
    private final BlockPos playerPos; // Spieler-Position für Map

    public DefenderSyncPacket(
        List<DefenderData> defenders,
        BlockPos villageCorePos,
        int coreHealth,
        int coreMaxHealth,
        List<BlockPos> activeSpawnLocations,
        BlockPos playerPos
    ) {
        this.defenders = defenders;
        this.villageCorePos = villageCorePos;
        this.coreHealth = coreHealth;
        this.coreMaxHealth = coreMaxHealth;
        this.activeSpawnLocations = activeSpawnLocations;
        this.playerPos = playerPos;
    }

    public List<DefenderData> defenders() {
        return defenders;
    }

    public BlockPos villageCorePos() {
        return villageCorePos;
    }

    public int coreHealth() {
        return coreHealth;
    }

    public int coreMaxHealth() {
        return coreMaxHealth;
    }

    public List<BlockPos> activeSpawnLocations() {
        return activeSpawnLocations;
    }

    public BlockPos playerPos() {
        return playerPos;
    }

    /**
     * Schreibt Packet-Daten in Buffer
     */
    public void write(PacketByteBuf buf) {
        // Defender-Liste
        buf.writeInt(defenders.size());
        for (DefenderData defender : defenders) {
            // UUID
            buf.writeUuid(defender.uuid);

            // Name & Class
            buf.writeString(defender.name);
            buf.writeEnumConstant(defender.villagerClass);

            // Level & XP
            buf.writeInt(defender.level);
            buf.writeInt(defender.xp);

            // Stats
            buf.writeInt(defender.kills);
            buf.writeInt(defender.damageDealt);
            buf.writeInt(defender.wavesCompleted);
            buf.writeInt(defender.healingDone);

            // Position & HP
            buf.writeBlockPos(defender.position);
            buf.writeFloat(defender.health);
            buf.writeFloat(defender.maxHealth);

            // Behavior
            buf.writeBoolean(defender.following);
        }

        // Village Core
        buf.writeBoolean(villageCorePos != null);
        if (villageCorePos != null) {
            buf.writeBlockPos(villageCorePos);
            buf.writeInt(coreHealth);
            buf.writeInt(coreMaxHealth);
        }

        // Spawn-Locations (für Map-Overlay)
        buf.writeInt(activeSpawnLocations.size());
        for (BlockPos pos : activeSpawnLocations) {
            buf.writeBlockPos(pos);
        }

        // Player-Position
        buf.writeBoolean(playerPos != null);
        if (playerPos != null) {
            buf.writeBlockPos(playerPos);
        }
    }

    /**
     * Liest Packet-Daten aus Buffer
     */
    public static DefenderSyncPacket read(PacketByteBuf buf) {
        int defenderCount = buf.readInt();
        List<DefenderData> defenders = new ArrayList<>(defenderCount);

        for (int i = 0; i < defenderCount; i++) {
            UUID uuid = buf.readUuid();
            String name = buf.readString();
            VillagerClass villagerClass = buf.readEnumConstant(VillagerClass.class);
            int level = buf.readInt();
            int xp = buf.readInt();
            int kills = buf.readInt();
            int damageDealt = buf.readInt();
            int wavesCompleted = buf.readInt();
            int healingDone = buf.readInt();
            BlockPos position = buf.readBlockPos();
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();
            boolean following = buf.readBoolean();

            defenders.add(new DefenderData(
                uuid, name, villagerClass, level, xp,
                kills, damageDealt, wavesCompleted, healingDone,
                position, health, maxHealth, following
            ));
        }

        // Village Core
        BlockPos corePos = null;
        int coreHealth = 0;
        int coreMaxHealth = 0;
        if (buf.readBoolean()) {
            corePos = buf.readBlockPos();
            coreHealth = buf.readInt();
            coreMaxHealth = buf.readInt();
        }

        // Spawn-Locations
        int spawnCount = buf.readInt();
        List<BlockPos> spawnLocations = new ArrayList<>(spawnCount);
        for (int i = 0; i < spawnCount; i++) {
            spawnLocations.add(buf.readBlockPos());
        }

        // Player-Position
        BlockPos playerPos = null;
        if (buf.readBoolean()) {
            playerPos = buf.readBlockPos();
        }

        return new DefenderSyncPacket(defenders, corePos, coreHealth, coreMaxHealth, spawnLocations, playerPos);
    }

    /**
     * Vereinfachte Defender-Daten für Client-Darstellung
     */
    public record DefenderData(
        UUID uuid,
        String name,
        VillagerClass villagerClass,
        int level,
        int xp,
        int kills,
        int damageDealt,
        int wavesCompleted,
        int healingDone,
        BlockPos position,
        float health,
        float maxHealth,
        boolean following
    ) {}
}
