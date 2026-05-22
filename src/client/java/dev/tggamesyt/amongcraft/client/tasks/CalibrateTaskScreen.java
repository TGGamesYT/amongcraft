package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Calibrate Distributor minigame. Three stacked circles each with a spinning
 * marker; click the active circle when its marker aligns with the right-side
 * target. Wrong click resets to the first circle.
 */
public class CalibrateTaskScreen extends TaskMinigameScreen {

    private static final int[] COLORS = { 0xFFFF4444, 0xFF4488FF, 0xFF44CC44 };
    private static final double TOLERANCE = Math.toRadians(14);
    private static final double SPIN_PER_TICK = Math.toRadians(9);

    private int radius;
    private int[] cx = new int[3];
    private int[] cy = new int[3];

    /** marker angle for each circle, radians. */
    private final double[] angle = new double[3];
    private int current = 0;
    private final boolean[] done = new boolean[3];

    public CalibrateTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Calibrate Distributor");
    }

    @Override
    protected void initTask() {
        Random rng = new Random();
        radius = 28;
        int areaTop = contentTop() + 8;
        int areaBottom = panelY + panelHeight - 12;
        int span = areaBottom - areaTop;
        for (int i = 0; i < 3; i++) {
            cx[i] = panelX + panelWidth / 2 - 14;
            cy[i] = areaTop + radius + (span - 2 * radius) * i / 2;
            angle[i] = rng.nextDouble() * Math.PI * 2;
        }
    }

    @Override
    protected void tickTask() {
        if (current < 3) {
            angle[current] = (angle[current] + SPIN_PER_TICK) % (Math.PI * 2);
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < 3; i++) {
            int color = done[i] ? 0xFF44CC44 : COLORS[i];
            boolean active = (i == current) && !isFinished();
            drawRing(context, cx[i], cy[i], radius, color);

            // target marker fixed at angle 0 (right side)
            int tx = cx[i] + radius;
            int ty = cy[i];
            context.fill(tx + 3, ty - 5, tx + 13, ty + 5, color);
            context.drawBorder(tx + 3, ty - 5, 10, 10, 0xFF101418);

            // spinning marker on perimeter
            if (!done[i]) {
                double a = angle[i];
                int mx = cx[i] + (int) Math.round(Math.cos(a) * radius);
                int my = cy[i] + (int) Math.round(Math.sin(a) * radius);
                int mColor = active ? 0xFFFFFFFF : 0xFF888888;
                context.fill(mx - 4, my - 4, mx + 5, my + 5, mColor);
            }
        }
    }

    private void drawRing(DrawContext context, int ccx, int ccy, int r, int color) {
        int inner = r - 4;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 <= r * r && d2 >= inner * inner) {
                    context.fill(ccx + dx, ccy + dy, ccx + dx + 1, ccy + dy + 1, color);
                }
            }
        }
    }

    private boolean aligned(double a) {
        double d = a % (Math.PI * 2);
        if (d < 0) d += Math.PI * 2;
        if (d > Math.PI) d = Math.PI * 2 - d;
        return d <= TOLERANCE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0 || current >= 3) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // any click inside the active circle's area counts
        int dx = (int) mouseX - cx[current];
        int dy = (int) mouseY - cy[current];
        boolean onCircle = dx * dx + dy * dy <= (radius + 16) * (radius + 16);
        if (!onCircle) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (aligned(angle[current])) {
            done[current] = true;
            current++;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            if (current >= 3) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f));
                completeTask();
            }
        } else {
            // wrong: reset
            for (int i = 0; i < 3; i++) done[i] = false;
            current = 0;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f));
        }
        return true;
    }
}
