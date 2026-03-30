package me.tg.amongcraft.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ReactorScreen extends Screen {
    private final BlockPos pos;
    private final String task;
    private boolean holding = false;

    public ReactorScreen(BlockPos pos, String task) {
        super(Text.literal("Reactor Sabotage"));
        this.pos = pos;
        this.task = task;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Hold"), btn -> holding = true)
                .position(width / 2 - 50, height / 2 - 10).size(100, 20).build());
    }

    @Override
    public void tick() {
        if (holding) {
            // ✅ Send packet every tick while holding
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(task);       // e.g., "reactor-2"
            ClientPlayNetworking.send(new Identifier("amongcraft", "sabotage_resolve_try"), buf);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        holding = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        String title = this.title.getString();
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, width / 2 - tw / 2, 20, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
