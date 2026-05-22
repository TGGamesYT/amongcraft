package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Align Engine Output minigame. A vertical slider on the right with a draggable
 * handle. A target "center zone" band sits in the middle of the track. While the
 * handle is held within the zone a 3-second meter fills; leaving the zone resets
 * it. Keeping the meter full for 3 continuous seconds wins.
 */
public class AlignEngineTaskScreen extends TaskMinigameScreen {

    /** Track geometry, computed in initTask(). */
    private int trackX;
    private int trackTop;
    private int trackBottom;
    private static final int TRACK_WIDTH = 16;
    private static final int HANDLE_HEIGHT = 22;

    /** Handle position as 0..1 along the track (0 = top, 1 = bottom). */
    private double handlePos = 0.5;
    private boolean dragging = false;

    /** Center zone as fraction of track length. */
    private static final double ZONE_HALF = 0.07;

    /** Ticks (20/s) the handle has continuously been in the zone. 60 = win. */
    private int inZoneTicks = 0;
    private static final int WIN_TICKS = 60;

    public AlignEngineTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Align Engine Output");
    }

    @Override
    protected void initTask() {
        trackX = panelX + panelWidth - 50;
        trackTop = contentTop() + 24;
        trackBottom = panelY + panelHeight - 30;
        handlePos = new Random().nextDouble();
    }

    private int trackLength() {
        return trackBottom - trackTop;
    }

    private int handleCenterY() {
        return trackTop + (int) Math.round(handlePos * trackLength());
    }

    private boolean inZone() {
        return Math.abs(handlePos - 0.5) <= ZONE_HALF;
    }

    @Override
    protected void tickTask() {
        if (inZone()) {
            inZoneTicks++;
            if (inZoneTicks >= WIN_TICKS) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f));
                completeTask();
            }
        } else {
            inZoneTicks = 0;
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean aligned = inZone();

        // Instruction text on the left.
        int textX = panelX + 16;
        context.drawText(textRenderer, Text.literal("Drag the handle into"),
                textX, contentTop() + 8, 0xFFCfd8e0, false);
        context.drawText(textRenderer, Text.literal("the center zone and"),
                textX, contentTop() + 20, 0xFFCfd8e0, false);
        context.drawText(textRenderer, Text.literal("hold it there."),
                textX, contentTop() + 32, 0xFFCfd8e0, false);

        // Alignment indicator dot + label.
        int dotX = textX;
        int dotY = contentTop() + 56;
        int indColor = aligned ? 0xFF35E04A : 0xFFE03030;
        context.fill(dotX, dotY, dotX + 12, dotY + 12, indColor);
        context.drawBorder(dotX - 1, dotY - 1, 14, 14, 0xFF101418);
        context.drawText(textRenderer, Text.literal(aligned ? "ALIGNED" : "MISALIGNED"),
                dotX + 20, dotY + 2, indColor, false);

        // Progress meter (horizontal bar below the indicator).
        int meterX = textX;
        int meterY = dotY + 26;
        int meterW = 140;
        int meterH = 16;
        context.fill(meterX, meterY, meterX + meterW, meterY + meterH, 0xFF0C1014);
        int fillW = (int) Math.round((double) inZoneTicks / WIN_TICKS * (meterW - 4));
        if (fillW > 0) {
            context.fill(meterX + 2, meterY + 2, meterX + 2 + fillW, meterY + meterH - 2, 0xFF35E04A);
        }
        context.drawBorder(meterX - 1, meterY - 1, meterW + 2, meterH + 2, 0xFF101418);
        int secs = (WIN_TICKS - inZoneTicks + 19) / 20;
        context.drawText(textRenderer, Text.literal("Hold: " + Math.max(0, secs) + "s"),
                meterX, meterY + meterH + 6, 0xFFCfd8e0, false);

        // Track background.
        context.fill(trackX, trackTop, trackX + TRACK_WIDTH, trackBottom, 0xFF0C1014);
        context.drawBorder(trackX - 1, trackTop - 1, TRACK_WIDTH + 2, trackLength() + 2, 0xFF101418);

        // Center zone band.
        int zoneTop = trackTop + (int) Math.round((0.5 - ZONE_HALF) * trackLength());
        int zoneBot = trackTop + (int) Math.round((0.5 + ZONE_HALF) * trackLength());
        int zoneColor = aligned ? 0x6035E04A : 0x60E0D020;
        context.fill(trackX, zoneTop, trackX + TRACK_WIDTH, zoneBot, zoneColor);
        context.drawHorizontalLine(trackX, trackX + TRACK_WIDTH - 1, zoneTop, 0xFFE0D020);
        context.drawHorizontalLine(trackX, trackX + TRACK_WIDTH - 1, zoneBot, 0xFFE0D020);

        // Handle.
        int hcy = handleCenterY();
        int hTop = hcy - HANDLE_HEIGHT / 2;
        int hColor = aligned ? 0xFF35E04A : 0xFFB0B8C0;
        context.fill(trackX - 4, hTop, trackX + TRACK_WIDTH + 4, hTop + HANDLE_HEIGHT, hColor);
        context.drawBorder(trackX - 5, hTop - 1, TRACK_WIDTH + 10, HANDLE_HEIGHT + 2, 0xFF101418);
        context.drawHorizontalLine(trackX - 2, trackX + TRACK_WIDTH + 1, hcy, 0xFF101418);
    }

    private boolean overHandle(double mx, double my) {
        int hcy = handleCenterY();
        return mx >= trackX - 6 && mx <= trackX + TRACK_WIDTH + 6
                && my >= hcy - HANDLE_HEIGHT / 2 - 2 && my <= hcy + HANDLE_HEIGHT / 2 + 2;
    }

    private void setFromMouse(double my) {
        double p = (my - trackTop) / (double) trackLength();
        handlePos = Math.max(0.0, Math.min(1.0, p));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (overHandle(mouseX, mouseY)) {
            dragging = true;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            return true;
        }
        // Clicking on the track jumps the handle there.
        if (mouseX >= trackX - 6 && mouseX <= trackX + TRACK_WIDTH + 6
                && mouseY >= trackTop && mouseY <= trackBottom) {
            setFromMouse(mouseY);
            dragging = true;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isFinished()) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (dragging) {
            setFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
