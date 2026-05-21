package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Clean Vent minigame. Six pieces of debris at random positions inside a vent;
 * click each one to remove it. Removing all six completes the task.
 */
public class CleanVentTaskScreen extends TaskMinigameScreen {

    private static final int COUNT = 6;
    private static final int[] COLORS = {
            0xFFE03030, 0xFFE0D020, 0xFF40E040,
            0xFF30D0E0, 0xFFE030E0, 0xFFFFFFFF
    };

    private int ventX, ventY, ventW, ventH;
    private float[] dx;
    private float[] dy;
    private int[] dsize;
    private int[] dcolor;
    private boolean[] dcircle;
    private boolean[] removed;
    private int remaining = COUNT;

    public CleanVentTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Clean Vent");
    }

    @Override
    protected void initTask() {
        ventX = panelX + 24;
        ventY = contentTop() + 10;
        ventW = panelWidth - 48;
        ventH = panelHeight - 22 - 28;

        Random rng = new Random();
        dx = new float[COUNT];
        dy = new float[COUNT];
        dsize = new int[COUNT];
        dcolor = new int[COUNT];
        dcircle = new boolean[COUNT];
        removed = new boolean[COUNT];
        for (int i = 0; i < COUNT; i++) {
            dsize[i] = 16 + rng.nextInt(8);
            dx[i] = ventX + 6 + rng.nextFloat() * (ventW - 12 - dsize[i]);
            dy[i] = ventY + 6 + rng.nextFloat() * (ventH - 12 - dsize[i]);
            dcolor[i] = COLORS[rng.nextInt(COLORS.length)];
            dcircle[i] = rng.nextBoolean();
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // vent background
        context.fill(ventX, ventY, ventX + ventW, ventY + ventH, 0xFF444444);
        context.drawBorder(ventX - 1, ventY - 1, ventW + 2, ventH + 2, 0xFF101418);

        // vent slats
        for (int sy = ventY + 8; sy < ventY + ventH - 4; sy += 12) {
            context.fill(ventX + 4, sy, ventX + ventW - 4, sy + 2, 0xFF3A3A3A);
        }

        // debris
        for (int i = 0; i < COUNT; i++) {
            if (removed[i]) continue;
            int x = (int) dx[i];
            int y = (int) dy[i];
            int s = dsize[i];
            if (dcircle[i]) {
                drawDisc(context, x + s / 2, y + s / 2, s / 2, dcolor[i]);
            } else {
                context.fill(x, y, x + s, y + s, dcolor[i]);
                context.drawBorder(x, y, s, s, 0xFF101418);
            }
        }
    }

    private void drawDisc(DrawContext context, int ccx, int ccy, int r, int color) {
        for (int yy = -r; yy <= r; yy++) {
            for (int xx = -r; xx <= r; xx++) {
                if (xx * xx + yy * yy <= r * r) {
                    context.fill(ccx + xx, ccy + yy, ccx + xx + 1, ccy + yy + 1, color);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int i = COUNT - 1; i >= 0; i--) {
            if (removed[i]) continue;
            int s = dsize[i];
            if (mouseX >= dx[i] && mouseX <= dx[i] + s
                    && mouseY >= dy[i] && mouseY <= dy[i] + s) {
                removed[i] = true;
                remaining--;
                if (remaining == 0) {
                    completeTask();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
