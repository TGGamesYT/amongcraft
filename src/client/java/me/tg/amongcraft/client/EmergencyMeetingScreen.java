package me.tg.amongcraft.client;


import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.tg.amongcraft.AmongCraftCommands;
import me.tg.amongcraft.AmongMapManager;
import me.tg.amongcraft.Amongcraft;
import me.tg.amongcraft.SettingsManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;



public class EmergencyMeetingScreen extends Screen {
    private UUID caller;
    Set<UUID> allPlayers = new HashSet<>();

    public enum Phase { DISCUSSION, VOTING, REVEAL }
    private TextFieldWidget chatInput;
    public static List<String> chatMessages = new ArrayList<>();
    private UUID selectedPlayer = null;
    private ButtonWidget voteButton;
    private ButtonWidget skipButton;


    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Map<String, Identifier> loadedHeads = new HashMap<>();
    private final Set<String> loadingTextures = new HashSet<>();
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Map<UUID, Integer> voteCounts = new HashMap<>();

    public Phase phase = Phase.DISCUSSION;
    private int timerTicks;

    private boolean anonymousVotes;
    private UUID self;

    private static final int PLAYER_ROW_HEIGHT = 32;
    private static final int PLAYER_ICON_SIZE = 20;
    private static final int VISIBLE_ROWS = 6;

    private int scrollOffset = 0;
    private UUID votedFor = null;
    private static final int GREY_COLOR = 0xFF777777;
    private static final int WHITE_COLOR = 0xFFFFFFFF;
    double chatScale;
    private final Map<UUID, Identifier> faceTextures = new HashMap<>();
    private final Map<UUID, NativeImage> faceImages = new HashMap<>();

    private ServerPlayerEntity getPlayerFromUUID(MinecraftServer server, UUID uuid) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    public EmergencyMeetingScreen(UUID selfUuid) {
        super(Text.literal("Emergency Meeting"));
        this.self = selfUuid;

        this.anonymousVotes = SettingsManager.get("anonim-votes").getAsBoolean();
        int discussionSeconds = SettingsManager.get("meeting-discussion").getAsInt();
        this.timerTicks = discussionSeconds * 20;
    }

    @Override
    protected void init() {
        phase = Phase.DISCUSSION;
        super.init();
        int buttonY = height - 30;
        chatInput = new TextFieldWidget(
                this.textRenderer,
                this.width / 2 - 100,
                this.height - 30,
                200,
                20,
                Text.literal("Chat")
        );
        chatInput.setMaxLength(256);
        chatInput.setEditable(true);
        chatInput.setFocused(false);
        this.addSelectableChild(chatInput);
        this.addDrawableChild(chatInput);
        double chatScale = MinecraftClient.getInstance().inGameHud.getChatHud().getChatScale();
        Amongcraft.LOGGER.info("Chat Scale saved: " + chatScale);
        MinecraftClient.getInstance().options.getChatScale().setValue(0.0);
        voteButton = ButtonWidget.builder(Text.literal("Vote"), btn -> {
            if (selectedPlayer != null) {
                vote(selectedPlayer);
            }
        }).dimensions(width - 110, 100, 100, 20).build();

        skipButton = ButtonWidget.builder(Text.literal("Skip Vote"), btn -> {
            vote(null);
        }).dimensions(width - 110, 130, 100, 20).build();
    }

    public void onMeetingStarted(UUID caller, Set<UUID> alivePlayers) {
        this.caller = caller;
        this.allPlayers = alivePlayers;
        Amongcraft.LOGGER.info("on meeting started, setting alive players: " + alivePlayers);
    }



    @Override
    public void tick() {
        if (--timerTicks <= 0) {
            switch (phase) {
                case DISCUSSION -> {
                    timerTicks = SettingsManager.get("meeting-voting").getAsInt() * 20;
                    phase = Phase.VOTING;
                }
                case VOTING -> {
                    phase = Phase.REVEAL;
                    countVotes();
                    timerTicks = 100;
                }
                case REVEAL -> {
                    finishVote();
                    client.setScreen(null);
                }
            }
        }
        if (phase == Phase.VOTING) {
            if (!this.children().contains(voteButton)) {
                addDrawableChild(voteButton);
                addDrawableChild(skipButton);
            }
        } else {
            this.remove(voteButton);
            this.remove(skipButton);
        }
    }

    public void setPhase(Phase newPhase, int secondsRemaining) {
        this.selectedPlayer = null;
        this.phase = newPhase;
        this.timerTicks = secondsRemaining * 20;
    }

    private void countVotes() {
        voteCounts.clear();
        for (UUID target : votes.values()) {
            if (target != null) voteCounts.merge(target, 1, Integer::sum);
        }
    }

    private void finishVote() {
        MinecraftClient.getInstance().options.getChatScale().setValue(1.0);
        int maxVotes = voteCounts.values().stream().max(Integer::compare).orElse(0);
        List<UUID> top = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() == maxVotes) {
                top.add(entry.getKey());
            }
        }
        if (top.size() == 1) {
            ServerPlayerEntity target = getPlayerFromUUID(client.getServer(), top.get(0));
            if (target != null) {
                AmongCraftCommands.setPlayerDead(target);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(phase.name()), width / 2, 15, 0xFFFFFF);

        if (phase == Phase.VOTING) {
            renderVotingPhase(context, mouseX, mouseY);
        } else if (phase == Phase.REVEAL) {
            renderRevealPhase(context);
        }

        super.render(context, mouseX, mouseY, delta);
        chatInput.render(context, mouseX, mouseY, delta);

        int start = Math.max(0, chatMessages.size() - 10);
        List<String> visibleMessages = chatMessages.subList(start, chatMessages.size());

        for (int i = 0; i < visibleMessages.size(); i++) {
            context.drawText(textRenderer, visibleMessages.get(i), 10, 10 + i * 10, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (chatInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (chatInput.isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message);
                chatInput.setText("");
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return chatInput.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void renderVotingPhase(DrawContext context, int mouseX, int mouseY) {
        int listStartY = 50;
        int row = 0;

        List<UUID> playerList = new ArrayList<>(allPlayers);
        List<UUID> playersToRender = playerList.subList(
                Math.min(scrollOffset, playerList.size()),
                Math.min(scrollOffset + VISIBLE_ROWS, playerList.size())
        );

        // Always white color for text (no greyness)
        int textColor = WHITE_COLOR;

        for (UUID uuid : playersToRender) {
            int y = listStartY + row * PLAYER_ROW_HEIGHT;
            int x = width / 2 - 100;

            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry == null) continue;

            String name = entry.getProfile().getName();
            Identifier faceTexture = faceTextures.get(uuid);

            if (faceTexture == null) {
                try {
                    NativeImage faceImg = PlayerFaceFetcher.getFace(uuid, name, false);
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(faceImg);
                    faceTexture = client.getTextureManager().registerDynamicTexture("face_" + uuid, tex);
                    faceTextures.put(uuid, faceTexture);
                    faceImages.put(uuid, faceImg);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue; // Skip rendering this player on failure
                }
            }

            // Draw player face
            context.drawTexture(faceTexture, x, y, 0, 0, 16, 16);

            // Draw player name
            context.drawText(client.textRenderer, name, x + PLAYER_ICON_SIZE + 5, y + 6, textColor, false);

            if (mouseHovered(mouseX, mouseY, x, y, 200, PLAYER_ROW_HEIGHT)) {
                if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    selectedPlayer = uuid;
                }
            }
            if (uuid.equals(selectedPlayer)) {
                context.fill(x - 2, y - 2, x + 200 + 2, y + PLAYER_ROW_HEIGHT + 2, 0xAAFFFFFF);
            }

            row++;
        }


        // Scroll buttons always white and clickable during voting
        int scrollX = width - 40;
        if (allPlayers.size() > VISIBLE_ROWS) {
            if (scrollOffset > 0) {
                context.drawText(client.textRenderer, "▲", scrollX, listStartY, WHITE_COLOR, false);
                if (mouseHovered(mouseX, mouseY, scrollX, listStartY, 20, 20) && phase == Phase.VOTING && client.mouse.wasLeftButtonClicked()) {
                    scrollOffset--;
                }
            }
            if (scrollOffset + VISIBLE_ROWS < allPlayers.size()) {
                context.drawText(client.textRenderer, "▼", scrollX, listStartY + VISIBLE_ROWS * PLAYER_ROW_HEIGHT - 10, WHITE_COLOR, false);
                if (mouseHovered(mouseX, mouseY, scrollX, listStartY + VISIBLE_ROWS * PLAYER_ROW_HEIGHT - 10, 20, 20) && phase == Phase.VOTING && client.mouse.wasLeftButtonClicked()) {
                    scrollOffset++;
                }
            }
        }
    }



    private void vote(@Nullable UUID target) {
        votedFor = target;

        if (client.player != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeUuid(target != null ? target : Util.NIL_UUID);
            ClientPlayNetworking.send(new Identifier("amongcraft", "vote"), buf);
            Amongcraft.LOGGER.info("Player voted:" + target);
        }
    }


    private void renderRevealPhase(DrawContext context) {
        int x = width / 2 - allPlayers.size() * 24 / 2;
        int y = 50;
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            UUID target = entry.getValue();
            if (target == null) continue;
            Identifier face = anonymousVotes ? getPlayerHeadTexture("steve") : getPlayerHeadTexture(entry.getKey().toString().toLowerCase());
            context.drawTexture(face, x, y, 20, 20, 20, 20);
            x += 24;
        }
    }

    private boolean mouseHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }


    private Identifier getPlayerHeadTexture(String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier identifier = new Identifier("amongcraft", "head/" + playerName);

        if (loadedHeads.containsKey(playerName)) {
            return loadedHeads.get(playerName);
        }

        if (!loadingTextures.contains(playerName)) {
            loadingTextures.add(playerName);

            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)),
                    playerName
            );

            client.getSkinProvider().loadSkin(profile, (type, textureId, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN && textureId != null) {
                    client.execute(() -> {
                        NativeImageBackedTexture tex = (NativeImageBackedTexture) client.getTextureManager().getTexture(textureId);
                        if (tex == null) {
                            loadingTextures.remove(playerName);
                            return;
                        }

                        NativeImage original = tex.getImage();
                        if (original == null) {
                            loadingTextures.remove(playerName);
                            return;
                        }

                        int size = 32;
                        NativeImage face = new NativeImage(size, size, true);

                        // Face layer
                        for (int y = 0; y < size; y++) {
                            for (int x = 0; x < size; x++) {
                                int srcX = x * 8 / size + 8;
                                int srcY = y * 8 / size + 8;
                                face.setColor(x, y, original.getColor(srcX, srcY));
                            }
                        }

                        // Hat layer
                        for (int y = 0; y < size; y++) {
                            for (int x = 0; x < size; x++) {
                                int srcX = x * 8 / size + 40;
                                int srcY = y * 8 / size + 8;
                                int hatColor = original.getColor(srcX, srcY);
                                if ((hatColor >> 24) != 0) {
                                    face.setColor(x, y, hatColor);
                                }
                            }
                        }

                        NativeImageBackedTexture headTexture = new NativeImageBackedTexture(face);
                        client.getTextureManager().registerTexture(identifier, headTexture);
                        loadedHeads.put(playerName, identifier);
                        loadingTextures.remove(playerName);
                    });
                } else {
                    client.execute(() -> loadingTextures.remove(playerName));
                }
            }, true);
        }

        return identifier;
    }



}
