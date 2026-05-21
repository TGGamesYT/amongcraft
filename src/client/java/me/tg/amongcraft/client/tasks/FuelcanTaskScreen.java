package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Fuel Engines minigame. A vertical fuel tank and a "HOLD TO FILL" button.
 * While the left mouse button is held down over the button the tank fills
 * (~3 seconds to full). Releasing stops filling, fuel level is kept. Reaching
 * 100% wins.
 */
public class FuelcanTaskScreen extends TaskMinigameScreen {

    /** Fuel level, 0..100. */
    private double fuel = 0.0;
    /** ~3s to fill at 20 ticks/s => 100 / 60. */
    private static final double FILL_PER_TICK = 100.0 / 60.0;

    private boolean holding = false;

    // Tank geometry.
    private int tankX, tankY, tankW, tankH;
    // Button geometry.
    private int btnX, btnY, btnW, btnH;

    public FuelcanTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Fuel Engines");
    }

    @Override
    protected void initTask() {
        tankW = 70;
        tankH = 150;
        tankX = centerX() - tankW / 2;
        tankY = contentTop() + 18;

        btnW = 160;
        btnH = 34;
        btnX = centerX() - btnW / 2;
        btnY = tankY + tankH + 20;
    }

    private boolean overButton(double mx, double my) {
        return mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
    }

    @Override
    protected void tickTask() {
        if (holding) {
            fuel = Math.min(100.0, fuel + FILL_PER_TICK);
            if (fuel >= 100.0) {
                completeTask();
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Tank shell.
        context.fill(tankX - 3, tankY - 3, tankX + tankW + 3, tankY + tankH + 3, 0xFF3A4250);
        context.fill(tankX, tankY, tankX + tankW, tankY + tankH, 0xFF0C1014);

        // Fuel fill (rises from the bottom).
        int fillH = (int) Math.round(fuel / 100.0 * tankH);
        if (fillH > 0) {
            context.fill(tankX, tankY + tankH - fillH, tankX + tankW, tankY + tankH, 0xFFFFCC00);
        }
        context.drawBorder(tankX - 1, tankY - 1, tankW + 2, tankH + 2, 0xFF101418);

        // Percentage label centred on the tank.
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal((int) Math.round(fuel) + "%"),
                centerX(), tankY + tankH / 2 - 4, 0xFF101418);

        // Hold-to-fill button.
        boolean over = overButton(mouseX, mouseY);
        int btnColor;
        if (holding) btnColor = 0xFFFFAA00;
        else if (over) btnColor = 0xFFFF9922;
        else btnColor = 0xFFFF8800;
        context.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
        context.drawBorder(btnX - 1, btnY - 1, btnW + 2, btnH + 2, 0xFF101418);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("HOLD TO FILL"),
                centerX(), btnY + btnH / 2 - 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (overButton(mouseX, mouseY)) {
            holding = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && holding) {
            holding = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // Stop filling if the cursor leaves the button while held.
        if (holding && !overButton(mouseX, mouseY)) {
            holding = false;
        }
        super.mouseMoved(mouseX, mouseY);
    }
}
