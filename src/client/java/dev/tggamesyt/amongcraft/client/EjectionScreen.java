package dev.tggamesyt.amongcraft.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native, fully in-game recreation of the Among Us ejection screen.
 *
 * <p>Replaces the old MCEF/HTML {@code voteEnd} browser screen. It first shows
 * who voted for whom, then flings the ejected player across a starfield and
 * types out whether they were an impostor.</p>
 */
public class EjectionScreen extends Screen {

    private static final int VOTER_STAGGER = 4;   // ticks between each voter icon appearing
    private static final int FLY_DURATION = 55;   // ticks the body takes to cross the screen

    private final MinecraftClient client = MinecraftClient.getInstance();

    private final Map<UUID, List<UUID>> votesByTarget;
    private final UUID eliminated;
    private final boolean wasImpostor;
    private final int totalVoters;
    private final String resultText;

    // Timeline (in ticks).
    private final int votesEndTick;
    private final int pauseEndTick;
    private final int flyEndTick;
    private final int textStartTick;
    private final int textEndTick;
    private final int closeTick;

    private float ticks = 0f;
    private final List<int[]> stars = new ArrayList<>();
    private LivingEntity bodyEntity;

    public EjectionScreen(Map<UUID, List<UUID>> votesByTarget, UUID eliminated, boolean wasImpostor) {
        super(Text.literal("Ejection"));
        this.votesByTarget = votesByTarget;
        this.eliminated = (eliminated != null && eliminated.equals(Util.NIL_UUID)) ? null : eliminated;
        this.wasImpostor = wasImpostor;

        int voters = 0;
        for (List<UUID> list : votesByTarget.values()) {
            voters += list.size();
        }
        this.totalVoters = voters;

        this.votesEndTick = totalVoters * VOTER_STAGGER + 15;
        this.pauseEndTick = votesEndTick + 12;

        if (this.eliminated == null) {
            this.resultText = "No one was ejected.";
            this.flyEndTick = pauseEndTick;
            this.textStartTick = pauseEndTick;
            this.textEndTick = pauseEndTick;
            this.closeTick = pauseEndTick + 90;
        } else {
            String name = nameFor(this.eliminated);
            this.resultText = name + (wasImpostor ? " was An Impostor." : " was not An Impostor.");
            this.flyEndTick = pauseEndTick + FLY_DURATION;
            this.textStartTick = flyEndTick + 8;
            this.textEndTick = textStartTick + resultText.length();
            this.closeTick = textEndTick + 70;
        }
    }

    @Override
    protected void init() {
        stars.clear();
        Random random = Random.create();
        for (int i = 0; i < 170; i++) {
            stars.add(new int[]{
                    random.nextInt(Math.max(1, width)),
                    random.nextInt(Math.max(1, height)),
                    1 + random.nextInt(2)
            });
        }
        if (eliminated != null) {
            bodyEntity = resolveBodyEntity(eliminated);
        }
    }

    @Override
    public void tick() {
        ticks += 1f;
        if (ticks >= closeTick) {
            client.setScreen(null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float t = ticks + delta;
        context.fill(0, 0, width, height, 0xFF000000);

        if (t < pauseEndTick) {
            renderVotes(context, t);
        } else {
            renderStars(context);
            if (eliminated == null) {
                drawCenteredText(context, resultText, height / 2, 0xFFFFFFFF);
            } else {
                if (t < flyEndTick) {
                    renderFlyingBody(context, t);
                }
                if (t >= textStartTick) {
                    int chars = (int) Math.min(resultText.length(), t - textStartTick);
                    String shown = resultText.substring(0, Math.max(0, chars));
                    int color = wasImpostor ? 0xFFFF5555 : 0xFFFFFFFF;
                    drawCenteredText(context, shown, (int) (height * 0.78f), color);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderVotes(DrawContext context, float t) {
        List<Map.Entry<UUID, List<UUID>>> rows = new ArrayList<>(votesByTarget.entrySet());
        if (rows.isEmpty()) {
            drawCenteredText(context, "No votes were cast.", height / 2, 0xFFAAAAAA);
            return;
        }

        int rowHeight = 30;
        int startY = height / 2 - (rows.size() * rowHeight) / 2;
        int targetFace = 24;
        int voterFace = 16;
        int labelX = width / 2 - 170;
        int votersX = width / 2 + 10;

        int voterIndex = 0;
        for (int r = 0; r < rows.size(); r++) {
            Map.Entry<UUID, List<UUID>> row = rows.get(r);
            int y = startY + r * rowHeight;

            drawFace(context, row.getKey(), labelX, y, targetFace);
            context.drawTextWithShadow(textRenderer, nameFor(row.getKey()),
                    labelX + targetFace + 6, y + targetFace / 2 - 4, 0xFFFFFFFF);

            int vx = votersX;
            for (UUID voter : row.getValue()) {
                if (t >= voterIndex * VOTER_STAGGER) {
                    drawFace(context, voter, vx, y + (targetFace - voterFace) / 2, voterFace);
                    vx += voterFace + 4;
                }
                voterIndex++;
            }
        }
    }

    private void renderStars(DrawContext context) {
        for (int[] star : stars) {
            int c = star[2] == 1 ? 0xFF888888 : 0xFFFFFFFF;
            context.fill(star[0], star[1], star[0] + star[2], star[1] + star[2], c);
        }
    }

    private void renderFlyingBody(DrawContext context, float t) {
        float progress = MathHelper.clamp((t - pauseEndTick) / FLY_DURATION, 0f, 1f);
        float x = -150f + progress * (width + 300f);
        float y = height / 2f + 20f;
        float roll = progress * 720f;
        int scale = Math.max(30, height / 9);

        if (bodyEntity != null) {
            drawSpinningEntity(context, x, y, scale, roll, bodyEntity);
        } else {
            drawSpinningFace(context, eliminated, x, y - 20f, roll, 96);
        }
    }

    private void drawSpinningEntity(DrawContext context, float x, float y, int scale, float roll, LivingEntity entity) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 1050.0);
        matrices.scale(scale, -scale, scale);

        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(roll))
                .rotateY((float) Math.toRadians(180f));
        matrices.multiply(rotation);

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);
        dispatcher.render(entity, 0, 0, 0, 0f, 1.0f, matrices, context.getVertexConsumers(), 15728880);
        dispatcher.setRenderShadows(true);

        matrices.pop();
        context.draw();
    }

    private void drawSpinningFace(DrawContext context, UUID uuid, float cx, float cy, float roll, int size) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx, cy, 0);
        matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(roll)));
        drawFace(context, uuid, -size / 2, -size / 2, size);
        matrices.pop();
    }

    private void drawFace(DrawContext context, UUID uuid, int x, int y, int size) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) {
            context.fill(x, y, x + size, y + size, 0xFF444444);
            context.drawCenteredTextWithShadow(textRenderer, "?", x + size / 2, y + size / 2 - 4, 0xFFBBBBBB);
            return;
        }
        Identifier skin = skinFor(uuid);
        context.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        RenderSystem.enableBlend();
        context.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private void drawCenteredText(DrawContext context, String text, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, text, width / 2, y, color);
    }

    private Identifier skinFor(UUID uuid) {
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                return entry.getSkinTexture();
            }
        }
        return DefaultSkinHelper.getTexture(uuid);
    }

    private String nameFor(UUID uuid) {
        if (uuid == null || uuid.equals(Util.NIL_UUID)) {
            return "Skipped";
        }
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null && entry.getProfile().getName() != null) {
                return entry.getProfile().getName();
            }
        }
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                return player.getName().getString();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private LivingEntity resolveBodyEntity(UUID uuid) {
        if (client.world == null) return null;

        PlayerEntity worldPlayer = client.world.getPlayerByUuid(uuid);
        if (worldPlayer != null) {
            return worldPlayer;
        }
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                GameProfile profile = entry.getProfile();
                return new CutsceneScreen.CustomSkinnedPlayer(client, client.world, profile, entry.getSkinTexture());
            }
        }
        return null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
