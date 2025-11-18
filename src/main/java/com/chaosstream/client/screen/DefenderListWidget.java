package com.chaosstream.client.screen;

import com.chaosstream.VillagerClass;
import com.chaosstream.network.DefenderSyncPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Scrollbare Liste von Defendern für DefenderListScreen
 * Zeigt Defender mit Icon, Name, HP-Bar, Level
 */
public class DefenderListWidget extends AlwaysSelectedEntryListWidget<DefenderListWidget.DefenderEntry> {
    private final DefenderListScreen parent;

    public DefenderListWidget(MinecraftClient client, DefenderListScreen parent, int width, int height, int top, int bottom) {
        super(client, width, height, top, bottom, 42); // 42 Pixel pro Entry (für Stats-Zeile)
        this.parent = parent;
    }

    /**
     * Lädt Defender-Daten in die Liste
     */
    public void load(List<DefenderSyncPacket.DefenderData> defenders) {
        this.clearEntries();
        for (DefenderSyncPacket.DefenderData defender : defenders) {
            this.addEntry(new DefenderEntry(defender));
        }
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.width - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    /**
     * Entry für einen einzelnen Defender in der Liste
     */
    public class DefenderEntry extends Entry<DefenderEntry> {
        private final DefenderSyncPacket.DefenderData defender;

        public DefenderEntry(DefenderSyncPacket.DefenderData defender) {
            this.defender = defender;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Name + Level (mit Klassen-Farbe als String)
            String classColorCode = defender.villagerClass().getColorCode();
            String name = String.format("%s%s - Lvl %d", classColorCode, defender.name(), defender.level());
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(name),
                x + 5, y + 5, 0xFFFFFF, true
            );

            // HP-Bar
            float healthPercent = defender.health() / defender.maxHealth();
            int barWidth = 100;
            int barHeight = 6;
            int barX = x + 5;
            int barY = y + 20;

            // HP-Bar Hintergrund (Grau)
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

            // HP-Bar Füllung (Farbe basierend auf HP%)
            int fillColor;
            if (healthPercent > 0.7f) {
                fillColor = 0xFF00FF00; // Grün
            } else if (healthPercent > 0.4f) {
                fillColor = 0xFFFFFF00; // Gelb
            } else {
                fillColor = 0xFFFF0000; // Rot
            }
            int fillWidth = (int) (barWidth * healthPercent);
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

            // HP-Text
            String hpText = String.format("%.0f/%.0f HP", defender.health(), defender.maxHealth());
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(hpText),
                barX + barWidth + 5, barY - 1, 0xFFFFFF, false
            );

            // Stats (Kills & Damage) unterhalb HP-Bar
            String statsText = String.format("§7💀 %d  §7💥 %d", defender.kills(), defender.damageDealt());
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(statsText),
                x + 5, y + 28, 0xFFFFFF, false
            );

            // Klasse (rechts, mit Farb-Code)
            String className = classColorCode + defender.villagerClass().getDisplayName();
            context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(className),
                x + entryWidth - 60, y + 5, 0xFFFFFF, false
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) { // Left-Click
                // Öffne DefenderManagementScreen für diesen Defender
                parent.openDefenderManagement(defender);
                return true;
            }
            return false;
        }

        @Override
        public Text getNarration() {
            return Text.literal(String.format("%s - Level %d", defender.name(), defender.level()));
        }
    }
}
