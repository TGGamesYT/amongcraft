package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Start Reactor — a Simon-says memory game. The left 3x3 grid flashes a growing
 * sequence of cells; the player repeats it on the right 3x3 grid. The sequence
 * grows by one cell each round, 5 rounds total. A wrong click resets to round 1.
 */
public class StartReactorTaskScreen extends TaskMinigameScreen {

    private static final int TOTAL_ROUNDS = 5;

    // Phases.
    private static final int PHASE_PRESTART = 0; // brief pause before playback
    private static final int PHASE_PLAYBACK = 1;
    private static final int PHASE_INPUT = 2;
    private static final int PHASE_WRONG = 3; // showing the error flash

    // Playback timing (ticks).
    private static final int PRESTART_TICKS = 10;
    private static final int ON_TICKS = 10;  // cell lit
    private static final int OFF_TICKS = 4;  // gap between cells
    private static final int WRONG_TICKS = 14;

    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();

    private int round = 1;
    private int phase = PHASE_PRESTART;
    private int timer = PRESTART_TICKS;

    // Playback state.
    private int playIndex = 0;     // which sequence element
    private boolean playCellOn = false;

    // Input state.
    private int inputProgress = 0; // how many correct cells entered this round
    private int wrongCell = -1;
    private int correctFlashCell = -1;
    private int correctFlashTimer = 0;

    // Layout.
    private int cellSize, cellGap;
    private int dispX, inputX, gridsY;

    public StartReactorTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Start Reactor");
        this.panelWidth = 400;
        this.panelHeight = 270;
    }

    @Override
    protected void initTask() {
        cellSize = 44;
        cellGap = 6;
        int gridW = cellSize * 3 + cellGap * 2;
        int totalW = gridW * 2 + 40; // 40px gap between grids
        dispX = centerX() - totalW / 2;
        inputX = dispX + gridW + 40;
        gridsY = contentTop() + 34;

        startRound();
    }

    private void startRound() {
        sequence.add(random.nextInt(9));
        phase = PHASE_PRESTART;
        timer = PRESTART_TICKS;
        playIndex = 0;
        playCellOn = false;
        inputProgress = 0;
    }

    private void resetGame() {
        sequence.clear();
        round = 1;
        startRound();
    }

    @Override
    protected void tickTask() {
        if (correctFlashTimer > 0) {
            correctFlashTimer--;
            if (correctFlashTimer == 0) correctFlashCell = -1;
        }

        timer--;
        if (timer > 0) return;

        switch (phase) {
            case PHASE_PRESTART:
                phase = PHASE_PLAYBACK;
                playIndex = 0;
                playCellOn = true;
                timer = ON_TICKS;
                break;
            case PHASE_PLAYBACK:
                if (playCellOn) {
                    playCellOn = false;
                    timer = OFF_TICKS;
                } else {
                    playIndex++;
                    if (playIndex >= sequence.size()) {
                        phase = PHASE_INPUT;
                        inputProgress = 0;
                        timer = 1;
                    } else {
                        playCellOn = true;
                        timer = ON_TICKS;
                    }
                }
                break;
            case PHASE_WRONG:
                wrongCell = -1;
                resetGame();
                break;
            default:
                timer = 1; // keep ticking during input
                break;
        }
    }

    private int litPlaybackCell() {
        if (phase == PHASE_PLAYBACK && playCellOn && playIndex < sequence.size()) {
            return sequence.get(playIndex);
        }
        return -1;
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Status.
        String status;
        if (phase == PHASE_PLAYBACK || phase == PHASE_PRESTART) {
            status = "Watch the sequence  -  Round " + round + " / " + TOTAL_ROUNDS;
        } else if (phase == PHASE_WRONG) {
            status = "Wrong! Restarting...";
        } else {
            status = "Repeat the sequence  -  Round " + round + " / " + TOTAL_ROUNDS;
        }
        context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal(status),
                centerX(), contentTop() + 8, 0xFFFFFFFF);

        int litDisp = litPlaybackCell();
        drawGrid(context, dispX, gridsY, true, litDisp, mouseX, mouseY);
        drawGrid(context, inputX, gridsY, false, -1, mouseX, mouseY);

        // Labels.
        int gridW = cellSize * 3 + cellGap * 2;
        context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal("Display"),
                dispX + gridW / 2, gridsY + gridW + 6, 0xFFAACCEE);
        context.drawCenteredTextWithShadow(textRenderer, net.minecraft.text.Text.literal("Input"),
                inputX + gridW / 2, gridsY + gridW + 6, 0xFFAACCEE);
    }

    private void drawGrid(DrawContext context, int gx, int gy, boolean isDisplay,
                          int litCell, int mouseX, int mouseY) {
        boolean inputActive = !isDisplay && phase == PHASE_INPUT;
        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = gx + col * (cellSize + cellGap);
            int y = gy + row * (cellSize + cellGap);

            int color = 0xFF444444;
            if (isDisplay) {
                if (i == litCell) color = 0xFFFFFF33;
            } else {
                if (i == wrongCell && phase == PHASE_WRONG) {
                    color = 0xFFE03030;
                } else if (i == correctFlashCell) {
                    color = 0xFF32CD32;
                } else if (inputActive
                        && mouseX >= x && mouseX <= x + cellSize
                        && mouseY >= y && mouseY <= y + cellSize) {
                    color = 0xFF5A5A5A;
                }
            }
            context.fill(x, y, x + cellSize, y + cellSize, color);
            context.drawBorder(x, y, cellSize, cellSize, 0xFF101010);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || phase != PHASE_INPUT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = inputX + col * (cellSize + cellGap);
            int y = gridsY + row * (cellSize + cellGap);
            if (mouseX >= x && mouseX <= x + cellSize
                    && mouseY >= y && mouseY <= y + cellSize) {
                handleInput(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleInput(int cell) {
        int expected = sequence.get(inputProgress);
        if (cell != expected) {
            wrongCell = cell;
            phase = PHASE_WRONG;
            timer = WRONG_TICKS;
            return;
        }

        correctFlashCell = cell;
        correctFlashTimer = 6;
        inputProgress++;

        if (inputProgress >= sequence.size()) {
            if (round >= TOTAL_ROUNDS) {
                completeTask();
            } else {
                round++;
                startRound();
            }
        }
    }
}
