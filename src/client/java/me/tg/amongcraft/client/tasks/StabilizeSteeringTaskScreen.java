package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/**
 * Stabilize Steering — a large steering circle with a faint grid and a red
 * center crosshair. The player drags a point (constrained inside the circle)
 * onto the center; when within ~14px of center it snaps/locks and the task
 * completes.
 */
public class StabilizeSteeringTaskScreen extends TaskMinigameScreen {

    private static final float TARGET_RADIUS = 14f;
    private static final int GRID_COUNT = 6;

    private float cx, cy;          // steering center
    private float outerRadius;
    private float innerRadius;

    private float pointX, pointY;
    private static final float POINT_R = 6f;

    private boolean dragging = false;
    private float dragOffX, dragOffY;
    private boolean locked = false;

    public StabilizeSteeringTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Stabilize Steering");
        this.panelWidth = 320;
        this.panelHeight = 300;
    }

    @Override
    protected void initTask() {
        cx = centerX();
        cy = contentTop() + (panelY + panelHeight - contentTop()) / 2 + 6;
        outerRadius = Math.min(panelWidth, panelY + panelHeight - contentTop()) / 2f - 26f;
        innerRadius = outerRadius - 6f;

        // Start the point off-center but inside the circle.
        pointX = cx + innerRadius * 0.6f;
        pointY = cy - innerRadius * 0.35f;
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer,
                net.minecraft.text.Text.literal("Drag the point onto the crosshair center"),
                centerX(), contentTop() + 4, 0xFFCCE6FF);

        // Filled circle background.
        fillCircle(context, cx, cy, innerRadius, 0xFF1A3A5A);

        // Outer + inner circle outlines.
        drawCircleOutline(context, cx, cy, outerRadius, 0xFF005F99, 3);
        drawCircleOutline(context, cx, cy, innerRadius, 0xFF007ACC, 2);

        // Faint dashed grid inside the circle.
        drawGrid(context);

        // Bold crosshair axes through center.
        drawClippedHLine(context, cy, 0xFF3C9FE0);
        drawClippedVLine(context, cx, 0xFF3C9FE0);

        // Red center crosshair.
        int chSize = 16;
        context.fill((int) cx - chSize, (int) cy - 1, (int) cx + chSize, (int) cy + 2, 0xFFE02020);
        context.fill((int) cx - 1, (int) cy - chSize, (int) cx + 2, (int) cy + chSize, 0xFFE02020);

        // White crosshair lines through the draggable point.
        drawClippedHLine(context, pointY, 0xFFFFFFFF);
        drawClippedVLine(context, pointX, 0xFFFFFFFF);

        // The draggable point.
        fillCircle(context, pointX, pointY, POINT_R, locked ? 0xFF55DD55 : 0xFF004080);
        drawCircleOutline(context, pointX, pointY, POINT_R, 0xFFFFFFFF, 1);
    }

    /** Draws a horizontal line clipped to the inner circle, at row y. */
    private void drawClippedHLine(DrawContext context, float y, int color) {
        float dy = y - cy;
        if (Math.abs(dy) > innerRadius) return;
        float half = (float) Math.sqrt(innerRadius * innerRadius - dy * dy);
        context.fill((int) (cx - half), (int) y - 1, (int) (cx + half), (int) y + 1, color);
    }

    /** Draws a vertical line clipped to the inner circle, at column x. */
    private void drawClippedVLine(DrawContext context, float x, int color) {
        float dx = x - cx;
        if (Math.abs(dx) > innerRadius) return;
        float half = (float) Math.sqrt(innerRadius * innerRadius - dx * dx);
        context.fill((int) x - 1, (int) (cy - half), (int) x + 1, (int) (cy + half), color);
    }

    private void drawGrid(DrawContext context) {
        int color = 0x664DA6FF;
        for (int i = -GRID_COUNT; i <= GRID_COUNT; i++) {
            if (i == 0) continue;
            float off = i * (innerRadius / GRID_COUNT);
            // Horizontal grid line.
            float y = cy + off;
            if (Math.abs(off) < innerRadius) {
                float half = (float) Math.sqrt(innerRadius * innerRadius - off * off);
                drawDashedH(context, cx - half, cx + half, y, color);
            }
            // Vertical grid line.
            float x = cx + off;
            if (Math.abs(off) < innerRadius) {
                float half = (float) Math.sqrt(innerRadius * innerRadius - off * off);
                drawDashedV(context, x, cy - half, cy + half, color);
            }
        }
    }

    private void drawDashedH(DrawContext context, float x1, float x2, float y, int color) {
        for (float x = x1; x < x2; x += 8f) {
            float end = Math.min(x + 4f, x2);
            context.fill((int) x, (int) y, (int) end, (int) y + 1, color);
        }
    }

    private void drawDashedV(DrawContext context, float x, float y1, float y2, int color) {
        for (float y = y1; y < y2; y += 8f) {
            float end = Math.min(y + 4f, y2);
            context.fill((int) x, (int) y, (int) x + 1, (int) end, color);
        }
    }

    private void fillCircle(DrawContext context, float ccx, float ccy, float r, int color) {
        int minX = (int) Math.floor(ccx - r);
        int maxX = (int) Math.ceil(ccx + r);
        int minY = (int) Math.floor(ccy - r);
        int maxY = (int) Math.ceil(ccy + r);
        float r2 = r * r;
        for (int px = minX; px < maxX; px++) {
            for (int py = minY; py < maxY; py++) {
                float dx = px + 0.5f - ccx;
                float dy = py + 0.5f - ccy;
                if (dx * dx + dy * dy <= r2) {
                    context.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private void drawCircleOutline(DrawContext context, float ccx, float ccy, float r,
                                   int color, int thickness) {
        int minX = (int) Math.floor(ccx - r - thickness);
        int maxX = (int) Math.ceil(ccx + r + thickness);
        int minY = (int) Math.floor(ccy - r - thickness);
        int maxY = (int) Math.ceil(ccy + r + thickness);
        float outer2 = (r + thickness * 0.5f) * (r + thickness * 0.5f);
        float inner2 = (r - thickness * 0.5f) * (r - thickness * 0.5f);
        for (int px = minX; px < maxX; px++) {
            for (int py = minY; py < maxY; py++) {
                float dx = px + 0.5f - ccx;
                float dy = py + 0.5f - ccy;
                float d2 = dx * dx + dy * dy;
                if (d2 <= outer2 && d2 >= inner2) {
                    context.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || locked) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && dist((float) mouseX, (float) mouseY, pointX, pointY) <= POINT_R + 3) {
            dragging = true;
            dragOffX = (float) mouseX - pointX;
            dragOffY = (float) mouseY - pointY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (isFinished() || locked || !dragging) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        float mx = (float) mouseX - dragOffX;
        float my = (float) mouseY - dragOffY;

        // Constrain inside the circle.
        float dx = mx - cx;
        float dy = my - cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float maxDist = innerRadius - POINT_R;
        if (d > maxDist && d > 0) {
            mx = cx + dx / d * maxDist;
            my = cy + dy / d * maxDist;
        }
        pointX = mx;
        pointY = my;

        // Snap/lock if within the target radius of center.
        if (dist(pointX, pointY, cx, cy) <= TARGET_RADIUS) {
            locked = true;
            dragging = false;
            pointX = cx;
            pointY = cy;
            completeTask();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
