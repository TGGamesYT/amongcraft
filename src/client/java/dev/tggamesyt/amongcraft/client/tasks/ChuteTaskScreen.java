package dev.tggamesyt.amongcraft.client.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Empty Garbage Chute minigame. Drag the lever handle from the top of the
 * track to the bottom; release early and it springs back. Reaching the bottom
 * releases falling trash and completes the task.
 */
public class ChuteTaskScreen extends TaskMinigameScreen {

    private static final int HANDLE_H = 16;

    private int trackX, trackY, trackW, trackH;
    private int handleY;
    private boolean dragging = false;
    private double dragOffset = 0;
    private boolean opened = false;
    private int openTimer = 0;

    private float[] trashX;
    private float[] trashY;
    private int[] trashSize;

    public ChuteTaskScreen(String taskId, BlockPos pos) {
        super(taskId, pos, "Empty Garbage Chute");
    }

    @Override
    protected void initTask() {
        trackW = 34;
        trackX = panelX + panelWidth - 30 - trackW;
        trackY = contentTop() + 16;
        trackH = panelHeight - 22 - 36;
        handleY = trackY;

        Random rng = new Random();
        trashX = new float[10];
        trashY = new float[10];
        trashSize = new int[10];
        int areaX = panelX + 24;
        int areaW = trackX - 24 - areaX;
        for (int i = 0; i < 10; i++) {
            trashX[i] = areaX + rng.nextFloat() * areaW;
            trashY[i] = contentTop() - rng.nextFloat() * (panelHeight);
            trashSize[i] = 8 + rng.nextInt(9);
        }
    }

    private int handleBottom() {
        return trackY + trackH - HANDLE_H;
    }

    @Override
    protected void tickTask() {
        if (opened) {
            openTimer++;
            for (int i = 0; i < 10; i++) {
                trashY[i] += 6f;
            }
            if (openTimer >= 30) { // ~1.5s
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f));
                completeTask();
            }
        }
    }

    @Override
    protected void renderTask(DrawContext context, int mouseX, int mouseY, float delta) {
        // instruction
        context.drawText(textRenderer, "Pull the lever down",
                panelX + 24, contentTop(), 0xFFCCD4DD, false);

        // track
        context.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF3A3A3A);
        context.drawBorder(trackX - 1, trackY - 1, trackW + 2, trackH + 2, 0xFF101418);

        // bottom goal marker
        context.fill(trackX, trackY + trackH - 3, trackX + trackW, trackY + trackH, 0xFF55AA55);

        // handle
        int hColor = dragging ? 0xFFE0E0E0 : 0xFFAAAAAA;
        context.fill(trackX, handleY, trackX + trackW, handleY + HANDLE_H, hColor);
        context.fill(trackX - 4, handleY + HANDLE_H / 2 - 2, trackX + trackW + 4,
                handleY + HANDLE_H / 2 + 2, 0xFFDDDDDD);
        context.drawBorder(trackX, handleY, trackW, HANDLE_H, 0xFF101418);

        // falling trash
        if (opened) {
            int clipTop = contentTop();
            int clipBottom = panelY + panelHeight - 6;
            for (int i = 0; i < 10; i++) {
                int x = (int) trashX[i];
                int y = (int) trashY[i];
                int s = trashSize[i];
                int y1 = Math.max(y, clipTop);
                int y2 = Math.min(y + s, clipBottom);
                if (y2 > y1) {
                    context.fill(x, y1, x + s, y2, 0xFF7A4A24);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFinished() || opened || button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (mouseX >= trackX && mouseX <= trackX + trackW
                && mouseY >= handleY && mouseY <= handleY + HANDLE_H) {
            dragging = true;
            dragOffset = mouseY - handleY;
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && !opened) {
            int newY = (int) (mouseY - dragOffset);
            handleY = Math.max(trackY, Math.min(handleBottom(), newY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && !opened) {
            dragging = false;
            if (handleY >= handleBottom()) {
                opened = true;
                openTimer = 0;
            } else {
                handleY = trackY; // spring back
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f));
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
