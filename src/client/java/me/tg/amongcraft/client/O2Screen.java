package me.tg.amongcraft.client;

import me.tg.amongcraft.Amongcraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

import io.netty.buffer.Unpooled;

@Environment(EnvType.CLIENT)
public class O2Screen extends Screen {
    private final String code = String.format("%06d", new Random().nextInt(1_000_000));
    private final BlockPos pos;
    private final String task;
    private TextFieldWidget input;

    public O2Screen(BlockPos pos, String task) {
        super(Text.literal("O2 Sabotage"));
        this.pos = pos;
        this.task = task;
    }

    @Override
    protected void init() {
        input = new TextFieldWidget(textRenderer, width / 2 - 60, height / 2 - 10, 120, 20, Text.literal(""));
        addSelectableChild(input);
        addDrawableChild(input);
        addDrawableChild(ButtonWidget.builder(Text.literal("Submit"), btn -> {
            if (code.equals(input.getText())) {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeString(task);
                ClientPlayNetworking.send(Amongcraft.SABOTAGE_RESOLVE, buf);

                client.setScreen(null); // close screen
            }
        }).position(width / 2 - 40, height / 2 + 20).size(80, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.drawTextWithShadow(textRenderer, "Code: " + code, width / 2 - textRenderer.getWidth("Code: " + code) / 2, 40, 0xAAAAAA);
        super.render(ctx, mouseX, mouseY, delta);
    }
}

