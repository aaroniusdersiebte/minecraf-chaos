package com.chaosstream.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client → Server Packet für Defender-GUI-Actions
 *
 * Unterstützt folgende Action-Typen:
 * - UPGRADE: Equipment upgraden (konsumiert Items aus Spieler-Inventar)
 * - HEAL: Defender heilen (konsumiert Items aus Spieler-Inventar)
 * - GIVE_ITEM: Item vom Spieler zum Defender-Inventar übertragen
 * - TAKE_ITEM: Item vom Defender-Inventar zum Spieler übertragen
 */
public class DefenderActionPacket {
    public static final Identifier ID = new Identifier("chaosstream", "defender_action");

    private final ActionType actionType;
    private final UUID defenderUUID;
    private final ItemStack itemStack; // Für GIVE_ITEM/TAKE_ITEM
    private final int slotIndex;       // Inventar-Slot-Index (0-8)

    public DefenderActionPacket(ActionType actionType, UUID defenderUUID, ItemStack itemStack, int slotIndex) {
        this.actionType = actionType;
        this.defenderUUID = defenderUUID;
        this.itemStack = itemStack;
        this.slotIndex = slotIndex;
    }

    // Convenience-Konstruktor für Actions ohne Item-Data
    public DefenderActionPacket(ActionType actionType, UUID defenderUUID) {
        this(actionType, defenderUUID, ItemStack.EMPTY, -1);
    }

    public ActionType actionType() {
        return actionType;
    }

    public UUID defenderUUID() {
        return defenderUUID;
    }

    public ItemStack itemStack() {
        return itemStack;
    }

    public int slotIndex() {
        return slotIndex;
    }

    /**
     * Schreibt Packet-Daten in Buffer
     */
    public void write(PacketByteBuf buf) {
        // Action-Type
        buf.writeEnumConstant(actionType);

        // Defender-UUID
        buf.writeUuid(defenderUUID);

        // ItemStack (kann leer sein)
        buf.writeItemStack(itemStack);

        // Slot-Index (-1 wenn nicht relevant)
        buf.writeInt(slotIndex);
    }

    /**
     * Liest Packet-Daten aus Buffer
     */
    public static DefenderActionPacket read(PacketByteBuf buf) {
        ActionType actionType = buf.readEnumConstant(ActionType.class);
        UUID defenderUUID = buf.readUuid();
        ItemStack itemStack = buf.readItemStack();
        int slotIndex = buf.readInt();

        return new DefenderActionPacket(actionType, defenderUUID, itemStack, slotIndex);
    }

    /**
     * Action-Typen für Defender-GUI
     */
    public enum ActionType {
        UPGRADE,      // Equipment upgraden (konsumiert Items aus Spieler-Inventar)
        HEAL,         // Defender heilen (konsumiert Items aus Spieler-Inventar)
        GIVE_ITEM,    // Item vom Spieler zum Defender-Inventar
        TAKE_ITEM     // Item vom Defender-Inventar zum Spieler
    }
}
