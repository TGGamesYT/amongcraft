package me.tg.amongcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.tg.amongcraft.SettingsManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * In-game meeting UI shown during the discussion and voting phases.
 *
 * <p>This screen is purely a view: all phase transitions and the timer are
 * driven by the server via {@code MEETING_PHASE_PACKET}. When voting ends the
 * server replaces this screen with {@link EjectionScreen}.</p>
 */
public class EmergencyMeetingScreen extends Screen {

    public enum Phase { DISCUSSION, VOTING }

    /** Shared with {@code MeetingChatMixin} so chat is mirrored into the meeting log. */
    public static List<String> chatMessages = new ArrayList<>();

    private static final int ROW_HEIGHT = 22;
    private static final int ICON_SIZE = 16;
    private static final int VISIBLE_ROWS = 8;

    private final MinecraftClient client = MinecraftClient.getInstance();

    private UUID caller;
    private UUID self;
    private final Set<UUID> alivePlayers = new HashSet<>();
    private List<UUID> playerList = new ArrayList<>();

    private Phase phase = Phase.DISCUSSION;
    private int timerTicks;

    private TextFieldWidget chatInput;
    private ButtonWidget voteButton;
    private ButtonWidget skipButton;

    private UUID selectedPlayer = null;
    private boolean hasVoted = false;
    private int scrollOffset = 0;

    private double savedChatScale = 1.0;

    public EmergencyMeetingScreen(UUID caller) {
        super(Text.literal("Emergency Meeting"));
        this.caller = caller;
        this.timerTicks = secondsSetting("meeting-discussion", 15) * 20;
    }

    public void onMeetingStarted(UUID caller, Set<UUID> alive) {
        this.caller = caller;
        this.alivePlayers.clear();
        this.alivePlayers.addAll(alive);
        this.playerList = new ArrayList<>(alive);
        this.playerList.sort(Comparator.comparing(this::nameFor, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        if (client.player != null) {
            this.self = client.player.getUuid();
        }
        chatMessages.clear();

        savedChatScale = client.options.getChatScale().getValue();
        client.options.getChatScale().setValue(0.0);

        chatInput = new TextFieldWidget(textRenderer, width / 2 - 120, height - 26, 240, 18, Text.literal("Chat"));
        chatInput.setMaxLength(256);
        addSelectableChild(chatInput);
        addDrawableChild(chatInput);

        voteButton = ButtonWidget.builder(Text.literal("Vote"), btn -> {
            if (selectedPlayer != null) vote(selectedPlayer);
        }).dimensions(width - 120, height - 78, 110, 20).build();
        skipButton = ButtonWidget.builder(Text.literal("Skip Vote"), btn -> vote(null))
                .dimensions(width - 120, height - 54, 110, 20).build();
        addDrawableChild(voteButton);
        addDrawableChild(skipButton);
    }

    @Override
    public void removed() {
        client.options.getChatScale().setValue(savedChatScale);
        super.removed();
    }

    public void setPhase(Phase newPhase, int secondsRemaining) {
        this.phase = newPhase;
        this.timerTicks = secondsRemaining * 20;
        if (newPhase == Phase.DISCUSSION) {
            this.selectedPlayer = null;
        }
    }

    @Override
    public void tick() {
        if (timerTicks > 0) timerTicks--;
    }

    private boolean isAlive() {
        return self != null && alivePlayers.contains(self);
    }

    private boolean canVote() {
        return phase == Phase.VOTING && isAlive() && !hasVoted;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        String header = (phase == Phase.DISCUSSION ? "Discussion" : "Voting") + "  -  " + (timerTicks / 20) + "s";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Emergency Meeting"), width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(header), width / 2, 24, 0xFFFFD050);

        renderPlayerList(context, mouseX, mouseY);
        renderChatLog(context);

        boolean votingControls = phase == Phase.VOTING && isAlive();
        voteButton.visible = votingControls;
        skipButton.visible = votingControls;
        voteButton.active = canVote() && selectedPlayer != null;
        skipButton.active = canVote();

        if (phase == Phase.VOTING && isAlive() && hasVoted) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Vote cast - waiting for others..."),
                    width / 2, height - 96, 0xFF66FF66);
        } else if (phase == Phase.VOTING && !isAlive()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("You are dead - spectating the vote"),
                    width / 2, height - 96, 0xFFAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayerList(DrawContext context, int mouseX, int mouseY) {
        int listX = width / 2 - 110;
        int listY = 42;

        int end = Math.min(scrollOffset + VISIBLE_ROWS, playerList.size());
        for (int i = scrollOffset; i < end; i++) {
            UUID uuid = playerList.get(i);
            int y = listY + (i - scrollOffset) * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX <= listX + 220 && mouseY >= y && mouseY <= y + ROW_HEIGHT;

            if (uuid.equals(selectedPlayer)) {
                context.fill(listX - 2, y - 2, listX + 222, y + ROW_HEIGHT - 2, 0x66FFFFFF);
            } else if (hovered && canVote()) {
                context.fill(listX - 2, y - 2, listX + 222, y + ROW_HEIGHT - 2, 0x33FFFFFF);
            }

            drawFace(context, uuid, listX, y, ICON_SIZE);
            context.drawTextWithShadow(textRenderer, nameFor(uuid), listX + ICON_SIZE + 6, y + 4, 0xFFFFFFFF);
            if (uuid.equals(caller)) {
                context.drawTextWithShadow(textRenderer, Text.literal("(called)"), listX + 150, y + 4, 0xFF8888FF);
            }
        }

        if (playerList.size() > VISIBLE_ROWS) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("▲"), listX + 110, listY - 12, 0xFFFFFFFF);
            }
            if (end < playerList.size()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("▼"),
                        listX + 110, listY + VISIBLE_ROWS * ROW_HEIGHT, 0xFFFFFFFF);
            }
        }
    }

    private void renderChatLog(DrawContext context) {
        int start = Math.max(0, chatMessages.size() - 8);
        List<String> visible = chatMessages.subList(start, chatMessages.size());
        for (int i = 0; i < visible.size(); i++) {
            context.drawTextWithShadow(textRenderer, visible.get(i), 8, height - 130 + i * 11, 0xFFDDDDDD);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (canVote()) {
            int listX = width / 2 - 110;
            int listY = 42;
            int end = Math.min(scrollOffset + VISIBLE_ROWS, playerList.size());
            for (int i = scrollOffset; i < end; i++) {
                int y = listY + (i - scrollOffset) * ROW_HEIGHT;
                if (mouseX >= listX - 2 && mouseX <= listX + 222 && mouseY >= y - 2 && mouseY <= y + ROW_HEIGHT - 2) {
                    selectedPlayer = playerList.get(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (playerList.size() > VISIBLE_ROWS) {
            scrollOffset -= (int) Math.signum(amount);
            scrollOffset = Math.max(0, Math.min(scrollOffset, playerList.size() - VISIBLE_ROWS));
        }
        return true;
    }

    private void vote(@Nullable UUID target) {
        if (!canVote()) return;
        hasVoted = true;
        selectedPlayer = target;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(target != null ? target : Util.NIL_UUID);
        ClientPlayNetworking.send(new Identifier("amongcraft", "vote"), buf);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chatInput.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            String message = chatInput.getText().trim();
            if (!message.isEmpty() && client.player != null) {
                client.player.networkHandler.sendChatMessage(message);
                chatInput.setText("");
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawFace(DrawContext context, UUID uuid, int x, int y, int size) {
        Identifier skin = skinFor(uuid);
        context.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        RenderSystem.enableBlend();
        context.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private Identifier skinFor(UUID uuid) {
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) return entry.getSkinTexture();
        }
        return DefaultSkinHelper.getTexture(uuid);
    }

    private String nameFor(UUID uuid) {
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null && entry.getProfile().getName() != null) {
                return entry.getProfile().getName();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    private static int secondsSetting(String key, int fallback) {
        try {
            return SettingsManager.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
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
