package com.chaosstream.client;

import com.chaosstream.client.screen.DefenderListScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Registriert und verwaltet Keybinds für Chaos Mod
 */
public class ChaosKeybinds {
    private static KeyBinding openDefenderManagementKey;

    /**
     * Registriert alle Keybinds
     */
    public static void register() {
        // Keybind: 'K' für Defender Management Screen
        openDefenderManagementKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chaosstream.open_defender_management", // Translation-Key
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.chaosstream" // Category
        ));

        // Client Tick Event - Check für Keybind-Press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDefenderManagementKey.wasPressed()) {
                if (client.player != null) {
                    // Öffne DefenderListScreen
                    client.setScreen(new DefenderListScreen(client.currentScreen));
                }
            }
        });
    }
}
