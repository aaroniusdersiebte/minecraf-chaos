package com.chaosstream.client.screen;

import com.chaosstream.client.ClientNetworkHandler;
import com.chaosstream.network.DefenderActionPacket;
import com.chaosstream.network.DefenderCommandPacket;
import com.chaosstream.network.DefenderInventoryPacket;
import com.chaosstream.network.DefenderSyncPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Haupt-GUI für einzelnen Defender (geöffnet per Rechtsklick auf Defender)
 * Zeigt Stats, HP/XP-Bars, Equipment-Icons, Inventar, Buttons
 */
public class DefenderManagementScreen extends Screen {
    private final Screen parent;
    private final DefenderSyncPacket.DefenderData defender;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 260; // Erhöht für Inventar-Slots

    // Textures (später implementieren - für MVP verwenden wir einfache Rechtecke)
    private static final Identifier TEXTURE = new Identifier("chaosstream", "textures/gui/defender_management.png");

    // Lokaler Follow-Status (wird aktualisiert wenn Button gedrückt wird)
    private boolean isFollowing;
    private ButtonWidget followButton;

    // Ticker für Equipment-Loading (prüft alle 10 Ticks ob Daten verfügbar)
    private int loadingTicker = 0;
    private boolean inventoryLoaded = false;

    public DefenderManagementScreen(Screen parent, DefenderSyncPacket.DefenderData defender) {
        super(Text.literal("§6§lDefender: §f" + defender.name()));
        this.parent = parent;
        this.defender = defender;
        this.isFollowing = defender.following();
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        // Folgen/Bleib Button (links)
        String followText = isFollowing ? "§e⏸ Bleiben" : "§a▶ Folgen";
        followButton = ButtonWidget.builder(Text.literal(followText), button -> {
                    // Toggle Follow-Status
                    isFollowing = !isFollowing;
                    sendFollowCommand(isFollowing);
                    // Update Button-Text
                    button.setMessage(Text.literal(isFollowing ? "§e⏸ Bleiben" : "§a▶ Folgen"));
                })
                .dimensions(x + 10, y + 130, 70, 20)
                .build();
        this.addDrawableChild(followButton);

        // Upgrade Button (mitte links)
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("§6⚔ Aufwerten"), button -> {
                    sendAction(DefenderActionPacket.ActionType.UPGRADE);
                })
                .dimensions(x + 10, y + 220, 70, 20)
                .build()
        );

        // Heal Button (mitte rechts)
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("§c💚 Heilen"), button -> {
                    sendAction(DefenderActionPacket.ActionType.HEAL);
                })
                .dimensions(x + 95, y + 220, 70, 20)
                .build()
        );

        // Schließen Button
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("§8Schließen"), button -> this.close())
                .dimensions(x + 10, y + 235, 156, 20)
                .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Prüfe alle 10 Ticks ob Inventar-Daten verfügbar sind
        loadingTicker++;
        if (loadingTicker % 10 == 0 && !inventoryLoaded) {
            DefenderInventoryPacket inventoryPacket = ClientNetworkHandler.getDefenderInventory(defender.uuid());
            if (inventoryPacket != null) {
                inventoryLoaded = true;
            }
        }

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        // GUI-Hintergrund (einfaches Rechteck für MVP)
        context.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xC0101010);
        context.drawBorder(x, y, GUI_WIDTH, GUI_HEIGHT, 0xFF8B8B8B);

        // Titel
        context.drawText(
            this.textRenderer,
            this.title,
            x + 10,
            y + 10,
            0xFFFFFF,
            true
        );

        // Klasse + Level (mit Farb-Code)
        String classColorCode = defender.villagerClass().getColorCode();
        String classText = String.format("%s%s - Level %d",
            classColorCode,
            defender.villagerClass().getDisplayName(),
            defender.level()
        );
        context.drawText(
            this.textRenderer,
            Text.literal(classText),
            x + 10,
            y + 22,
            0xFFFFFF,
            false
        );

        // HP-Bar
        renderStatBar(context, x + 10, y + 40, 156, "HP",
            defender.health(), defender.maxHealth(), 0xFF00FF00, 0xFFFF0000);

        // XP-Bar
        int currentXP = defender.xp();
        int nextLevelXP = getXPForNextLevel(defender.level());
        renderStatBar(context, x + 10, y + 60, 156, "XP",
            currentXP, nextLevelXP, 0xFF00FFFF, 0xFF0088FF);

        // Stats
        context.drawText(this.textRenderer,
            Text.literal(String.format("§7💀 Kills: §e%d  §7💥 Schaden: §e%d",
                defender.kills(), defender.damageDealt())),
            x + 10, y + 80, 0xFFFFFF, false
        );
        context.drawText(this.textRenderer,
            Text.literal(String.format("§7🌊 Wellen: §e%d", defender.wavesCompleted())),
            x + 10, y + 92, 0xFFFFFF, false
        );

        // Equipment-Vorschau (Icons) - UNTER Follow-Button verschoben
        context.drawText(this.textRenderer,
            Text.literal("§7Ausrüstung:"),
            x + 10, y + 158, 0xFFFFFF, false
        );
        renderEquipment(context, x + 10, y + 170);

        // Defender-Inventar (9 Slots)
        context.drawText(this.textRenderer,
            Text.literal("§7Inventar:"),
            x + 10, y + 195, 0xFFFFFF, false
        );
        renderInventory(context, x + 10, y + 207);

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Rendert eine Stat-Bar (HP/XP)
     */
    private void renderStatBar(DrawContext context, int x, int y, int width, String label,
                                float current, float max, int fullColor, int emptyColor) {
        // Label
        context.drawText(this.textRenderer, Text.literal(label), x, y - 10, 0xFFFFFF, false);

        // Hintergrund
        context.fill(x, y, x + width, y + 10, 0xFF333333);

        // Füllung
        float percent = Math.min(current / max, 1.0f);
        int fillWidth = (int) (width * percent);
        int color = percent > 0.7f ? fullColor : (percent > 0.4f ? 0xFFFFFF00 : emptyColor);
        context.fill(x, y, x + fillWidth, y + 10, color);

        // Text
        String text = String.format("%.0f/%.0f", current, max);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(text),
            x + width / 2, y + 1, 0xFFFFFF);
    }

    /**
     * Rendert Equipment-Icons (Helm, Brust, Hose, Schuhe, Waffe)
     */
    private void renderEquipment(DrawContext context, int x, int y) {
        // Hole Equipment-Daten vom ClientNetworkHandler
        DefenderInventoryPacket inventoryPacket = ClientNetworkHandler.getDefenderInventory(defender.uuid());

        if (inventoryPacket == null || inventoryPacket.equipment() == null) {
            // Fallback: Zeige "Lädt..." wenn keine Daten verfügbar
            context.drawText(this.textRenderer,
                Text.literal("§7(Wird geladen...)"),
                x, y, 0x888888, false
            );
            return;
        }

        DefenderInventoryPacket.EquipmentData equipment = inventoryPacket.equipment();

        // Rendere Equipment-Slots (16x16 Icons mit 2 Pixel Abstand)
        int slotSize = 18;
        int spacing = 20;

        // Helm
        renderEquipmentSlot(context, x, y, equipment.helmet(), "Helm");
        // Brustplatte
        renderEquipmentSlot(context, x + spacing, y, equipment.chestplate(), "Brust");
        // Hose
        renderEquipmentSlot(context, x + spacing * 2, y, equipment.leggings(), "Hose");
        // Schuhe
        renderEquipmentSlot(context, x + spacing * 3, y, equipment.boots(), "Schuhe");
        // Waffe
        renderEquipmentSlot(context, x + spacing * 4, y, equipment.mainHand(), "Waffe");
    }

    /**
     * Rendert einen einzelnen Equipment-Slot mit Item-Icon
     */
    private void renderEquipmentSlot(DrawContext context, int x, int y, ItemStack stack, String label) {
        // Slot-Hintergrund (dunkelgrau)
        context.fill(x, y, x + 18, y + 18, 0xFF3C3C3C);

        // Slot-Rahmen (hellgrau)
        context.drawBorder(x, y, 18, 18, 0xFF8B8B8B);

        // Item-Icon rendern (falls vorhanden)
        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x + 1, y + 1);
            context.drawItemInSlot(this.textRenderer, stack, x + 1, y + 1);
        }
    }

    /**
     * Rendert Defender-Inventar (9 Slots in einer Reihe)
     */
    private void renderInventory(DrawContext context, int x, int y) {
        // Hole Inventar-Daten vom ClientNetworkHandler
        DefenderInventoryPacket inventoryPacket = ClientNetworkHandler.getDefenderInventory(defender.uuid());

        if (inventoryPacket == null || inventoryPacket.inventoryItems() == null) {
            // Fallback: Zeige "Lädt..." wenn keine Daten verfügbar
            context.drawText(this.textRenderer,
                Text.literal("§7(Wird geladen...)"),
                x, y, 0x888888, false
            );
            return;
        }

        List<ItemStack> inventory = inventoryPacket.inventoryItems();

        // Rendere 9 Inventar-Slots (18x18 Pixel mit 2 Pixel Abstand)
        int slotSize = 18;
        int spacing = 20;

        for (int i = 0; i < Math.min(9, inventory.size()); i++) {
            int slotX = x + (i % 9) * spacing;
            int slotY = y;

            renderInventorySlot(context, slotX, slotY, inventory.get(i), i);
        }
    }

    /**
     * Rendert einen einzelnen Inventar-Slot mit Item-Icon
     */
    private void renderInventorySlot(DrawContext context, int x, int y, ItemStack stack, int slotIndex) {
        // Slot-Hintergrund (dunkelgrau)
        context.fill(x, y, x + 18, y + 18, 0xFF3C3C3C);

        // Slot-Rahmen (hellgrau)
        context.drawBorder(x, y, 18, 18, 0xFF8B8B8B);

        // Item-Icon rendern (falls vorhanden)
        if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x + 1, y + 1);
            context.drawItemInSlot(this.textRenderer, stack, x + 1, y + 1);
        }
    }

    /**
     * Sendet Follow/Stay Command zum Server
     */
    private void sendFollowCommand(boolean follow) {
        DefenderCommandPacket.CommandType commandType = follow
            ? DefenderCommandPacket.CommandType.FOLLOW
            : DefenderCommandPacket.CommandType.STAY;

        DefenderCommandPacket packet = new DefenderCommandPacket(
            commandType,
            List.of(defender.uuid()),
            null
        );

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        ClientPlayNetworking.send(DefenderCommandPacket.ID, buf);
    }

    /**
     * Sendet Action (Upgrade/Heal) zum Server
     */
    private void sendAction(DefenderActionPacket.ActionType actionType) {
        DefenderActionPacket packet = new DefenderActionPacket(actionType, defender.uuid());

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        ClientPlayNetworking.send(DefenderActionPacket.ID, buf);
    }

    /**
     * Berechnet XP für nächstes Level
     */
    private int getXPForNextLevel(int level) {
        int[] XP_THRESHOLDS = {0, 10, 30, 60, 100};
        if (level >= 5) return Integer.MAX_VALUE;
        return XP_THRESHOLDS[level];
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
