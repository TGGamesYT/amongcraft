package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * Divert Power 2 — left wires are yellow, right wires gray. Click the toggle
 * switch once: the knob slides over, the right wires turn yellow and the task
 * completes.
 */
public class DivertPower2TaskScreen extends TaskMinigameScreen {

    private static final int WIRE_COUNT = 5;
    private static final int WIRE_WIDTH = 50;
    private static final int WIRE_HEIGHT = 8;
    private static final int WIRE_GAP = 10;

    private static final int SWITCH_WIDTH = 60;
    private static final int SWITCH_HEIGHT = 32;
    private static final int KNOB_SIZE = 28;

    private boolean switched = false;
    private float knobProgress = 0f; // 0 = left, 1 = right

    private int leftWireX, rightWireX, wiresTopY;
    private int switchX, switchY;

    public DivertPower2TaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Divert Power");
        this.panelWidth = 360;
        this.panelHeight = 220;
    }

    @Override
    protected void initTask() {
        int totalWiresHeight = WIRE_COUNT * WIRE_HEIGHT + (WIRE_COUNT - 1) * WIRE_GAP;
        wiresTopY = centerY() - totalWiresHeight / 2;

        int cx = centerX();
        switchX = cx - SWITCH_WIDTH / 2;
        switchY = centerY() - SWITCH_HEIGHT / 2;

        leftWireX = panelX + 40;
        rightWireX = panelX + panelWidth - 40 - WIRE_WIDTH;
    }

    @Override
    protected void tickTask() {
        if (switched && knobProgress < 1f) {
            // Slide the knob over a few ticks.
            knobProgress = Math.min(1f, knobProgress + 0.2f);
            if (knobProgress >= 1f) {
                // Knob finished sliding: complete the task.
                if (client != null) {
                    client.getSoundManager().play(PositionedSoundInstance.master(
                            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f));
                }
                completeTask();
            }
        }
    }

    private void drawWires(DrawContext context, int x, boolean yellow) {
        for (int i = 0; i < WIRE_COUNT; i++) {
            int y = wiresTopY + i * (WIRE_HEIGHT + WIRE_GAP);
            int color = yellow ? 0xFFFFEE33 : 0xFF888888;
            context.fill(x, y, x + WIRE_WIDTH, y + WIRE_HEIGHT, color);
        }
    }

    private void drawCircle(DrawContext context, float cx, float cy, float r, int color) {
        int minX = (int) Math.floor(cx - r);
        int maxX = (int) Math.ceil(cx + r);
        int minY = (int) Math.floor(cy - r);
        int maxY = (int) Math.ceil(cy + r);
        float r2 = r * r;
        for (int px = minX; px < maxX; px++) {
            for (int py = minY; py < maxY; py++) {
                float dx = px + 0.5f - cx;
                float dy = py + 0.5f - cy;
                if (dx * dx + dy * dy <= r2) {
                    context.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal("Flip the switch"),
                centerX(), contentTop() + 4, 0xFFCCCCCC);

        // Left wires always yellow
        drawWires(context, leftWireX, true);
        // Right wires yellow once switched
        drawWires(context, rightWireX, switched);

        // Switch track (rounded rectangle)
        int trackColor = switched ? 0xFF1F7A33 : 0xFF444444;
        context.fill(switchX + 4, switchY, switchX + SWITCH_WIDTH - 4, switchY + SWITCH_HEIGHT, trackColor);
        // rounded ends
        drawCircle(context, switchX + SWITCH_HEIGHT / 2f, switchY + SWITCH_HEIGHT / 2f,
                SWITCH_HEIGHT / 2f, trackColor);
        drawCircle(context, switchX + SWITCH_WIDTH - SWITCH_HEIGHT / 2f, switchY + SWITCH_HEIGHT / 2f,
                SWITCH_HEIGHT / 2f, trackColor);

        // Knob
        int travel = SWITCH_WIDTH - KNOB_SIZE - 4;
        float knobX = switchX + 2 + KNOB_SIZE / 2f + knobProgress * travel;
        float knobY = switchY + SWITCH_HEIGHT / 2f;
        drawCircle(context, knobX, knobY, KNOB_SIZE / 2f, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && !switched
                && mouseX >= switchX && mouseX <= switchX + SWITCH_WIDTH
                && mouseY >= switchY && mouseY <= switchY + SWITCH_HEIGHT) {
            switched = true;
            // Knob now animates in tickTask; task completes when it finishes sliding.
            if (client != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
