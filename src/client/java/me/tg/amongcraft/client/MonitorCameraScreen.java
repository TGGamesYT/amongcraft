package me.tg.amongcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class MonitorCameraScreen extends Screen {
    private final String identifier;
    private final boolean type; // true = 2x2, false = single + scroll
    private final int maxCams;
    private int selectedCamera = 1;

    public MonitorCameraScreen(String identifier, boolean type, int maxCams, BlockPos pos) {
        super(Text.of("Camera Monitor"));
        this.identifier = identifier;
        this.type = type;
        this.maxCams = maxCams;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (type) {
            // 2x2 cameras (1-4)
            for (int i = 0; i < 4; i++) {
                Identifier tex = CameraRenderManager.getInstance().getCameraTexture(identifier, i + 1);
                context.drawTexture(tex, xOffset(i), yOffset(i), 0, 0, 0, 128, 72, 128, 72);
            }
        } else {
            Identifier tex = CameraRenderManager.getInstance().getCameraTexture(identifier, selectedCamera);
            context.drawTexture(tex, this.width / 2 - 128, this.height / 2 - 72, 0, 0, 0, 256, 144, 256, 144);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!type) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                selectedCamera = (selectedCamera % maxCams) + 1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                selectedCamera = (selectedCamera - 2 + maxCams) % maxCams + 1;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int xOffset(int i) {
        return this.width / 2 - 130 + (i % 2) * 130;
    }

    private int yOffset(int i) {
        return this.height / 2 - 80 + (i / 2) * 80;
    }
}
