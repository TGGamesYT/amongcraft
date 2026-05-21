package me.tg.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * O2 Filter — 6 green leaves scattered in a play area. The player drags leaves
 * into the bin rectangle on the left. Releasing with drag velocity flicks the
 * leaf, which then coasts with friction and bounces off the edges. Win when all
 * 6 leaves rest inside the bin.
 */
public class O2FilterTaskScreen extends TaskMinigameScreen {

    private static final int LEAF_COUNT = 6;
    private static final float LEAF_R = 9f;
    private static final float FRICTION = 0.96f;
    private static final float BOUNCE = -0.5f;
    private static final float MIN_VEL = 0.05f;

    private static final class Leaf {
        float x, y, vx, vy;
        boolean dragging;
    }

    private final Random random = new Random();
    private final Leaf[] leaves = new Leaf[LEAF_COUNT];

    // Play area (canvas).
    private int areaX1, areaY1, areaX2, areaY2;
    // Bin rectangle (inside the area, on the left).
    private int binX1, binY1, binX2, binY2;

    // Drag tracking.
    private Leaf dragged = null;
    private double dragStartX, dragStartY;
    private double lastMouseX, lastMouseY;

    public O2FilterTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "O2 Filter");
        this.panelWidth = 360;
        this.panelHeight = 300;
    }

    @Override
    protected void initTask() {
        areaX1 = panelX + 24;
        areaX2 = panelX + panelWidth - 24;
        areaY1 = contentTop() + 22;
        areaY2 = panelY + panelHeight - 20;

        // Bin: left side, vertically centered-ish.
        binX1 = areaX1;
        binX2 = areaX1 + 64;
        int binH = 150;
        binY1 = areaY1 + ((areaY2 - areaY1) - binH) / 2;
        binY2 = binY1 + binH;

        for (int i = 0; i < LEAF_COUNT; i++) {
            Leaf l = new Leaf();
            // Spawn to the right of the bin so leaves start outside it.
            l.x = binX2 + 30 + random.nextFloat() * (areaX2 - binX2 - 50);
            l.y = areaY1 + LEAF_R + random.nextFloat() * (areaY2 - areaY1 - LEAF_R * 2);
            leaves[i] = l;
        }
    }

    @Override
    protected void tickTask() {
        for (Leaf l : leaves) {
            if (l.dragging) continue;
            l.x += l.vx;
            l.y += l.vy;
            l.vx *= FRICTION;
            l.vy *= FRICTION;
            if (Math.abs(l.vx) < MIN_VEL) l.vx = 0;
            if (Math.abs(l.vy) < MIN_VEL) l.vy = 0;

            // Horizontal bounce off the area edges.
            if (l.x - LEAF_R < areaX1) {
                l.x = areaX1 + LEAF_R;
                l.vx *= BOUNCE;
            }
            if (l.x + LEAF_R > areaX2) {
                l.x = areaX2 - LEAF_R;
                l.vx *= BOUNCE;
            }
            // Vertical bounce off the area edges.
            if (l.y - LEAF_R < areaY1) {
                l.y = areaY1 + LEAF_R;
                l.vy *= BOUNCE;
            }
            if (l.y + LEAF_R > areaY2) {
                l.y = areaY2 - LEAF_R;
                l.vy *= BOUNCE;
            }
        }
        checkCompletion();
    }

    private boolean inBin(Leaf l) {
        return l.x > binX1 && l.x < binX2 && l.y > binY1 && l.y < binY2;
    }

    private void checkCompletion() {
        for (Leaf l : leaves) {
            if (!inBin(l)) return;
        }
        completeTask();
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Canvas background.
        context.fill(areaX1, areaY1, areaX2, areaY2, 0xFF000000);
        context.drawBorder(areaX1, areaY1, areaX2 - areaX1, areaY2 - areaY1, 0xFF555555);

        context.drawText(textRenderer, "Drag the leaves into the bin",
                panelX + 24, contentTop() + 6, 0xFFCCCCCC, false);

        // Bin: translucent fill + walls.
        context.fill(binX1, binY1, binX2, binY2, 0x4D505050);
        context.fill(binX1, binY1, binX1 + 4, binY2, 0xFF555555);          // left wall
        context.fill(binX1, binY1, binX2, binY1 + 4, 0xFF555555);          // top wall
        context.fill(binX1, binY2 - 4, binX2, binY2, 0xFF555555);          // bottom wall

        // Leaves.
        for (Leaf l : leaves) {
            int color = inBin(l) ? 0xFF55DD55 : 0xFF22AA22;
            drawCircle(context, l.x, l.y, LEAF_R, color);
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
                if (px < areaX1 || px >= areaX2 || py < areaY1 || py >= areaY2) continue;
                float dx = px + 0.5f - cx;
                float dy = py + 0.5f - cy;
                if (dx * dx + dy * dy <= r2) {
                    context.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Pick the nearest leaf under the cursor.
        Leaf best = null;
        double bestDist = LEAF_R;
        for (Leaf l : leaves) {
            double dx = l.x - mouseX;
            double dy = l.y - mouseY;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < bestDist) {
                bestDist = d;
                best = l;
            }
        }
        if (best != null) {
            dragged = best;
            best.dragging = true;
            best.vx = 0;
            best.vy = 0;
            dragStartX = mouseX;
            dragStartY = mouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (isFinished()) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        if (dragged != null && button == 0) {
            float mx = (float) mouseX;
            float my = (float) mouseY;
            // Clamp inside the play area.
            if (mx < areaX1 + LEAF_R) mx = areaX1 + LEAF_R;
            if (mx > areaX2 - LEAF_R) mx = areaX2 - LEAF_R;
            if (my < areaY1 + LEAF_R) my = areaY1 + LEAF_R;
            if (my > areaY2 - LEAF_R) my = areaY2 - LEAF_R;
            dragged.x = mx;
            dragged.y = my;
            dragged.vx = 0;
            dragged.vy = 0;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseReleased(mouseX, mouseY, button);
        if (dragged != null && button == 0) {
            dragged.dragging = false;
            // Flick: small velocity from the overall drag displacement.
            float vx = (float) (mouseX - dragStartX) * 0.14f;
            float vy = (float) (mouseY - dragStartY) * 0.14f;
            // Cap the flick speed so leaves don't fly absurdly fast.
            float speed = (float) Math.sqrt(vx * vx + vy * vy);
            float max = 8f;
            if (speed > max) {
                vx = vx / speed * max;
                vy = vy / speed * max;
            }
            dragged.vx = vx;
            dragged.vy = vy;
            dragged = null;
            checkCompletion();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
