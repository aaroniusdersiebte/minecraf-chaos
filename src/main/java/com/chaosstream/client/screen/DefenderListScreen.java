package com.chaosstream.client.screen;

import com.chaosstream.client.ClientNetworkHandler;
import com.chaosstream.network.DefenderSyncPacket;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Management-Screen für alle Defender (geöffnet per Keybind 'K')
 * Zeigt scrollbare Liste von Defendern
 * Click auf Defender → öffnet DefenderManagementScreen
 */
public class DefenderListScreen extends Screen {
    private DefenderListWidget defenderList;
    private final Screen parent;

    public DefenderListScreen(Screen parent) {
        super(Text.literal("Defender Management"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Defender-Liste Widget (scrollbar)
        int listWidth = Math.min(400, this.width - 40);
        int listHeight = this.height - 96;
        int listX = (this.width - listWidth) / 2;
        int listY = 48;

        this.defenderList = new DefenderListWidget(
            this.client,
            this,
            listWidth,
            listHeight,
            listY,
            listY + listHeight
        );
        this.addSelectableChild(this.defenderList);

        // Lade Defender-Daten von Client-Cache
        List<DefenderSyncPacket.DefenderData> defenders = ClientNetworkHandler.getCachedDefenders();
        this.defenderList.load(defenders);

        // "Schließen" Button
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Schließen"), button -> this.close())
                .dimensions(this.width / 2 - 75, this.height - 32, 150, 20)
                .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dunkler Hintergrund (wie DefenderManagementScreen, nicht Erde-Textur)
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        // Defender-Liste
        this.defenderList.render(context, mouseX, mouseY, delta);

        // Titel (zentriert oben)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            16,
            0xFFFFFF
        );

        // Defender-Count
        int defenderCount = ClientNetworkHandler.getCachedDefenders().size();
        String subtitle = String.format("§7%d Defender aktiv", defenderCount);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(subtitle),
            this.width / 2,
            28,
            0xAAAAAA
        );

        // Widgets (Buttons)
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Öffnet DefenderManagementScreen für einen spezifischen Defender
     */
    public void openDefenderManagement(DefenderSyncPacket.DefenderData defender) {
        if (this.client != null) {
            this.client.setScreen(new DefenderManagementScreen(this, defender));
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // Spiel läuft weiter
    }
}
