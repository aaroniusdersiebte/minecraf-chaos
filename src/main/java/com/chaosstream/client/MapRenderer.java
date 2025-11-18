package com.chaosstream.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Rendert eine minimalistische Top-Down-Minimap um das Village Core.
 * Optimiert für Performance durch Terrain-Caching.
 */
public class MapRenderer {

    private static final int MAP_SIZE = 200; // Pixel-Größe der Map
    private static final int RENDER_RADIUS = 50; // Blöcke um Core
    public static final int PIXELS_PER_BLOCK = 3; // Auflösung: 3 Pixel = 1 Block (public für MapWidget)

    private final MinecraftClient client;
    private NativeImage terrainCache;
    private NativeImageBackedTexture terrainTexture;
    private Identifier terrainTextureId;
    private BlockPos cachedCorePos;
    private long lastTerrainUpdate;

    // Block-zu-Farbe-Mapping (RGB als int)
    private static final Map<Block, Integer> BLOCK_COLORS = new HashMap<>();

    static {
        // Gras & Pflanzen (Grün)
        BLOCK_COLORS.put(Blocks.GRASS_BLOCK, 0x7CBD6B);
        BLOCK_COLORS.put(Blocks.TALL_GRASS, 0x7CBD6B);
        BLOCK_COLORS.put(Blocks.FERN, 0x7CBD6B);
        BLOCK_COLORS.put(Blocks.GRASS, 0x7CBD6B); // Short grass in 1.20.1

        // Stein (Grau)
        BLOCK_COLORS.put(Blocks.STONE, 0x7F7F7F);
        BLOCK_COLORS.put(Blocks.COBBLESTONE, 0x7F7F7F);
        BLOCK_COLORS.put(Blocks.ANDESITE, 0x858585);
        BLOCK_COLORS.put(Blocks.GRAVEL, 0x888888);

        // Erde (Braun)
        BLOCK_COLORS.put(Blocks.DIRT, 0x8C6646);
        BLOCK_COLORS.put(Blocks.COARSE_DIRT, 0x8C6646);
        BLOCK_COLORS.put(Blocks.PODZOL, 0x5C4630);

        // Wasser (Blau)
        BLOCK_COLORS.put(Blocks.WATER, 0x3F76E4);

        // Sand (Gelb/Beige)
        BLOCK_COLORS.put(Blocks.SAND, 0xDBD3A0);
        BLOCK_COLORS.put(Blocks.SANDSTONE, 0xD9C98A);

        // Holz (Dunkelbraun)
        BLOCK_COLORS.put(Blocks.OAK_LOG, 0x6F5235);
        BLOCK_COLORS.put(Blocks.BIRCH_LOG, 0xD7D3CB);
        BLOCK_COLORS.put(Blocks.SPRUCE_LOG, 0x4A3A28);
        BLOCK_COLORS.put(Blocks.OAK_PLANKS, 0x9C7F4D);

        // Laub (Dunkelgrün)
        BLOCK_COLORS.put(Blocks.OAK_LEAVES, 0x5A8A3E);
        BLOCK_COLORS.put(Blocks.BIRCH_LEAVES, 0x6FA85A);
        BLOCK_COLORS.put(Blocks.SPRUCE_LEAVES, 0x4A6B3A);

        // Schnee (Weiß)
        BLOCK_COLORS.put(Blocks.SNOW, 0xFFFFFE);
        BLOCK_COLORS.put(Blocks.SNOW_BLOCK, 0xFFFFFE);

        // Village-spezifische Blöcke
        BLOCK_COLORS.put(Blocks.COBBLESTONE_WALL, 0x787878);
        BLOCK_COLORS.put(Blocks.OAK_FENCE, 0x9C7F4D);
        BLOCK_COLORS.put(Blocks.TORCH, 0xFFAA00);
        BLOCK_COLORS.put(Blocks.BELL, 0xFFD700);

        // Obsidian (Schwarz - für Wave-Spawn-Marker)
        BLOCK_COLORS.put(Blocks.OBSIDIAN, 0x100020);
        BLOCK_COLORS.put(Blocks.GLOWSTONE, 0xFFEE88);
    }

    public MapRenderer(MinecraftClient client) {
        this.client = client;
        this.lastTerrainUpdate = 0;
    }

    /**
     * Initialisiert oder aktualisiert den Terrain-Cache.
     * Wird nur aufgerufen wenn Core-Position sich ändert oder alle 5 Sekunden.
     */
    public void updateTerrainCache(BlockPos corePos) {
        World world = client.world;
        if (world == null) return;

        long currentTime = System.currentTimeMillis();

        // Cache nur alle 5 Sekunden neu rendern (Performance!)
        if (cachedCorePos != null && cachedCorePos.equals(corePos)
            && currentTime - lastTerrainUpdate < 5000) {
            return;
        }

        // Erstelle neue NativeImage wenn nötig
        if (terrainCache == null) {
            terrainCache = new NativeImage(MAP_SIZE, MAP_SIZE, true);
            terrainTexture = new NativeImageBackedTexture(terrainCache);
            terrainTextureId = client.getTextureManager().registerDynamicTexture("chaos_minimap", terrainTexture);
        }

        // Rendere Terrain in festem Radius um Core
        int maxHeight = 0;
        int minHeight = 999;

        // Erste Pass: Finde Min/Max Höhe für Shading
        for (int x = -RENDER_RADIUS; x <= RENDER_RADIUS; x++) {
            for (int z = -RENDER_RADIUS; z <= RENDER_RADIUS; z++) {
                BlockPos worldPos = corePos.add(x, 0, z);
                BlockPos topBlockPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, worldPos);
                int height = topBlockPos.getY();
                maxHeight = Math.max(maxHeight, height);
                minHeight = Math.min(minHeight, height);
            }
        }

        int heightRange = Math.max(1, maxHeight - minHeight);

        // Zweite Pass: Rendere mit Höhen-Shading
        for (int x = -RENDER_RADIUS; x <= RENDER_RADIUS; x++) {
            for (int z = -RENDER_RADIUS; z <= RENDER_RADIUS; z++) {
                BlockPos worldPos = corePos.add(x, 0, z);

                // Finde obersten nicht-Luft-Block (Heightmap)
                BlockPos topBlockPos = world.getTopPosition(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, worldPos);
                BlockState topBlock = world.getBlockState(topBlockPos);

                // Hole Basis-Farbe für Block
                int baseColor = getBlockColor(topBlock.getBlock());

                // Höhen-Shading: Höhere Blöcke heller, niedrigere dunkler
                int height = topBlockPos.getY();
                float heightFactor = (float)(height - minHeight) / heightRange;
                heightFactor = 0.7f + (heightFactor * 0.3f); // 0.7 bis 1.0

                int r = (int)(((baseColor >> 16) & 0xFF) * heightFactor);
                int g = (int)(((baseColor >> 8) & 0xFF) * heightFactor);
                int b = (int)((baseColor & 0xFF) * heightFactor);
                int color = (r << 16) | (g << 8) | b;

                // Konvertiere World-Koordinaten zu Map-Pixel
                int pixelX = (x + RENDER_RADIUS) * PIXELS_PER_BLOCK;
                int pixelZ = (z + RENDER_RADIUS) * PIXELS_PER_BLOCK;

                // Zeichne Block als 3x3 Pixel-Bereich
                for (int px = 0; px < PIXELS_PER_BLOCK; px++) {
                    for (int pz = 0; pz < PIXELS_PER_BLOCK; pz++) {
                        int finalX = pixelX + px;
                        int finalZ = pixelZ + pz;

                        if (finalX >= 0 && finalX < MAP_SIZE && finalZ >= 0 && finalZ < MAP_SIZE) {
                            // Setze Pixel (ABGR-Format für NativeImage)
                            terrainCache.setColor(finalX, finalZ, color | 0xFF000000);
                        }
                    }
                }
            }
        }

        // Upload Texture zu GPU
        terrainTexture.upload();

        cachedCorePos = corePos;
        lastTerrainUpdate = currentTime;
    }

    /**
     * Gibt die Farbe für einen Block zurück.
     * Fallback: Grün für unbekannte Blöcke.
     */
    private int getBlockColor(Block block) {
        return BLOCK_COLORS.getOrDefault(block, 0x7CBD6B); // Default: Gras-Grün
    }

    /**
     * Konvertiert World-Koordinaten (relativ zu Core) zu Map-Pixel-Koordinaten.
     */
    public int[] worldToMapPixel(BlockPos worldPos, BlockPos corePos) {
        int relativeX = worldPos.getX() - corePos.getX();
        int relativeZ = worldPos.getZ() - corePos.getZ();

        int pixelX = (relativeX + RENDER_RADIUS) * PIXELS_PER_BLOCK + (PIXELS_PER_BLOCK / 2);
        int pixelZ = (relativeZ + RENDER_RADIUS) * PIXELS_PER_BLOCK + (PIXELS_PER_BLOCK / 2);

        return new int[]{pixelX, pixelZ};
    }

    /**
     * Gibt die gecachte Terrain-Texture zurück.
     */
    public Identifier getTerrainTexture() {
        return terrainTextureId;
    }

    /**
     * Cleanup-Methode beim Screen-Schließen.
     */
    public void close() {
        if (terrainCache != null) {
            terrainCache.close();
            terrainCache = null;
        }
        if (terrainTextureId != null && client.getTextureManager() != null) {
            client.getTextureManager().destroyTexture(terrainTextureId);
            terrainTextureId = null;
        }
    }

    public static int getMapSize() {
        return MAP_SIZE;
    }

    public static int getRenderRadius() {
        return RENDER_RADIUS;
    }
}
