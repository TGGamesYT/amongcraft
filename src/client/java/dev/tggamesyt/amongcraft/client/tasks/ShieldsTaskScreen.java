package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Prime Shields minigame. Seven hexagon cells in three rows (2, 3, 2). Each
 * starts red or blue; clicking toggles its color. All blue completes the task.
 */
public class ShieldsTaskScreen extends TaskMinigameScreen {

    private static final int RED = 0xFFE03030;
    private static final int BLUE = 0xFF00AAFF;
    private static final int HEX_W = 56;
    private static final int HEX_H = 48;

    private final int[] rowCounts = { 2, 3, 2 };

    private int[] hexX;
    private int[] hexY;
    private boolean[] isBlue;

    public ShieldsTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Prime Shields");
    }

    @Override
    protected void initTask() {
        hexX = new int[7];
        hexY = new int[7];
        isBlue = new boolean[7];
        Random rng = new Random();

        int gap = 8;
        int totalH = rowCounts.length * HEX_H + (rowCounts.length - 1) * gap;
        int startY = contentTop() + ((panelHeight - 22) - totalH) / 2;

        int idx = 0;
        for (int r = 0; r < rowCounts.length; r++) {
            int count = rowCounts[r];
            int rowW = count * HEX_W + (count - 1) * gap;
            int startX = panelX + (panelWidth - rowW) / 2;
            int y = startY + r * (HEX_H + gap);
            for (int c = 0; c < count; c++) {
                hexX[idx] = startX + c * (HEX_W + gap);
                hexY[idx] = y;
                isBlue[idx] = rng.nextBoolean();
                idx++;
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < 7; i++) {
            drawHexagon(context, hexX[i], hexY[i], isBlue[i] ? BLUE : RED);
        }
    }

    /**
     * Draws a filled hexagon (pointy top/bottom) by scanning rows and filling a
     * horizontal span whose width follows the hex outline.
     */
    private void drawHexagon(DrawContext context, int x, int y, int color) {
        int w = HEX_W;
        int h = HEX_H;
        int quarter = h / 4;
        for (int row = 0; row < h; row++) {
            int half;
            if (row < quarter) {
                // top point widening out
                half = (int) ((w / 2.0) * (row + 1) / quarter);
            } else if (row >= h - quarter) {
                // bottom point narrowing in
                int rr = h - row;
                half = (int) ((w / 2.0) * rr / quarter);
            } else {
                half = w / 2;
            }
            int cx = x + w / 2;
            context.fill(cx - half, y + row, cx + half, y + row + 1, color);
        }
        // subtle border highlight at edges
        context.fill(x + w / 2 - 1, y, x + w / 2 + 1, y + 2, 0x44FFFFFF);
    }

    private boolean hitHex(double mx, double my, int i) {
        // approximate hit test with the hex bounding box
        return mx >= hexX[i] && mx <= hexX[i] + HEX_W
                && my >= hexY[i] && my <= hexY[i] + HEX_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int i = 0; i < 7; i++) {
            if (hitHex(mouseX, mouseY, i)) {
                isBlue[i] = !isBlue[i];
                checkDone();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void checkDone() {
        for (boolean b : isBlue) {
            if (!b) return;
        }
        completeTask();
    }
}
