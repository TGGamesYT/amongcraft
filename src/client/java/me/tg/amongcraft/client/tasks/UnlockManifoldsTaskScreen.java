package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unlock Manifolds — 10 buttons in a 5x2 grid showing shuffled numbers 1-10.
 * Click them in ascending order. A wrong click reshuffles. Click all 10 in
 * order to win.
 */
public class UnlockManifoldsTaskScreen extends TaskMinigameScreen {

    private static final int COLS = 5;
    private static final int ROWS = 2;
    private static final int CELL = 56;
    private static final int GAP = 12;

    private final int[] numbers = new int[COLS * ROWS]; // number shown in each cell
    private int nextNumber = 1;

    private int gridX, gridY;

    public UnlockManifoldsTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Unlock Manifolds");
        this.panelWidth = 380;
        this.panelHeight = 220;
    }

    @Override
    protected void initTask() {
        int gridW = COLS * CELL + (COLS - 1) * GAP;
        int gridH = ROWS * CELL + (ROWS - 1) * GAP;
        gridX = centerX() - gridW / 2;
        gridY = contentTop() + 22 + ((panelY + panelHeight - (contentTop() + 22)) - gridH) / 2;
        shuffle();
    }

    private void shuffle() {
        List<Integer> nums = new ArrayList<>();
        for (int i = 1; i <= COLS * ROWS; i++) {
            nums.add(i);
        }
        Collections.shuffle(nums);
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = nums.get(i);
        }
    }

    private int cellX(int idx) {
        return gridX + (idx % COLS) * (CELL + GAP);
    }

    private int cellY(int idx) {
        return gridY + (idx / COLS) * (CELL + GAP);
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal("Click the boxes in order 1 to 10"),
                centerX(), contentTop() + 4, 0xFFCCE6FF);

        for (int i = 0; i < numbers.length; i++) {
            int x = cellX(i);
            int y = cellY(i);
            boolean correct = numbers[i] < nextNumber;
            boolean hovered = !correct && !isFinished()
                    && mouseX >= x && mouseX <= x + CELL
                    && mouseY >= y && mouseY <= y + CELL;

            int bg;
            if (correct) {
                bg = 0xFF00CC66;
            } else if (hovered) {
                bg = 0xFF007ACC;
            } else {
                bg = 0xFF005A99;
            }
            context.fill(x, y, x + CELL, y + CELL, bg);
            context.drawBorder(x, y, CELL, CELL, correct ? 0xFF00FF88 : 0xFF003355);

            int textColor = correct ? 0xFFFFFFFF : 0xFFCCE6FF;
            String label = String.valueOf(numbers[i]);
            int tw = textRenderer.getWidth(label);
            context.drawText(textRenderer, label,
                    x + (CELL - tw) / 2, y + (CELL - 8) / 2, textColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            for (int i = 0; i < numbers.length; i++) {
                int x = cellX(i);
                int y = cellY(i);
                if (mouseX >= x && mouseX <= x + CELL && mouseY >= y && mouseY <= y + CELL) {
                    if (numbers[i] < nextNumber) {
                        return true; // already correct, ignore
                    }
                    if (numbers[i] == nextNumber) {
                        nextNumber++;
                        if (nextNumber > COLS * ROWS) {
                            completeTask();
                        }
                    } else {
                        // wrong: reset
                        nextNumber = 1;
                        shuffle();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
