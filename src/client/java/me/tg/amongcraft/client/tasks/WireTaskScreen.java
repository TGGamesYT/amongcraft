package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Connect Wires minigame. Three colored nodes on the left, three on the right.
 * Click a left node then the same-colored right node to connect them.
 */
public class WireTaskScreen extends TaskMinigameScreen {

    private static final int[] PALETTE = {
            0xFFE03030, // red
            0xFF3060E0, // blue
            0xFFE0D020, // yellow
            0xFF30C040, // green
            0xFF9030D0, // purple
            0xFFE08020  // orange
    };

    private static final int NODE = 18;

    private int[] leftColors;
    private int[] rightColors;
    private boolean[] leftUsed;
    private boolean[] rightUsed;
    /** rightIndex connected to each leftIndex, or -1. */
    private int[] connected;

    private int selectedLeft = -1;
    private int connections = 0;

    public WireTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Connect Wires");
    }

    @Override
    protected void initTask() {
        Random rng = new Random();
        List<Integer> all = new ArrayList<>();
        for (int c : PALETTE) all.add(c);
        Collections.shuffle(all, rng);
        int[] chosen = { all.get(0), all.get(1), all.get(2) };

        List<Integer> lc = new ArrayList<>();
        List<Integer> rc = new ArrayList<>();
        for (int c : chosen) { lc.add(c); rc.add(c); }
        Collections.shuffle(lc, rng);
        Collections.shuffle(rc, rng);

        leftColors = new int[3];
        rightColors = new int[3];
        for (int i = 0; i < 3; i++) {
            leftColors[i] = lc.get(i);
            rightColors[i] = rc.get(i);
        }
        leftUsed = new boolean[3];
        rightUsed = new boolean[3];
        connected = new int[]{ -1, -1, -1 };
    }

    private int nodeX(boolean left) {
        return left ? panelX + 30 : panelX + panelWidth - 30 - NODE;
    }

    private int nodeY(int index) {
        int top = contentTop() + 26;
        int gap = (panelHeight - 22 - 52) / 2;
        return top + index * gap;
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // connected wires
        for (int i = 0; i < 3; i++) {
            if (connected[i] >= 0) {
                int x1 = nodeX(true) + NODE / 2;
                int y1 = nodeY(i) + NODE / 2;
                int x2 = nodeX(false) + NODE / 2;
                int y2 = nodeY(connected[i]) + NODE / 2;
                drawThickLine(context, x1, y1, x2, y2, leftColors[i], 5);
            }
        }

        // preview wire to mouse
        if (selectedLeft >= 0 && !isFinished()) {
            int x1 = nodeX(true) + NODE / 2;
            int y1 = nodeY(selectedLeft) + NODE / 2;
            drawThickLine(context, x1, y1, mouseX, mouseY, leftColors[selectedLeft], 5);
        }

        // nodes
        for (int i = 0; i < 3; i++) {
            drawNode(context, nodeX(true), nodeY(i), leftColors[i], leftUsed[i], selectedLeft == i);
            drawNode(context, nodeX(false), nodeY(i), rightColors[i], rightUsed[i], false);
        }
    }

    private void drawNode(DrawContext context, int x, int y, int color, boolean used, boolean selected) {
        int c = used ? dim(color) : color;
        context.fill(x, y, x + NODE, y + NODE, c);
        context.drawBorder(x - 1, y - 1, NODE + 2, NODE + 2,
                selected ? 0xFFFFFFFF : 0xFF101418);
    }

    private static int dim(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return 0xFF000000 | ((r / 3) << 16) | ((g / 3) << 8) | (b / 3);
    }

    private void drawThickLine(DrawContext context, int x1, int y1, int x2, int y2, int color, int thick) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) steps = 1;
        int half = thick / 2;
        for (int s = 0; s <= steps; s++) {
            int px = x1 + dx * s / steps;
            int py = y1 + dy * s / steps;
            context.fill(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }

    private int hitNode(double mx, double my, boolean left) {
        for (int i = 0; i < 3; i++) {
            int x = nodeX(left);
            int y = nodeY(i);
            if (mx >= x - 2 && mx <= x + NODE + 2 && my >= y - 2 && my <= y + NODE + 2) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int l = hitNode(mouseX, mouseY, true);
        if (l >= 0 && !leftUsed[l]) {
            selectedLeft = l;
            return true;
        }

        int r = hitNode(mouseX, mouseY, false);
        if (r >= 0 && !rightUsed[r] && selectedLeft >= 0) {
            if (leftColors[selectedLeft] == rightColors[r]) {
                connected[selectedLeft] = r;
                leftUsed[selectedLeft] = true;
                rightUsed[r] = true;
                connections++;
                if (connections == 3) {
                    completeTask();
                }
            }
            selectedLeft = -1;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
