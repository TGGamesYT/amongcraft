package me.tg.amongcraft.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class CommsScreen extends Screen {
    private float rotation = 0f;
    private final float target = new Random().nextFloat();
    private final BlockPos pos;  // You need to track pos if you want to use it, add to constructor

    public CommsScreen(BlockPos pos) {
        super(Text.literal("Fix Comms"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        addDrawableChild(new SliderWidget(width / 2 - 100, height / 2, 200, 20, Text.literal("Rotate"), rotation) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Rotation: " + MathHelper.floor(rotation * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                rotation = (float) value;
                if (Math.abs(rotation - target) < 0.01f) {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeString("comms"); // task identifier
                    ClientPlayNetworking.send(new Identifier("amongcraft", "sabotage_resolve_try"), buf);
                    close();
                }
            }
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        String ttl = title.getString();
        ctx.drawTextWithShadow(textRenderer, ttl, width / 2 - textRenderer.getWidth(ttl) / 2, 20, 0xFFFFFF);
        String tgt = "Target: " + MathHelper.floor(target * 100) + "%";
        ctx.drawTextWithShadow(textRenderer, tgt, width / 2 - textRenderer.getWidth(tgt) / 2, 40, 0xAAAAAA);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
