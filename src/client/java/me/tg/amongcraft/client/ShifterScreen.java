package me.tg.amongcraft.client;

import me.tg.amongcraft.Amongcraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class ShifterScreen extends Screen {
    private final Map<UUID, Boolean> playerStatus;

    public ShifterScreen(Map<UUID, Boolean> playerStatus) {
        super(Text.literal("Choose a player to mimic"));
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
            boolean isAlive = playerStatus.getOrDefault(uuid, false);

            ButtonWidget button = ButtonWidget.builder(Text.literal(name), b -> {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeUuid(uuid);
                ClientPlayNetworking.send(Amongcraft.SHIFTER_SELECT_PACKET, buf);
                MinecraftClient.getInstance().setScreen(null);
            }).dimensions(this.width / 2 - 60, y + i * 24, 120, 20).build();

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
