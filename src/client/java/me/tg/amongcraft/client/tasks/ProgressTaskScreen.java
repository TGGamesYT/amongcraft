package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/**
 * Download/Upload Data — an automatic progress bar that fills from 0% to 100%
 * over ~10 seconds, then completes the task.
 */
public class ProgressTaskScreen extends TaskMinigameScreen {

    private static final int DURATION_TICKS = 200; // ~10 seconds at 20 tps

    private static final String[] FAKE_TIMES = {
            "1 year", "6 months", "5 days", "12 hours",
            "1 hour", "30 minutes", "5 minutes", "1 minute",
            "30 seconds", "10 seconds", "5 seconds", "1 second"
    };

    private final boolean upload;
    private int elapsed = 0;

    public ProgressTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, taskId.equals("upload") ? "Upload Data" : "Download Data");
        this.upload = taskId.equals("upload");
        this.panelWidth = 360;
        this.panelHeight = 180;
    }

    @Override
    protected void initTask() {
    }

    @Override
    protected void tickTask() {
        if (elapsed < DURATION_TICKS) {
            elapsed++;
            if (elapsed >= DURATION_TICKS) {
                completeTask();
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        float progress = Math.min(1f, elapsed / (float) DURATION_TICKS);

        String prefix = upload ? "Uploading" : "Downloading";
        int idx = Math.min(FAKE_TIMES.length - 1, (int) (progress * FAKE_TIMES.length));
        String status = prefix + "... " + FAKE_TIMES[idx] + " remaining";

        int cx = centerX();
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal(status), cx, contentTop() + 26, 0xFFE0E0E0);

        // Progress bar geometry
        int barMargin = 30;
        int barX1 = panelX + barMargin;
        int barX2 = panelX + panelWidth - barMargin;
        int barY1 = contentTop() + 60;
        int barHeight = 20;
        int barY2 = barY1 + barHeight;

        // Track
        context.fill(barX1, barY1, barX2, barY2, 0xFF444444);
        context.drawBorder(barX1, barY1, barX2 - barX1, barHeight, 0xFF222222);

        // Fill
        int fillWidth = (int) ((barX2 - barX1) * progress);
        if (fillWidth > 0) {
            context.fill(barX1, barY1, barX1 + fillWidth, barY2, 0xFF0F9D58);
        }

        // Percentage
        String pct = (int) (progress * 100) + "%";
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal(pct), cx, barY2 + 12, 0xFFFFFFFF);
    }
}
