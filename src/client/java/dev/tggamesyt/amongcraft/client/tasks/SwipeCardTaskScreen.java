package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Swipe Card minigame. A horizontal scanner slot with a draggable card. The
 * player drags the card left-to-right; on release the swipe is judged valid if
 * it travelled at least ~150px at a reasonable average speed. Valid swipes win;
 * invalid ones flash the status light red and reset the card.
 */
public class SwipeCardTaskScreen extends TaskMinigameScreen {

    // Slot geometry.
    private int slotX, slotY, slotW, slotH;
    // Card geometry.
    private int cardW, cardH;

    /** Card left edge offset from slot left, in pixels. */
    private double cardOffset = 0.0;
    private boolean dragging = false;
    private double grabDX = 0.0;

    /** Swipe tracking. */
    private long swipeStartMs = 0;
    private double swipeStartOffset = 0.0;

    /** Status light: 0 = gray, 1 = green, 2 = red. */
    private int lightState = 0;
    private int lightTimer = 0;
    private String message = "Swipe the card";

    private static final double MIN_DISTANCE = 150.0;
    private static final double MIN_SPEED = 300.0;
    private static final double MAX_SPEED = 900.0;

    public SwipeCardTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Swipe Card");
    }

    @Override
    protected void initTask() {
        slotW = 280;
        slotH = 64;
        slotX = centerX() - slotW / 2;
        slotY = contentTop() + 36;

        cardW = 90;
        cardH = 48;
        cardOffset = 0.0;
    }

    private double maxOffset() {
        return slotW - cardW;
    }

    private int cardLeft() {
        return slotX + (int) Math.round(cardOffset);
    }

    private int cardTop() {
        return slotY + (slotH - cardH) / 2;
    }

    @Override
    protected void tickTask() {
        if (lightTimer > 0) {
            lightTimer--;
            if (lightTimer == 0 && lightState != 1) {
                lightState = 0;
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawText(textRenderer, Text.literal("Swipe the card left to right at a steady speed."),
                panelX + 14, contentTop() + 8, 0xFFCfd8e0, false);

        // Scanner slot.
        context.fill(slotX - 4, slotY - 4, slotX + slotW + 4, slotY + slotH + 4, 0xFF3A4250);
        context.fill(slotX, slotY, slotX + slotW, slotY + slotH, 0xFF0C1014);
        context.drawBorder(slotX - 1, slotY - 1, slotW + 2, slotH + 2, 0xFF101418);
        // Slot guide rail.
        context.drawHorizontalLine(slotX + 6, slotX + slotW - 6, slotY + slotH / 2, 0xFF2C3642);

        // Card.
        int cx = cardLeft();
        int cy = cardTop();
        context.fill(cx, cy, cx + cardW, cy + cardH, 0xFF0E78C8);
        context.drawBorder(cx - 1, cy - 1, cardW + 2, cardH + 2, 0xFF101418);
        // Magnetic stripe on the card.
        context.fill(cx + 6, cy + 10, cx + cardW - 6, cy + 18, 0xFF0A0A0A);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("CARD"),
                cx + cardW / 2, cy + cardH - 16, 0xFFFFFFFF);

        // Status light.
        int lx = centerX() - 9;
        int ly = slotY + slotH + 20;
        int lightColor;
        switch (lightState) {
            case 1: lightColor = 0xFF35E04A; break;
            case 2: lightColor = 0xFFE03030; break;
            default: lightColor = 0xFF707880; break;
        }
        fillCircle(context, lx + 9, ly + 9, 9, lightColor);
        context.drawBorder(lx, ly, 19, 19, 0xFF101418);
        context.drawText(textRenderer, Text.literal(message),
                lx + 28, ly + 6, 0xFFCfd8e0, false);
    }

    private void fillCircle(DrawContext context, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r * r) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    private boolean overCard(double mx, double my) {
        int cx = cardLeft();
        int cy = cardTop();
        return mx >= cx && mx <= cx + cardW && my >= cy && my <= cy + cardH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (overCard(mouseX, mouseY)) {
            dragging = true;
            grabDX = mouseX - cardLeft();
            swipeStartMs = System.currentTimeMillis();
            swipeStartOffset = cardOffset;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isFinished() || !dragging) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        double newOffset = mouseX - grabDX - slotX;
        cardOffset = Math.max(0.0, Math.min(maxOffset(), newOffset));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0 || !dragging) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        dragging = false;

        double distance = cardOffset - swipeStartOffset;
        double durationSec = (System.currentTimeMillis() - swipeStartMs) / 1000.0;
        if (durationSec < 0.001) durationSec = 0.001;
        double speed = Math.abs(distance) / durationSec;

        if (distance < MIN_DISTANCE || speed < MIN_SPEED || speed > MAX_SPEED) {
            lightState = 2;
            lightTimer = 20;
            cardOffset = 0.0;
            if (distance < MIN_DISTANCE) message = "Too short - try again";
            else if (speed < MIN_SPEED) message = "Too slow - try again";
            else message = "Too fast - try again";
        } else {
            lightState = 1;
            message = "Accepted";
            completeTask();
        }
        return true;
    }
}
