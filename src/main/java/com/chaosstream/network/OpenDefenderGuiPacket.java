package com.chaosstream.network;

import com.chaosstream.VillagerClass;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Server → Client Packet um Defender-GUI zu öffnen
 * Wird gesendet wenn Spieler auf Defender rechtsklickt
 */
public class OpenDefenderGuiPacket {
    public static final Identifier ID = new Identifier("chaosstream", "open_defender_gui");

    private final UUID defenderUUID;
    private final String name;
    private final VillagerClass villagerClass;
    private final int level;
    private final int xp;
    private final int kills;
    private final int damageDealt;
    private final int wavesCompleted;
    private final int healingDone;
    private final BlockPos position;
    private final float health;
    private final float maxHealth;
    private final boolean following;

    public OpenDefenderGuiPacket(
        UUID defenderUUID,
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
    ) {
        this.defenderUUID = defenderUUID;
        this.name = name;
        this.villagerClass = villagerClass;
        this.level = level;
        this.xp = xp;
        this.kills = kills;
        this.damageDealt = damageDealt;
        this.wavesCompleted = wavesCompleted;
        this.healingDone = healingDone;
        this.position = position;
        this.health = health;
        this.maxHealth = maxHealth;
        this.following = following;
    }

    /**
     * Schreibt Packet-Daten in Buffer
     */
    public void write(PacketByteBuf buf) {
        buf.writeUuid(defenderUUID);
        buf.writeString(name);
        buf.writeEnumConstant(villagerClass);
        buf.writeInt(level);
        buf.writeInt(xp);
        buf.writeInt(kills);
        buf.writeInt(damageDealt);
        buf.writeInt(wavesCompleted);
        buf.writeInt(healingDone);
        buf.writeBlockPos(position);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeBoolean(following);
    }

    /**
     * Liest Packet-Daten aus Buffer
     */
    public static OpenDefenderGuiPacket read(PacketByteBuf buf) {
        UUID defenderUUID = buf.readUuid();
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

        return new OpenDefenderGuiPacket(
            defenderUUID, name, villagerClass, level, xp,
            kills, damageDealt, wavesCompleted, healingDone,
            position, health, maxHealth, following
        );
    }

    // Getters
    public UUID defenderUUID() { return defenderUUID; }
    public String name() { return name; }
    public VillagerClass villagerClass() { return villagerClass; }
    public int level() { return level; }
    public int xp() { return xp; }
    public int kills() { return kills; }
    public int damageDealt() { return damageDealt; }
    public int wavesCompleted() { return wavesCompleted; }
    public int healingDone() { return healingDone; }
    public BlockPos position() { return position; }
    public float health() { return health; }
    public float maxHealth() { return maxHealth; }
    public boolean following() { return following; }

    /**
     * Konvertiert Packet zu DefenderSyncPacket.DefenderData
     */
    public DefenderSyncPacket.DefenderData toDefenderData() {
        return new DefenderSyncPacket.DefenderData(
            defenderUUID, name, villagerClass, level, xp,
            kills, damageDealt, wavesCompleted, healingDone,
            position, health, maxHealth, following
        );
    }
}
