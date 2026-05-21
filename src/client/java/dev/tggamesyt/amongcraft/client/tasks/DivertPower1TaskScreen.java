package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Divert Power 1 — 8 vertical sliders. Only one (the highlighted green one) is
 * active; drag its handle to the top (100%) to win.
 */
public class DivertPower1TaskScreen extends TaskMinigameScreen {

    private static final int NUM_SLIDERS = 8;

    private final Random random = new Random();
    private int activeIndex;
    private final float[] values = new float[NUM_SLIDERS]; // 0..1
    private boolean dragging = false;

    // Layout
    private int trackTop, trackBottom, trackWidth;
    private final int[] trackX = new int[NUM_SLIDERS];
    private static final int HANDLE_HEIGHT = 14;

    public DivertPower1TaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Divert Power");
        this.panelWidth = 380;
        this.panelHeight = 280;
    }

    @Override
    protected void initTask() {
        activeIndex = random.nextInt(NUM_SLIDERS);
        for (int i = 0; i < NUM_SLIDERS; i++) {
            values[i] = 0.5f;
        }
        trackWidth = 20;
        trackTop = contentTop() + 24;
        trackBottom = panelY + panelHeight - 36;

        int gap = 18;
        int totalWidth = NUM_SLIDERS * trackWidth + (NUM_SLIDERS - 1) * gap;
        int startX = centerX() - totalWidth / 2;
        for (int i = 0; i < NUM_SLIDERS; i++) {
            trackX[i] = startX + i * (trackWidth + gap);
        }
    }

    private int handleY(int i) {
        // value 0 -> bottom, value 1 -> top
        int range = (trackBottom - HANDLE_HEIGHT) - trackTop;
        return trackBottom - HANDLE_HEIGHT - (int) (values[i] * range);
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal("Drag the green slider to the top"),
                centerX(), contentTop() + 4, 0xFFCCCCCC);

        for (int i = 0; i < NUM_SLIDERS; i++) {
            boolean active = i == activeIndex;
            int tx = trackX[i];

            // Track
            int trackColor = active ? 0xFF335533 : 0xFF333333;
            context.fill(tx, trackTop, tx + trackWidth, trackBottom, trackColor);
            context.drawBorder(tx, trackTop, trackWidth, trackBottom - trackTop,
                    active ? 0xFF55FF66 : 0xFF555555);

            // Filled portion below handle for active
            int hy = handleY(i);
            if (active) {
                context.fill(tx + 2, hy, tx + trackWidth - 2, trackBottom - 2, 0xFF1F7A33);
            }

            // Handle
            int handleColor = active ? 0xFF55FF66 : 0xFFAAAAAA;
            context.fill(tx + 1, hy, tx + trackWidth - 1, hy + HANDLE_HEIGHT, handleColor);
            context.drawBorder(tx + 1, hy, trackWidth - 2, HANDLE_HEIGHT, 0xFF111111);
        }
    }

    private boolean overActiveHandle(double mouseX, double mouseY) {
        int tx = trackX[activeIndex];
        int hy = handleY(activeIndex);
        return mouseX >= tx && mouseX <= tx + trackWidth
                && mouseY >= hy - 4 && mouseY <= hy + HANDLE_HEIGHT + 4;
    }

    private void updateActiveValue(double mouseY) {
        int range = (trackBottom - HANDLE_HEIGHT) - trackTop;
        double centerOfHandle = mouseY - HANDLE_HEIGHT / 2.0;
        float v = (float) ((trackBottom - HANDLE_HEIGHT - centerOfHandle) / range);
        values[activeIndex] = Math.max(0f, Math.min(1f, v));
        if (values[activeIndex] >= 0.999f) {
            completeTask();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && overActiveHandle(mouseX, mouseY)) {
            dragging = true;
            updateActiveValue(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isFinished()) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (dragging && button == 0) {
            updateActiveValue(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
