package dev.evasex.ewaira.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ConfigScreenAccess {
    private ConfigScreenAccess() {}
    static void close(Minecraft client, Screen parent) { client.setScreen(parent); }
    static void rebuildTerrain(Minecraft client) { client.levelRenderer.allChanged(); }
}
