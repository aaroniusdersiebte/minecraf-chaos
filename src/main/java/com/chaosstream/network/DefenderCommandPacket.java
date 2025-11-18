package com.chaosstream.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server Packet für Defender-Commands (zukünftig für Rechtsklick-Menü)
 *
 * Unterstützt folgende Command-Typen:
 * - FOLLOW: Defender folgt Spieler
 * - STAY: Defender bleibt am aktuellen Ort und patrouilliert
 */
public class DefenderCommandPacket {
    public static final Identifier ID = new Identifier("chaosstream", "defender_command");

    private final CommandType commandType;
    private final List<UUID> defenderUUIDs;
    private final BlockPos targetPos;

    public DefenderCommandPacket(CommandType commandType, List<UUID> defenderUUIDs, BlockPos targetPos) {
        this.commandType = commandType;
        this.defenderUUIDs = defenderUUIDs;
        this.targetPos = targetPos;
    }

    public CommandType commandType() {
        return commandType;
    }

    public List<UUID> defenderUUIDs() {
        return defenderUUIDs;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    /**
     * Schreibt Packet-Daten in Buffer
     */
    public void write(PacketByteBuf buf) {
        // Command-Type (Enum ordinal)
        buf.writeEnumConstant(commandType);

        // Defender-UUIDs (für Formationen)
        buf.writeInt(defenderUUIDs.size());
        for (UUID uuid : defenderUUIDs) {
            buf.writeUuid(uuid);
        }

        // Target-Position (kann null sein bei STAY)
        buf.writeBoolean(targetPos != null);
        if (targetPos != null) {
            buf.writeBlockPos(targetPos);
        }
    }

    /**
     * Liest Packet-Daten aus Buffer
     */
    public static DefenderCommandPacket read(PacketByteBuf buf) {
        CommandType commandType = buf.readEnumConstant(CommandType.class);

        int uuidCount = buf.readInt();
        List<UUID> uuids = new ArrayList<>(uuidCount);
        for (int i = 0; i < uuidCount; i++) {
            uuids.add(buf.readUuid());
        }

        BlockPos targetPos = null;
        if (buf.readBoolean()) {
            targetPos = buf.readBlockPos();
        }

        return new DefenderCommandPacket(commandType, uuids, targetPos);
    }

    /**
     * Command-Typen für Defender (vereinfacht - Patrol/Formation-Commands entfernt)
     */
    public enum CommandType {
        FOLLOW,                     // Defender folgt Spieler
        STAY                        // Defender patrouilliert am aktuellen Ort (Standard AI)
    }
}
