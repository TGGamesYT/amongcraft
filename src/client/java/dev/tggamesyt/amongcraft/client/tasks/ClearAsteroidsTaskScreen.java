package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Clear Asteroids — gray asteroids drift across a dark space area. Clicking one
 * destroys it. Destroy 20 to win.
 */
public class ClearAsteroidsTaskScreen extends TaskMinigameScreen {

    private static final int TOTAL_TO_DESTROY = 20;
    private static final int MAX_ASTEROIDS = 5;
    private static final int SPAWN_INTERVAL = 16; // ticks (~0.8s)

    private static final class Asteroid {
        float x, y, r, speed;
    }

    private final Random random = new Random();
    private final List<Asteroid> asteroids = new ArrayList<>();
    private int destroyed = 0;
    private int spawnTimer = 0;

    // Play area
    private int areaX1, areaY1, areaX2, areaY2;

    public ClearAsteroidsTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Clear Asteroids");
        this.panelWidth = 360;
        this.panelHeight = 300;
    }

    @Override
    protected void initTask() {
        areaX1 = panelX + 20;
        areaX2 = panelX + panelWidth - 20;
        areaY1 = contentTop() + 22;
        areaY2 = panelY + panelHeight - 20;
    }

    private void spawnAsteroid() {
        Asteroid a = new Asteroid();
        a.r = 10 + random.nextFloat() * 10;
        a.x = areaX1 - a.r;
        a.y = areaY1 + a.r + random.nextFloat() * (areaY2 - areaY1 - a.r * 2);
        a.speed = 1f + random.nextFloat() * 1.5f;
        asteroids.add(a);
    }

    @Override
    protected void tickTask() {
        spawnTimer++;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0;
            if (asteroids.size() < MAX_ASTEROIDS) {
                spawnAsteroid();
            }
        }

        Iterator<Asteroid> it = asteroids.iterator();
        while (it.hasNext()) {
            Asteroid a = it.next();
            a.x += a.speed;
            if (a.x - a.r > areaX2) {
                it.remove();
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // Space background
        context.fill(areaX1, areaY1, areaX2, areaY2, 0xFF000000);
        context.drawBorder(areaX1, areaY1, areaX2 - areaX1, areaY2 - areaY1, 0xFF555555);

        // Counter
        context.drawText(textRenderer, "Destroyed: " + destroyed + " / " + TOTAL_TO_DESTROY,
                panelX + 20, contentTop() + 4, 0xFFFFFFFF, false);

        for (Asteroid a : asteroids) {
            drawCircle(context, a.x, a.y, a.r);
        }
    }

    private void drawCircle(DrawContext context, float cx, float cy, float r) {
        int minX = (int) Math.floor(cx - r);
        int maxX = (int) Math.ceil(cx + r);
        int minY = (int) Math.floor(cy - r);
        int maxY = (int) Math.ceil(cy + r);
        float r2 = r * r;
        float inner2 = (r - 2) * (r - 2);
        for (int px = minX; px < maxX; px++) {
            for (int py = minY; py < maxY; py++) {
                if (px < areaX1 || px >= areaX2 || py < areaY1 || py >= areaY2) continue;
                float dx = px + 0.5f - cx;
                float dy = py + 0.5f - cy;
                float d2 = dx * dx + dy * dy;
                if (d2 <= r2) {
                    int color = d2 >= inner2 ? 0xFF888888 : 0xFF808080;
                    context.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished()) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            for (int i = 0; i < asteroids.size(); i++) {
                Asteroid a = asteroids.get(i);
                double dx = a.x - mouseX;
                double dy = a.y - mouseY;
                if (Math.sqrt(dx * dx + dy * dy) < a.r) {
                    asteroids.remove(i);
                    destroyed++;
                    if (destroyed >= TOTAL_TO_DESTROY) {
                        completeTask();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
