package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Inspect Sample — 5 sample tubes in a row, each with a "Select" button. The
 * player presses "Start", an ~8s countdown runs, then one random tube turns red
 * and the rest blue. Selecting the red tube completes the task; selecting a blue
 * tube just resets back to the pre-Start state.
 */
public class InspectSampleTaskScreen extends TaskMinigameScreen {

    private static final int TUBE_COUNT = 5;
    private static final int COUNTDOWN_TICKS = 160; // ~8 seconds

    // Phases.
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_COUNTDOWN = 1;
    private static final int PHASE_SELECT = 2;

    private final Random random = new Random();

    private int phase = PHASE_IDLE;
    private int countdown = 0;
    private int redIndex = -1;

    // Layout (computed in initTask).
    private int tubeW, tubeH, tubeGap, tubesY, firstTubeX;
    private int btnW, btnH, btnsY;
    private int startX, startY, startW, startH;

    public InspectSampleTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Inspect Sample");
        this.panelWidth = 380;
        this.panelHeight = 280;
    }

    @Override
    protected void initTask() {
        tubeW = 14;
        tubeH = 120;
        tubeGap = 20;
        btnW = 50;
        btnH = 18;

        int totalW = TUBE_COUNT * btnW + (TUBE_COUNT - 1) * tubeGap;
        firstTubeX = centerX() - totalW / 2;
        tubesY = contentTop() + 30;
        btnsY = tubesY + tubeH + 8;

        startW = 90;
        startH = 22;
        startX = centerX() - startW / 2;
        startY = btnsY + btnH + 12;
    }

    @Override
    protected void tickTask() {
        if (phase == PHASE_COUNTDOWN) {
            countdown--;
            if (countdown <= 0) {
                phase = PHASE_SELECT;
                redIndex = random.nextInt(TUBE_COUNT);
            }
        }
    }

    private int tubeCenterX(int i) {
        return firstTubeX + i * (btnW + tubeGap) + btnW / 2;
    }

    private int btnX(int i) {
        return firstTubeX + i * (btnW + tubeGap) + (btnW - btnW) / 2;
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Status text.
        String status;
        if (phase == PHASE_IDLE) {
            status = "Click \"Start\" to begin";
        } else if (phase == PHASE_COUNTDOWN) {
            float secs = countdown / 20f;
            status = String.format("ETA: %.2fs", secs);
        } else {
            status = "Select the contaminated sample";
        }
        context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal(status),
                centerX(), contentTop() + 6, 0xFFFFFFFF);

        // Tubes.
        for (int i = 0; i < TUBE_COUNT; i++) {
            int cx = tubeCenterX(i);
            int x1 = cx - tubeW / 2;
            int x2 = cx + tubeW / 2;
            int color;
            if (phase == PHASE_SELECT) {
                color = (i == redIndex) ? 0xFFE03030 : 0xFF3060E0;
            } else {
                color = 0xFFE8E8E8;
            }
            context.fill(x1, tubesY, x2, tubesY + tubeH, color);
            context.drawBorder(x1, tubesY, tubeW, tubeH, 0xFF101010);
        }

        // Select buttons.
        for (int i = 0; i < TUBE_COUNT; i++) {
            int bx = btnX(i);
            boolean hover = phase == PHASE_SELECT
                    && mouseX >= bx && mouseX <= bx + btnW
                    && mouseY >= btnsY && mouseY <= btnsY + btnH;
            int bg = phase == PHASE_SELECT ? (hover ? 0xFF777777 : 0xFF555555) : 0xFF3A3A3A;
            context.fill(bx, btnsY, bx + btnW, btnsY + btnH, bg);
            context.drawBorder(bx, btnsY, btnW, btnH, 0xFF202020);
            context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal("Select"),
                    bx + btnW / 2, btnsY + (btnH - 8) / 2, 0xFFFFFFFF);
        }

        // Start button.
        boolean startHover = phase == PHASE_IDLE
                && mouseX >= startX && mouseX <= startX + startW
                && mouseY >= startY && mouseY <= startY + startH;
        int startBg = phase == PHASE_IDLE ? (startHover ? 0xFF55DD55 : 0xFF32CD32) : 0xFF3A4A3A;
        context.fill(startX, startY, startX + startW, startY + startH, startBg);
        context.drawBorder(startX, startY, startW, startH, 0xFF1A2A1A);
        context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal("Start"),
                startX + startW / 2, startY + (startH - 8) / 2, 0xFF103010);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (phase == PHASE_IDLE) {
            if (mouseX >= startX && mouseX <= startX + startW
                    && mouseY >= startY && mouseY <= startY + startH) {
                phase = PHASE_COUNTDOWN;
                countdown = COUNTDOWN_TICKS;
                return true;
            }
        } else if (phase == PHASE_SELECT) {
            for (int i = 0; i < TUBE_COUNT; i++) {
                int bx = btnX(i);
                if (mouseX >= bx && mouseX <= bx + btnW
                        && mouseY >= btnsY && mouseY <= btnsY + btnH) {
                    if (i == redIndex) {
                        completeTask();
                    } else {
                        // Wrong tube — reset to pre-Start state.
                        phase = PHASE_IDLE;
                        redIndex = -1;
                        countdown = 0;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
