package com.example.modmanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class ModManagerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Adds a "Mod Manager" button in the top-left corner of the title and pause screens.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
                Button button = Button.builder(Component.literal("Mod Manager"),
                                b -> Minecraft.getInstance().setScreen(new ManagerScreen(screen)))
                        .bounds(6, 6, 90, 20)
                        .build();
                Screens.getButtons(screen).add(button);
            }
        });
    }
}
