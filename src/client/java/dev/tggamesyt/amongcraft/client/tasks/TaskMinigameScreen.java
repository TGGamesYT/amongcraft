package dev.tggamesyt.amongcraft.client.tasks;

import dev.tggamesyt.amongcraft.client.AmongcraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Shared base for every native task minigame.
 *
 * <p>Subclasses implement {@link #initTask()}, {@link #renderTask} and
 * optionally {@link #tickTask()} and input handlers. They draw inside the
 * panel rectangle ({@code panelX/panelY/panelWidth/panelHeight}) — the frame
 * and title are drawn for them. When the player finishes the minigame the
 * subclass calls {@link #completeTask()}, which notifies the server and
 * closes the screen after a short "Task Complete" flash.</p>
 */
public abstract class TaskMinigameScreen extends Screen {

    protected final String taskId;
    protected final BlockPos taskPos;

    /** Panel size. Subclasses may change these in their constructor (before init). */
    protected int panelWidth = 380;
    protected int panelHeight = 280;
    protected int panelX;
    protected int panelY;

    private boolean finished = false;
    private int closeTimer = -1;

    protected TaskMinigameScreen(String taskId, BlockPos pos, String title) {
        super(Text.literal(title));
        this.taskId = taskId;
        this.taskPos = pos;
    }

    @Override
    protected final void init() {
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        initTask();
    }

    /** Set up state and widgets. {@code panelX/panelY} are valid here. */
    protected abstract void initTask();

    /** Draw the minigame. The panel frame and title are already drawn. */
    protected abstract void renderTask(DrawContext context, int mouseX, int mouseY, float delta);

    /** Optional per-tick logic (20/s). Not called once the task is finished. */
    protected void tickTask() {}

    /** Call once when the player completes the minigame. */
    protected final void completeTask() {
        if (finished) return;
        finished = true;
        AmongcraftClient.TaskDoneC2SPacket.send(taskId, taskPos);
        closeTimer = 36;
    }

    protected final boolean isFinished() {
        return finished;
    }

    /** Top of the usable content area, just below the title bar. */
    protected final int contentTop() {
        return panelY + 22;
    }

    protected final int centerX() {
        return panelX + panelWidth / 2;
    }

    protected final int centerY() {
        return panelY + 22 + (panelHeight - 22) / 2;
    }

    @Override
    public final void tick() {
        if (closeTimer > 0) {
            closeTimer--;
            if (closeTimer == 0 && client != null) {
                client.setScreen(null);
            }
        }
        if (!finished) {
            tickTask();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.fill(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0xFF12161C);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF1E2A38);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF2B3D52);
        context.drawCenteredTextWithShadow(textRenderer, title, panelX + panelWidth / 2, panelY + 6, 0xFFFFFFFF);

        renderTask(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        if (finished) {
            context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC0A1A0A);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("✔ Task Complete"),
                    panelX + panelWidth / 2, panelY + panelHeight / 2 - 4, 0xFF55FF66);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
