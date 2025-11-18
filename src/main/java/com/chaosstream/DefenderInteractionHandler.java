package com.chaosstream;

import com.chaosstream.network.OpenDefenderGuiPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Handler für Rechtsklick-Interaktionen mit Defender-Villagern
 * Zeigt ein Chat-basiertes Management-Menü mit klickbaren Buttons
 *
 * @deprecated Ersetzt durch GUI-System (DefenderManagementScreen, DefenderListScreen)
 * Wird nur noch als Fallback für Server-Only-Modus verwendet.
 * Client-Side Spieler sollten Keybind 'K' für GUI verwenden.
 */
@Deprecated
public class DefenderInteractionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChaosMod");
    private final DefenderManager defenderManager;

    public DefenderInteractionHandler(DefenderManager defenderManager) {
        this.defenderManager = defenderManager;
    }

    /**
     * Registriert den UseEntityCallback Event-Handler
     */
    public void register() {
        UseEntityCallback.EVENT.register(this::onUseEntity);
    }

    /**
     * Wird aufgerufen wenn ein Spieler Rechtsklick auf eine Entity macht
     */
    private ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hitResult) {
        // Nur server-side
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        // Nur bei Haupt-Hand (verhindert doppelte Trigger)
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        // Prüfe ob es ein Villager ist
        if (!(entity instanceof VillagerEntity villager)) {
            return ActionResult.PASS;
        }

        // Prüfe ob es ein Defender ist
        DefenderVillager defender = defenderManager.getDefenderByEntityUUID(villager.getUuid());
        if (defender == null) {
            return ActionResult.PASS; // Kein Defender, normales Villager-Verhalten
        }

        // OP-Permission-Check
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§c[Defender] Nur Server-OPs können Defender verwalten!"), false);
            return ActionResult.FAIL;
        }

        // Wenn Spieler schleicht (Shift), öffne Standard-Villager-GUI
        if (player.isSneaking()) {
            return ActionResult.PASS;
        }

        // Sende Packet zum Client um GUI zu öffnen
        sendOpenGuiPacket((ServerPlayerEntity) player, defender, villager);

        // Sende Inventar-Daten (für Equipment-Icons)
        com.chaosstream.network.NetworkHandler.sendDefenderInventory((ServerPlayerEntity) player, defender);

        // WICHTIG: SUCCESS_NO_ITEM_USED verhindert Standard-Villager-Trading-GUI
        return ActionResult.success(world.isClient());
    }

    /**
     * Sendet OpenDefenderGuiPacket zum Client
     */
    private void sendOpenGuiPacket(ServerPlayerEntity player, DefenderVillager defender, VillagerEntity villager) {
        OpenDefenderGuiPacket packet = new OpenDefenderGuiPacket(
            defender.getUuid(),
            defender.getViewerName(),
            defender.getVillagerClass(),
            defender.getLevel(),
            defender.getXp(),
            defender.getKills(),
            defender.getDamageDealt(),
            defender.getWavesCompleted(),
            defender.getHealingDone(),
            villager.getBlockPos(),
            villager.getHealth(),
            villager.getMaxHealth(),
            defender.isFollowing()
        );

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        packet.write(buf);
        ServerPlayNetworking.send(player, OpenDefenderGuiPacket.ID, buf);

        LOGGER.debug("OpenDefenderGuiPacket gesendet an {} für Defender {}",
            player.getName().getString(), defender.getViewerName());
    }

    /**
     * Zeigt das Chat-basierte Management-Menü mit klickbaren Buttons
     */
    private void showDefenderMenu(ServerPlayerEntity player, DefenderVillager defender, VillagerEntity villager) {
        UUID defenderUuid = defender.getUuid();

        // Menü-Header
        player.sendMessage(Text.literal("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
        player.sendMessage(Text.literal("§6§l⚔ DEFENDER MANAGEMENT ⚔"), false);
        player.sendMessage(Text.literal("§7Defender: §f" + defender.getViewerName()), false);
        player.sendMessage(Text.literal("§7Klasse: " + getClassColor(defender.getVillagerClass()) + defender.getVillagerClass().getDisplayName()), false);
        player.sendMessage(Text.literal(""), false);

        // Button 1: Stats anzeigen (gratis)
        MutableText statsButton = Text.literal("§a§l[📊 STATS]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chaos defender-stats " + defenderUuid))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aKlicken um Stats anzuzeigen\n§7Kostenlos")))
            );

        // Button 2: Equipment upgraden (kostet Items)
        MutableText upgradeButton = Text.literal(" §e§l[⚔️ UPGRADE]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chaos defender-upgrade " + defenderUuid))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§eUpgrade Equipment\n§7Kosten: " + getUpgradeCost(defender))))
            );

        // Button 3: Heilen (kostet Items)
        MutableText healButton = Text.literal(" §d§l[💚 HEILEN]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chaos defender-heal " + defenderUuid))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§dDefender heilen\n§7Kosten: 1 Golden Apple oder 3 Emeralds")))
            );

        // Zeige erste Reihe Buttons
        MutableText row1 = Text.literal("");
        row1.append(statsButton);
        row1.append(upgradeButton);
        row1.append(healButton);
        player.sendMessage(row1, false);

        // Button 4: Befehle (Folge/Bleib)
        String followStatus = defender.isFollowing() ? "§aFolgt dir" : "§7Patrouilliert";
        String followCommand = defender.isFollowing() ? "stay" : "follow";
        String followLabel = defender.isFollowing() ? "BLEIB HIER" : "FOLGE MIR";

        MutableText commandButton = Text.literal("§b§l[🎯 " + followLabel + "]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chaos defender-command " + defenderUuid + " " + followCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§bWechsel Verhalten\n§7Aktuell: " + followStatus)))
            );

        // Button 5: Entlassen (mit Bestätigung)
        MutableText dismissButton = Text.literal(" §c§l[🚪 ENTLASSEN]")
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chaos defender-dismiss " + defenderUuid))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§cDefender permanent entfernen\n§4⚠ Nicht rückgängig machbar!")))
            );

        // Zeige zweite Reihe Buttons
        MutableText row2 = Text.literal("");
        row2.append(commandButton);
        row2.append(dismissButton);
        player.sendMessage(row2, false);

        // Menü-Footer
        player.sendMessage(Text.literal("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
    }

    /**
     * Gibt die Upgrade-Kosten als String zurück
     */
    private String getUpgradeCost(DefenderVillager defender) {
        int currentLevel = defender.getLevel();

        // Kosten skalieren mit Level
        switch (currentLevel) {
            case 1:
                return "8 Eisen";
            case 2:
                return "16 Eisen";
            case 3:
                return "8 Diamanten";
            case 4:
                return "4 Netherite Ingots";
            case 5:
                return "Max Level!";
            default:
                return "???";
        }
    }

    /**
     * Gibt die Farbe für eine Defender-Klasse zurück
     */
    private String getClassColor(VillagerClass villagerClass) {
        switch (villagerClass) {
            case WARRIOR:
                return "§c"; // Rot
            case ARCHER:
                return "§a"; // Grün
            case HEALER:
                return "§d"; // Pink
            case BUILDER:
                return "§e"; // Gelb
            case TANK:
                return "§9"; // Blau
            default:
                return "§7"; // Grau
        }
    }
}
