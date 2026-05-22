package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Submit Scan minigame. No mouse interaction: a green scan line sweeps up and
 * down over the player's own 3D model while scan info lines reveal progressively
 * over ~12 seconds. When the scan finishes the task completes.
 */
public class ScanTaskScreen extends TaskMinigameScreen {

    /** Total scan duration: 12 seconds at 20 ticks/s. */
    private static final int SCAN_TICKS = 240;
    private int ticks = 0;
    private boolean playedDone = false;

    // Scan window geometry.
    private int scanX, scanY, scanW, scanH;

    // Randomized scan readouts.
    private int healthPct;
    private int oxygenPct;
    private int favColor;

    public ScanTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Submit Scan");
        this.panelWidth = 400;
        this.panelHeight = 300;
    }

    @Override
    protected void initTask() {
        scanW = 140;
        scanH = panelHeight - 22 - 36;
        scanX = panelX + 24;
        scanY = contentTop() + 14;

        Random rng = new Random();
        healthPct = 80 + rng.nextInt(21);
        oxygenPct = 90 + rng.nextInt(11);
        favColor = 0xFF000000
                | ((30 + rng.nextInt(200)) << 16)
                | ((30 + rng.nextInt(200)) << 8)
                | (30 + rng.nextInt(200));

        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    @Override
    protected void tickTask() {
        ticks++;
        if (ticks >= SCAN_TICKS) {
            if (client != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f));
            }
            completeTask();
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Scan window background.
        context.fill(scanX - 3, scanY - 3, scanX + scanW + 3, scanY + scanH + 3, 0xFF00C070);
        context.fill(scanX, scanY, scanX + scanW, scanY + scanH, 0xFF050807);

        drawPlayerModel(context);

        // Sweeping scan line (up/down ping-pong) drawn over the model.
        double phase = (ticks % 80) / 80.0; // 4-second sweep cycle
        double tri = phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0;
        int lineY = scanY + 2 + (int) Math.round(tri * (scanH - 6));
        context.fill(scanX, lineY, scanX + scanW, lineY + 3, 0xCC00FF58);
        // Faint glow above/below the line.
        context.fill(scanX, lineY - 4, scanX + scanW, lineY, 0x3300FF58);
        context.fill(scanX, lineY + 3, scanX + scanW, lineY + 7, 0x3300FF58);

        context.drawBorder(scanX - 1, scanY - 1, scanW + 2, scanH + 2, 0xFF101418);

        // Info panel on the right.
        int infoX = scanX + scanW + 22;
        int infoY = scanY + 6;
        int lineGap = 22;

        context.drawText(textRenderer, Text.literal("BIOSCAN REPORT"),
                infoX, infoY, 0xFF00FF58, false);

        boolean showName = ticks >= 60;     // 3s
        boolean showHealth = ticks >= 110;  // 5.5s
        boolean showOxygen = ticks >= 160;  // 8s
        boolean showColor = ticks >= 200;   // 10s

        String subjectName = "Crewmate";
        if (client != null && client.player != null) {
            subjectName = client.player.getGameProfile().getName();
        }
        drawInfoLine(context, infoX, infoY + lineGap, "Subject:", subjectName, showName);
        drawInfoLine(context, infoX, infoY + lineGap * 2, "Health:", healthPct + "%", showHealth);
        drawInfoLine(context, infoX, infoY + lineGap * 3, "Oxygen:", oxygenPct + "%", showOxygen);

        // Fav colour line with a colour swatch.
        int colorY = infoY + lineGap * 4;
        if (showColor) {
            context.drawText(textRenderer, Text.literal("Fav Color:"),
                    infoX, colorY, 0xFFCfd8e0, false);
            int swX = infoX + textRenderer.getWidth("Fav Color: ") + 4;
            context.fill(swX, colorY - 1, swX + 16, colorY + 9, favColor);
            context.drawBorder(swX - 1, colorY - 2, 18, 12, 0xFFFFFFFF);
        } else {
            context.drawText(textRenderer, Text.literal("Fav Color: ..."),
                    infoX, colorY, 0xFF55606A, false);
        }

        // Status line.
        boolean done = ticks >= SCAN_TICKS - 20;
        int statusY = infoY + lineGap * 5 + 6;
        String status = done ? "Scan complete" : "Scanning...";
        int statusColor = done ? 0xFF35E04A : 0xFFE0D020;
        context.drawText(textRenderer, Text.literal("Status: " + status),
                infoX, statusY, statusColor, false);

        // Progress bar under the info panel.
        int barX = infoX;
        int barY = statusY + 18;
        int barW = panelX + panelWidth - 24 - infoX;
        int barH = 12;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF0C1014);
        int prog = (int) Math.round((double) Math.min(ticks, SCAN_TICKS) / SCAN_TICKS * (barW - 4));
        if (prog > 0) {
            context.fill(barX + 2, barY + 2, barX + 2 + prog, barY + barH - 2, 0xFF00FF58);
        }
        context.drawBorder(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF101418);
    }

    private void drawInfoLine(DrawContext context, int x, int y, String label, String value, boolean show) {
        if (show) {
            context.drawText(textRenderer, Text.literal(label + " " + value),
                    x, y, 0xFFCfd8e0, false);
        } else {
            context.drawText(textRenderer, Text.literal(label + " ..."),
                    x, y, 0xFF55606A, false);
        }
    }

    /**
     * Renders the local player's actual 3D model inside the scan area, facing
     * the screen. The player's rotation fields are temporarily overridden so the
     * model faces forward, then restored afterwards.
     */
    private void drawPlayerModel(DrawContext context) {
        if (client == null || client.player == null) {
            return;
        }
        ClientPlayerEntity entity = client.player;

        // Save the rotation state so the live entity is left untouched.
        float savedBodyYaw = entity.bodyYaw;
        float savedPrevBodyYaw = entity.prevBodyYaw;
        float savedHeadYaw = entity.headYaw;
        float savedPrevHeadYaw = entity.prevHeadYaw;
        float savedYaw = entity.getYaw();
        float savedPrevYaw = entity.prevYaw;
        float savedPitch = entity.getPitch();
        float savedPrevPitch = entity.prevPitch;

        // Face the model straight at the screen.
        entity.bodyYaw = 180f;
        entity.prevBodyYaw = 180f;
        entity.headYaw = 180f;
        entity.prevHeadYaw = 180f;
        entity.setYaw(180f);
        entity.prevYaw = 180f;
        entity.setPitch(0f);
        entity.prevPitch = 0f;

        int modelX = scanX + scanW / 2;
        int modelY = scanY + scanH / 2 + scanH / 4;
        int size = Math.max(30, scanH / 4);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(modelX, modelY, 1050.0);
        matrices.scale(size, -size, size);

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);
        dispatcher.render(entity, 0, 0, 0, 0f, 1.0f, matrices,
                context.getVertexConsumers(), 15728880);
        dispatcher.setRenderShadows(true);

        matrices.pop();
        context.draw();

        // Restore the entity's rotation state.
        entity.bodyYaw = savedBodyYaw;
        entity.prevBodyYaw = savedPrevBodyYaw;
        entity.headYaw = savedHeadYaw;
        entity.prevHeadYaw = savedPrevHeadYaw;
        entity.setYaw(savedYaw);
        entity.prevYaw = savedPrevYaw;
        entity.setPitch(savedPitch);
        entity.prevPitch = savedPrevPitch;
    }
}
