package me.tg.amongcraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class TabletScreen extends Screen {
    private final Map<UUID, Boolean> playerStatus; // UUID -> isAlive

    public TabletScreen(Map<UUID, Boolean> playerStatus) {
        super(Text.literal("Player Status"));
        this.playerStatus = playerStatus;
    }

    @Override
    protected void init() {
        super.init();

        int y = 20;
        int i = 0;

        for (PlayerListEntry entry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
            UUID uuid = entry.getProfile().getId();
            String name = entry.getProfile().getName();

            boolean isAlive = playerStatus.getOrDefault(uuid, false); // false if not listed

            ButtonWidget button = ButtonWidget.builder(Text.literal(name), b -> {})
                    .dimensions(this.width / 2 - 60, y + i * 24, 120, 20)
                    .build();

            button.active = isAlive;
            this.addDrawableChild(button);
            i++;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
