package com.chaosstream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class CommandHandler {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        // /chaos add <amount>
        dispatcher.register(CommandManager.literal("chaos")
            .then(CommandManager.literal("add")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 1000))
                    .executes(CommandHandler::addChaos)))
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000))
                    .executes(CommandHandler::setChaos)))
            .then(CommandManager.literal("reset")
                .executes(CommandHandler::resetChaos))
            .then(CommandManager.literal("level")
                .executes(CommandHandler::getChaosLevel))
            .then(CommandManager.literal("wave")
                .executes(CommandHandler::forceWave))
            .then(CommandManager.literal("night")
                .executes(CommandHandler::setNight))
        );
    }

    private static int addChaos(CommandContext<ServerCommandSource> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        chaosManager.addChaos(amount);

        context.getSource().sendFeedback(
            () -> Text.literal("§aAdded " + amount + " chaos. Current level: §e" + chaosManager.getChaosLevel()),
            true
        );

        return 1;
    }

    private static int setChaos(CommandContext<ServerCommandSource> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        ChaosManager chaosManager = ChaosMod.getChaosManager();

        // Reset first, then add
        chaosManager.reset();
        if (amount > 0) {
            chaosManager.addChaos(amount);
        }

        context.getSource().sendFeedback(
            () -> Text.literal("§aChaos level set to: §e" + amount),
            true
        );

        return 1;
    }

    private static int resetChaos(CommandContext<ServerCommandSource> context) {
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        chaosManager.reset();

        context.getSource().sendFeedback(
            () -> Text.literal("§aChaos level reset to 0"),
            true
        );

        return 1;
    }

    private static int getChaosLevel(CommandContext<ServerCommandSource> context) {
        ChaosManager chaosManager = ChaosMod.getChaosManager();
        int level = chaosManager.getChaosLevel();
        int total = chaosManager.getTotalChaos();
        double multiplier = chaosManager.getSpawnMultiplier();

        context.getSource().sendFeedback(
            () -> Text.literal("§e=== Chaos Stats ===\n" +
                "§aCurrent Level: §e" + level + "\n" +
                "§aTotal Accumulated: §e" + total + "\n" +
                "§aSpawn Multiplier: §e" + String.format("%.2f", multiplier) + "x"),
            false
        );

        return 1;
    }

    private static int forceWave(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            ChaosManager chaosManager = ChaosMod.getChaosManager();
            SpawnHandler spawnHandler = ChaosMod.getSpawnHandler();

            int chaosLevel = chaosManager.getChaosLevel();
            if (chaosLevel <= 0) {
                context.getSource().sendError(
                    Text.literal("§cChaos level is 0! Use '/chaos add <amount>' first.")
                );
                return 0;
            }

            spawnHandler.getWaveManager().forceStartWave(
                player.getServerWorld(),
                player,
                chaosLevel
            );

            context.getSource().sendFeedback(
                () -> Text.literal("§c§lForcing wave spawn with chaos level: §e" + chaosLevel),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            ChaosMod.LOGGER.error("Error forcing wave", e);
            return 0;
        }
    }

    private static int setNight(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

            // Set time to 13000 (night start)
            player.getServerWorld().setTimeOfDay(13000);

            context.getSource().sendFeedback(
                () -> Text.literal("§aTime set to night (13000). Chaos waves will begin!"),
                true
            );

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
}
