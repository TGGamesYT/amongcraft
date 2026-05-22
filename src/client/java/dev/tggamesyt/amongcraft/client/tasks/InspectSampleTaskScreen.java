package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Inspect Sample — 5 sample tubes in a row, each with a "Select" button. The
 * player presses "Start", an ~8s countdown runs, then one random tube turns red
 * and the rest blue. Selecting the red tube completes the task; selecting a blue
 * tube just resets back to the pre-Start state.
 *
 * <p>The countdown is persisted in a {@code static} map keyed by the task
 * {@link BlockPos}: closing the screen (ESC) mid-countdown and reopening the
 * same block resumes the countdown from where it left off rather than
 * restarting. State for a block is cleared once that task is completed.</p>
 */
public class InspectSampleTaskScreen extends TaskMinigameScreen {

    private static final int TUBE_COUNT = 5;
    private static final int COUNTDOWN_TICKS = 160; // ~8 seconds

    // Phases.
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_COUNTDOWN = 1;
    private static final int PHASE_SELECT = 2;

    /** Persisted per-block countdown state, so reopening resumes it. */
    private static final class Progress {
        int phase = PHASE_IDLE;
        /** Remaining countdown ticks (only meaningful while PHASE_COUNTDOWN). */
        int countdown = 0;
        int redIndex = -1;
    }

    private static final Map<BlockPos, Progress> SAVED = new HashMap<>();

    private final Random random = new Random();

    /** Live view onto the persisted state for this task's block. */
    private Progress progress;

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

        // Resume persisted progress for this block, or start fresh.
        progress = SAVED.computeIfAbsent(taskPos.toImmutable(), p -> new Progress());
    }

    private void playSound(net.minecraft.sound.SoundEvent event, float pitch) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(event, pitch));
        }
    }

    @Override
    protected void tickTask() {
        if (progress.phase == PHASE_COUNTDOWN) {
            // 20 ticks/sec — one decrement per tick keeps the ETA accurate.
            progress.countdown--;
            if (progress.countdown <= 0) {
                progress.countdown = 0;
                progress.phase = PHASE_SELECT;
                progress.redIndex = random.nextInt(TUBE_COUNT);
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            }
        }
    }

    private int tubeCenterX(int i) {
        return firstTubeX + i * (btnW + tubeGap) + btnW / 2;
    }

    private int btnX(int i) {
        return firstTubeX + i * (btnW + tubeGap);
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        int phase = progress.phase;

        // Status text.
        String status;
        if (phase == PHASE_IDLE) {
            status = "Click \"Start\" to begin";
        } else if (phase == PHASE_COUNTDOWN) {
            // Round up so the label hits 0 exactly when the countdown ends.
            float secs = Math.max(0, progress.countdown) / 20f;
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
                color = (i == progress.redIndex) ? 0xFFE03030 : 0xFF3060E0;
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

        if (progress.phase == PHASE_IDLE) {
            if (mouseX >= startX && mouseX <= startX + startW
                    && mouseY >= startY && mouseY <= startY + startH) {
                progress.phase = PHASE_COUNTDOWN;
                progress.countdown = COUNTDOWN_TICKS;
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
                return true;
            }
        } else if (progress.phase == PHASE_SELECT) {
            for (int i = 0; i < TUBE_COUNT; i++) {
                int bx = btnX(i);
                if (mouseX >= bx && mouseX <= bx + btnW
                        && mouseY >= btnsY && mouseY <= btnsY + btnH) {
                    if (i == progress.redIndex) {
                        SAVED.remove(taskPos.toImmutable());
                        playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f);
                        completeTask();
                    } else {
                        // Wrong tube — reset to pre-Start state.
                        progress.phase = PHASE_IDLE;
                        progress.redIndex = -1;
                        progress.countdown = 0;
                        playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
