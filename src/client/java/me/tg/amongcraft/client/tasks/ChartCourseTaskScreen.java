package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Chart Course minigame. Five waypoints connected in order by a dashed path.
 * A draggable "ship" marker rides the path; dragging it within ~16px of the next
 * waypoint snaps it there and advances. Visiting all 5 in order wins.
 */
public class ChartCourseTaskScreen extends TaskMinigameScreen {

    private static final int POINT_COUNT = 5;
    private static final int POINT_RADIUS = 7;
    private static final int SHIP_RADIUS = 9;
    private static final double HIT_DISTANCE = 16.0;

    private final int[] px = new int[POINT_COUNT];
    private final int[] py = new int[POINT_COUNT];
    private final boolean[] visited = new boolean[POINT_COUNT];
    private int currentIndex = 0;

    private double shipX;
    private double shipY;
    private boolean dragging = false;

    public ChartCourseTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Chart Course");
    }

    @Override
    protected void initTask() {
        Random rng = new Random();
        int marginX = 36;
        int top = contentTop() + 26;
        int bottom = panelY + panelHeight - 24;
        int usableW = panelWidth - 2 * marginX;
        int step = usableW / (POINT_COUNT - 1);
        for (int i = 0; i < POINT_COUNT; i++) {
            px[i] = panelX + marginX + i * step;
            py[i] = top + rng.nextInt(Math.max(1, bottom - top));
        }
        visited[0] = true;
        currentIndex = 1;
        shipX = px[0];
        shipY = py[0];
    }

    /** Closest point on segment A-B to P, stored back via the returned 2-array. */
    private double[] closestOnSegment(int ax, int ay, int bx, int by, double pX, double pY) {
        double abx = bx - ax, aby = by - ay;
        double apx = pX - ax, apy = pY - ay;
        double ab2 = abx * abx + aby * aby;
        double t = ab2 == 0 ? 0 : (apx * abx + apy * aby) / ab2;
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[]{ ax + abx * t, ay + aby * t };
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawText(textRenderer, Text.literal("Drag the ship through every waypoint in order."),
                panelX + 14, contentTop() + 6, 0xFFCfd8e0, false);

        // Dashed path between consecutive waypoints.
        for (int i = 0; i < POINT_COUNT - 1; i++) {
            drawDashedLine(context, px[i], py[i], px[i + 1], py[i + 1], 0xFF6E7B88);
        }

        // Waypoints.
        for (int i = 0; i < POINT_COUNT; i++) {
            int color;
            if (visited[i]) color = 0xFF35E04A;
            else if (i == currentIndex) color = 0xFFE0D020;
            else color = 0xFF707880;
            fillCircle(context, px[i], py[i], POINT_RADIUS, color);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(i + 1)),
                    px[i], py[i] - 4, 0xFF101418);
        }

        // Ship.
        int sx = (int) Math.round(shipX);
        int sy = (int) Math.round(shipY);
        fillCircle(context, sx, sy, SHIP_RADIUS, 0xFF35B6E0);
        context.drawBorder(sx - SHIP_RADIUS, sy - SHIP_RADIUS,
                SHIP_RADIUS * 2 + 1, SHIP_RADIUS * 2 + 1, 0xFF101418);
        // Small white pointer triangle.
        for (int dy = 0; dy < 8; dy++) {
            int w = dy / 2;
            context.fill(sx - w, sy - SHIP_RADIUS + 3 + dy, sx + w + 1,
                    sy - SHIP_RADIUS + 4 + dy, 0xFFFFFFFF);
        }
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

    private void drawDashedLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) steps = 1;
        for (int s = 0; s <= steps; s++) {
            if ((s / 5) % 2 == 0) {
                int x = x1 + dx * s / steps;
                int y = y1 + dy * s / steps;
                context.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
        }
    }

    private void checkReached() {
        if (currentIndex >= POINT_COUNT) return;
        double dx = shipX - px[currentIndex];
        double dy = shipY - py[currentIndex];
        if (Math.sqrt(dx * dx + dy * dy) <= HIT_DISTANCE) {
            shipX = px[currentIndex];
            shipY = py[currentIndex];
            visited[currentIndex] = true;
            currentIndex++;
            if (currentIndex >= POINT_COUNT) {
                completeTask();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double dx = mouseX - shipX;
        double dy = mouseY - shipY;
        if (Math.sqrt(dx * dx + dy * dy) <= SHIP_RADIUS + 3) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isFinished() || !dragging) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (currentIndex >= POINT_COUNT) return true;
        int a = currentIndex - 1;
        int b = currentIndex;
        double[] c = closestOnSegment(px[a], py[a], px[b], py[b], mouseX, mouseY);
        shipX = c[0];
        shipY = c[1];
        checkReached();
        return true;
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
