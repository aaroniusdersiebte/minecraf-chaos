package com.chaosstream.client;

import com.chaosstream.VillagerClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Custom Map-Renderer für Minimap mit Terrain + Overlays.
 * Kein Widget, sondern einfache Render-Klasse mit Click-Handler.
 */
public class MapWidget {

    private final MapRenderer mapRenderer;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private BlockPos corePos;
    private int coreHealth;
    private int coreMaxHealth;
    private List<BlockPos> spawnLocations;
    private Map<UUID, DefenderMapData> defenders;
    private List<UUID> selectedDefenders; // Für Highlighting
    private BlockPos playerPos; // Spieler-Position

    // Klassen-Farben (RGB)
    private static final Map<VillagerClass, Integer> CLASS_COLORS = new HashMap<>();

    static {
        CLASS_COLORS.put(VillagerClass.WARRIOR, 0xFF3333); // Rot
        CLASS_COLORS.put(VillagerClass.ARCHER, 0x33FF33);  // Grün
        CLASS_COLORS.put(VillagerClass.HEALER, 0xFF69B4);  // Pink
        CLASS_COLORS.put(VillagerClass.BUILDER, 0xFFD700); // Gold
        CLASS_COLORS.put(VillagerClass.TANK, 0x3399FF);    // Blau
    }

    public MapWidget(int x, int y, MinecraftClient client) {
        this.x = x;
        this.y = y;
        this.width = MapRenderer.getMapSize();
        this.height = MapRenderer.getMapSize();
        this.mapRenderer = new MapRenderer(client);
        this.defenders = new HashMap<>();
        this.selectedDefenders = new ArrayList<>();
    }

    /**
     * Update-Methode für Daten vom Server (DefenderSyncPacket).
     */
    public void updateData(BlockPos corePos, int coreHealth, int coreMaxHealth,
                           List<BlockPos> spawnLocations, Map<UUID, DefenderMapData> defenders,
                           BlockPos playerPos) {
        this.corePos = corePos;
        this.coreHealth = coreHealth;
        this.coreMaxHealth = coreMaxHealth;
        this.spawnLocations = spawnLocations;
        this.defenders = defenders;
        this.playerPos = playerPos;

        // Update Terrain-Cache (nur wenn nötig)
        if (corePos != null) {
            mapRenderer.updateTerrainCache(corePos);
        }
    }

    /**
     * Setzt die ausgewählten Defender (für Highlighting).
     */
    public void setSelectedDefenders(List<UUID> selected) {
        this.selectedDefenders = selected != null ? new ArrayList<>(selected) : new ArrayList<>();
    }

    /**
     * Rendert die Map.
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (corePos == null) {
            // Fallback: Zeige "Loading..." wenn keine Daten
            context.drawText(MinecraftClient.getInstance().textRenderer,
                "Map wird geladen...", x + 70, y + 95, 0xFFFFFF, true);
            return;
        }

        // 1. Rendere Terrain-Hintergrund
        context.drawTexture(mapRenderer.getTerrainTexture(),
            x, y, 0, 0,
            MapRenderer.getMapSize(), MapRenderer.getMapSize(),
            MapRenderer.getMapSize(), MapRenderer.getMapSize());

        // 2. Rendere Overlays
        renderSpawnLocations(context);
        renderDefenders(context);
        renderCore(context);
        renderPlayer(context);

        // 3. Rendere Rahmen um Map
        context.drawBorder(x, y, width, height, 0xFFAAAAAA);

        // 4. Hover-Effekt: Zeige Koordinaten wenn Maus über Map
        if (isMouseOver(mouseX, mouseY)) {
            BlockPos worldPos = screenToWorldPos(mouseX, mouseY);
            if (worldPos != null) {
                String coordText = "X: " + worldPos.getX() + " Z: " + worldPos.getZ();
                context.drawText(MinecraftClient.getInstance().textRenderer,
                    coordText, mouseX + 10, mouseY - 10, 0xFFFFFFFF, true);
            }
        }
    }

    /**
     * Rendert Wave-Spawn-Locations als rote Kreise.
     */
    private void renderSpawnLocations(DrawContext context) {
        if (spawnLocations == null || spawnLocations.isEmpty()) return;

        for (BlockPos spawnPos : spawnLocations) {
            int[] pixel = mapRenderer.worldToMapPixel(spawnPos, corePos);
            int screenX = x + pixel[0];
            int screenY = y + pixel[1];

            // Roter Kreis (6x6 Pixel)
            drawCircle(context, screenX, screenY, 3, 0xFFFF0000);
        }
    }

    /**
     * Rendert Defender als farbige Punkte (Klassen-Farbe).
     */
    private void renderDefenders(DrawContext context) {
        if (defenders == null || defenders.isEmpty()) return;

        for (Map.Entry<UUID, DefenderMapData> entry : defenders.entrySet()) {
            UUID uuid = entry.getKey();
            DefenderMapData defender = entry.getValue();

            int[] pixel = mapRenderer.worldToMapPixel(defender.pos, corePos);
            int screenX = x + pixel[0];
            int screenY = y + pixel[1];

            // Klassen-Farbe
            int color = CLASS_COLORS.getOrDefault(defender.villagerClass, 0xFFFFFFFF);

            // Ist dieser Defender ausgewählt?
            boolean isSelected = selectedDefenders.contains(uuid);

            if (isSelected) {
                // SELECTED: Größerer Kreis + weißer Highlight-Ring + Name
                // Weißer Highlight-Ring (8x8 Pixel)
                drawCircle(context, screenX, screenY, 4, 0xFFFFFFFF);

                // Größerer Punkt (6x6 Pixel) mit schwarzem Rahmen
                context.fill(screenX - 3, screenY - 3, screenX + 3, screenY + 3, 0xFF000000); // Rahmen
                context.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, color | 0xFF000000); // Füllung

                // Zeige Namen über dem Defender
                String name = defender.name != null ? defender.name : "Defender";
                int nameWidth = MinecraftClient.getInstance().textRenderer.getWidth(name);
                context.drawText(MinecraftClient.getInstance().textRenderer,
                    name, screenX - nameWidth / 2, screenY - 12, 0xFFFFFFFF, true);
            } else {
                // NORMAL: Normaler Punkt (4x4 Pixel) mit schwarzem Rahmen
                context.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, 0xFF000000); // Rahmen
                context.fill(screenX - 1, screenY - 1, screenX + 1, screenY + 1, color | 0xFF000000); // Füllung
            }

            // Optional: Zeige HP-Bar unter Defender (für Low-HP)
            if (defender.healthPercentage < 40) {
                int barWidth = 8;
                int barHeight = 2;
                int barX = screenX - barWidth / 2;
                int barY = screenY + (isSelected ? 5 : 3); // Mehr Platz wenn Name angezeigt wird

                // Hintergrund (schwarz)
                context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);

                // HP-Bar (Rot/Gelb/Grün je nach HP%)
                int hpColor = defender.healthPercentage < 30 ? 0xFFFF0000 :
                              defender.healthPercentage < 60 ? 0xFFFFFF00 : 0xFF00FF00;
                int hpBarWidth = (int) (barWidth * defender.healthPercentage / 100.0);
                context.fill(barX, barY, barX + hpBarWidth, barY + barHeight, hpColor);
            }
        }
    }

    /**
     * Rendert Village Core als gelbes Herz-Icon.
     */
    private void renderCore(DrawContext context) {
        int centerX = x + MapRenderer.getMapSize() / 2;
        int centerY = y + MapRenderer.getMapSize() / 2;

        // Gelbes Herz (8x8 Pixel, vereinfacht als Raute)
        drawHeart(context, centerX, centerY, 0xFFFFD700);

        // Core HP-Text darunter
        String hpText = coreHealth + "/" + coreMaxHealth;
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(hpText);
        context.drawText(MinecraftClient.getInstance().textRenderer,
            hpText, centerX - textWidth / 2, centerY + 6, 0xFFFFFFFF, true);
    }

    /**
     * Rendert Spieler-Position als weißen Pfeil.
     */
    private void renderPlayer(DrawContext context) {
        if (playerPos == null || corePos == null) return;

        int[] pixel = mapRenderer.worldToMapPixel(playerPos, corePos);
        int screenX = x + pixel[0];
        int screenY = y + pixel[1];

        // Weißer Kreis (größer als Defender)
        drawCircle(context, screenX, screenY, 4, 0xFFFFFFFF);

        // Schwarzer Punkt in der Mitte
        context.fill(screenX - 1, screenY - 1, screenX + 1, screenY + 1, 0xFF000000);

        // "YOU" Text darunter
        String youText = "YOU";
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(youText);
        context.drawText(MinecraftClient.getInstance().textRenderer,
            youText, screenX - textWidth / 2, screenY + 6, 0xFFFFFFFF, true);
    }

    /**
     * Zeichnet einen einfachen Kreis (approximiert als gefüllter Bereich).
     */
    private void drawCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    context.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    /**
     * Zeichnet ein vereinfachtes Herz-Icon (Raute).
     */
    private void drawHeart(DrawContext context, int centerX, int centerY, int color) {
        // Einfaches Herz als Raute 8x8 Pixel
        int[][] heartPixels = {
            {0, -3}, {-1, -2}, {1, -2},
            {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {2, -1},
            {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {2, 0},
            {-2, 1}, {-1, 1}, {0, 1}, {1, 1}, {2, 1},
            {-1, 2}, {0, 2}, {1, 2},
            {0, 3}
        };

        for (int[] pixel : heartPixels) {
            context.fill(centerX + pixel[0], centerY + pixel[1],
                        centerX + pixel[0] + 1, centerY + pixel[1] + 1, color);
        }
    }

    /**
     * Prüft ob Maus über Map ist.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    /**
     * Konvertiert Screen-Koordinaten zu World-BlockPos.
     * Gibt die Position zurück wo der Spieler geklickt hat.
     */
    public BlockPos screenToWorldPos(double mouseX, double mouseY) {
        if (corePos == null) return null;

        // Relative Pixel-Position auf Map
        int pixelX = (int) (mouseX - x);
        int pixelZ = (int) (mouseY - y);

        // Konvertiere Pixel zu relativen Block-Koordinaten
        int relativeX = (pixelX / MapRenderer.PIXELS_PER_BLOCK) - MapRenderer.getRenderRadius();
        int relativeZ = (pixelZ / MapRenderer.PIXELS_PER_BLOCK) - MapRenderer.getRenderRadius();

        // Absolut World-Position
        return new BlockPos(
            corePos.getX() + relativeX,
            corePos.getY(), // Y bleibt gleich (wird später vom Server korrigiert)
            corePos.getZ() + relativeZ
        );
    }

    /**
     * Gibt die Map-Bounds zurück (für Click-Detection im Screen).
     */
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /**
     * Cleanup beim Screen-Schließen.
     */
    public void close() {
        mapRenderer.close();
    }

    /**
     * Daten-Container für Defender auf der Map.
     */
    public static class DefenderMapData {
        public final BlockPos pos;
        public final VillagerClass villagerClass;
        public final float healthPercentage;
        public final String name;

        public DefenderMapData(BlockPos pos, VillagerClass villagerClass, float healthPercentage, String name) {
            this.pos = pos;
            this.villagerClass = villagerClass;
            this.healthPercentage = healthPercentage;
            this.name = name;
        }
    }
}
