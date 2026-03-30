package me.tg.amongcraft.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class SabotageScreenClient extends Screen {
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private String currentSubMenu = null;
    private boolean awaitingDoorList = false;

    public static List<String> cachedDoorIds = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;

    public SabotageScreenClient() {
        super(Text.of("Sabotage Menu"));
    }

    @Override
    protected void init() {
        buttons.clear();
        this.clearChildren();
        scrollOffset = 0;

        if ("doors".equals(currentSubMenu)) {
            if (!awaitingDoorList) {
                awaitingDoorList = true;
            }

            updateDoorScreen();

        } else {
            int y = 40;
            List<String> sabotageTypes = List.of("doors", "reactor", "o2", "lights", "comms");
            for (String sabotage : sabotageTypes) {
                ButtonWidget button = ButtonWidget.builder(Text.of("Sabotage: " + sabotage), btn -> {
                    if (sabotage.equals("doors")) {
                        currentSubMenu = "doors";
                        awaitingDoorList = false;
                        init();
                    } else {
                        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                        buf.writeString(sabotage);
                        ClientPlayNetworking.send(new Identifier("amongcraft", "sabotage_trigger"), buf);
                        this.close();
                    }
                }).position(this.width / 2 - 100, y).size(200, 20).build();
                this.addDrawableChild(button);
                buttons.add(button);
                y += 24;
            }

            // No scrolling needed for sabotage list
            maxScroll = 0;
        }
    }

    public void receiveDoorList(List<String> doorIds) {
        cachedDoorIds = new ArrayList<>(doorIds);
    }

    public void updateDoorScreen() {
        buttons.clear();
        this.clearChildren();

        int y = 50;

        // Back button at top (static, not scrollable)
        ButtonWidget back = ButtonWidget.builder(Text.of("< Back"), btn -> {
            currentSubMenu = null;
            awaitingDoorList = false;
            init();
        }).position(this.width / 2 - 100, 20).size(200, 20).build();
        this.addDrawableChild(back);

        // Scrollable door buttons
        for (String id : cachedDoorIds) {
            ButtonWidget button = ButtonWidget.builder(Text.of("Toggle Door: " + id), btn -> {
                PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeString(id);
                ClientPlayNetworking.send(new Identifier("amongcraft", "toggle_door"), buf);
                this.close();
            }).position(this.width / 2 - 100, y).size(200, 20).build();
            this.addDrawableChild(button);
            buttons.add(button);
            y += 24;
        }

        int totalContentHeight = y;
        int visibleHeight = this.height - 50; // everything below the back button
        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset += (int)(amount * -20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Reposition scrollable buttons
        int baseY = 50;
        for (int i = 0; i < buttons.size(); i++) {
            ButtonWidget button = buttons.get(i);
            int y = baseY + (i * 24) - scrollOffset;
            button.setY(y);
        }

        return true;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);
        drawContext.drawTextWithShadow(this.textRenderer, this.title.getString(),
                this.width / 2 - textRenderer.getWidth(this.title) / 2, 5, 0xFFFFFF);

        super.render(drawContext, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }
}
