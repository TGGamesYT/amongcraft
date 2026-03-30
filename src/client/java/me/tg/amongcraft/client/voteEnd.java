package me.tg.amongcraft.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class voteEnd extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 0;

    private MCEFBrowser browser;

    private final MinecraftClient minecraft = MinecraftClient.getInstance();

    public voteEnd(Text title) {
        super(title);
    }

    protected void init(String url) {
        super.init();
        if (browser == null) {
            boolean transparent = true;
            browser = MCEF.createBrowser(url, transparent);
            resizeBrowser();
        }
    }

    public void endVote(Map<UUID, Set<UUID>> votes, @Nullable UUID eliminated, boolean isImpostor) {
        // Convert to URL query string
        JsonObject votesJson = new JsonObject();
        for (Map.Entry<UUID, Set<UUID>> entry : votes.entrySet()) {
            JsonArray arr = new JsonArray();
            for (UUID voter : entry.getValue()) {
                arr.add(voter.toString());
            }
            votesJson.add(entry.getKey().toString(), arr);
        }

        String votesStr = votesJson.toString();
        String elimStr = eliminated != null ? eliminated.toString() : "";
        int roleFlag = isImpostor ? 1 : 0;

        String url = "https://tg.is-a.dev/amongcraft/endvote.html"
                + "?votes=" + URLEncoder.encode(votesStr, StandardCharsets.UTF_8)
                + "&elim=" + elimStr
                + "&elimrole=" + roleFlag;

        init(url);
    }


    private int mouseX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getScaleFactor());
    }

    private int mouseY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getScaleFactor());
    }

    private int scaleX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getScaleFactor());
    }

    private int scaleY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getScaleFactor());
    }

    private void resizeBrowser() {
        if (width > 100 && height > 100) {
            browser.resize(scaleX(width), scaleY(height));
        }
    }

    @Override
    public void resize(MinecraftClient minecraft, int i, int j) {
        super.resize(minecraft, i, j);
        resizeBrowser();
    }

    @Override
    public void close() {
        browser.close();
        super.close();
    }
    @Override
    public void render(DrawContext guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buffer = t.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).texture(0.0f, 1.0f).color(255, 255, 255, 255).next();
        buffer.vertex(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0).texture(1.0f, 1.0f).color(255, 255, 255, 255).next();
        buffer.vertex(width - BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET, 0).texture(1.0f, 0.0f).color(255, 255, 255, 255).next();
        buffer.vertex(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET, 0).texture(0.0f, 0.0f).color(255, 255, 255, 255).next();
        t.draw();
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
        if (browser.getURL().startsWith("amongcraft://endmeeting")) {
            close();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        browser.sendMousePress(mouseX(mouseX), mouseY(mouseY), button);
        browser.setFocus(true);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        browser.sendMouseRelease(mouseX(mouseX), mouseY(mouseY), button);
        browser.setFocus(true);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), delta, 0);
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == (char) 0) return false;
        browser.sendKeyTyped(codePoint, modifiers);
        browser.setFocus(true);
        return super.charTyped(codePoint, modifiers);
    }
}


