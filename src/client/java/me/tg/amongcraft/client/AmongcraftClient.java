package me.tg.amongcraft.client;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.systems.RenderSystem;
import me.tg.amongcraft.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.texture.PlayerSkinTexture;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import java.util.Map;
import java.util.function.Consumer;

import static me.tg.amongcraft.Amongcraft.*;
import static me.tg.amongcraft.client.ClientCutscenePlayer.playCutscene;



public class AmongcraftClient implements ClientModInitializer {

    private static boolean registered = false;
    private static GLFWMouseButtonCallback prevCallback = null;
    private boolean localpeutron = false;

    @Override
    public void onInitializeClient() {
        Amongcraft.LOGGER.info("AmongcraftClient initializing...");
        ClientPlayNetworking.registerGlobalReceiver(AmongCraftPackets.OPEN_SCREEN, (client, handler, buf, responseSender) -> {
            String screenName = buf.readString();
            String settingsJson = buf.readString();
            buf.readString(); // ignore third arg

            if ("settings".equals(screenName)) {
                client.execute(() -> {
                    JsonObject settings = JsonParser.parseString(settingsJson).getAsJsonObject();
                    MinecraftClient.getInstance().setScreen(new SettingsScreen(settings));
                });
            }
        });
        EntityRendererRegistry.register(Amongcraft.CORPSE_ENTITY_TYPE, DeadBodyEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.OPEN_BROWSER_PACKET_ID, (client, handler, buf, responseSender) -> {
            String url = buf.readString();
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                Screen browser = new TaskBrowserScreen(url, pos);
                MinecraftClient.getInstance().setScreen(browser);
                Amongcraft.LOGGER.info("Recieved Packet!");
            });
        });
        TaskDoneC2SPacket.register();
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.MEETING_PHASE_PACKET, (client, handler, buf, responseSender) -> {
            EmergencyMeetingScreen.Phase phase = buf.readEnumConstant(EmergencyMeetingScreen.Phase.class);
            int secondsLeft = buf.readInt();

            client.execute(() -> {
                if (client.currentScreen instanceof EmergencyMeetingScreen screen) {
                    screen.setPhase(phase, secondsLeft);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.END_MEETING_PACKET, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                if (client.currentScreen instanceof EmergencyMeetingScreen) {
                    client.setScreen(null); // Close the meeting UI
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.VOTE_END_PACKET, (client, handler, buf, responseSender) -> {
            Map<UUID, Set<UUID>> fullVotes = new HashMap<>();

            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                UUID voter = buf.readUuid();
                boolean hasVoted = buf.readBoolean();
                if (hasVoted) {
                    UUID votedFor = buf.readUuid();
                    fullVotes.computeIfAbsent(votedFor, k -> new HashSet<>()).add(voter);
                }
            }

            UUID eliminated;
            boolean wasImpostor;
            if (buf.readBoolean()) {
                eliminated = buf.readUuid();
                wasImpostor = buf.readBoolean();
            } else {
                wasImpostor = false;
                eliminated = null;
            }

            client.execute(() -> {
                // Create a new voteEnd screen with a title
                voteEnd screen = new voteEnd(Text.of("Vote Results"));

                // Call your non-static method
                screen.endVote(fullVotes, eliminated, wasImpostor);

                // Set this screen as the current screen to display it
                client.setScreen(screen);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(TABLET_SYNC_PACKET_ID, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            Map<UUID, Boolean> playerStatus = new HashMap<>();
            for (int i = 0; i < count; i++) {
                UUID uuid = buf.readUuid();
                boolean isAlive = buf.readBoolean();
                playerStatus.put(uuid, isAlive);
            }

            client.execute(() -> {
                MinecraftClient.getInstance().setScreen(new TabletScreen(playerStatus));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.SHIFTER_PACKET, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            Map<UUID, Boolean> playerStatus = new HashMap<>();
            for (int i = 0; i < count; i++) {
                UUID uuid = buf.readUuid();
                boolean isAlive = buf.readBoolean();
                playerStatus.put(uuid, isAlive);
            }

            client.execute(() -> {
                MinecraftClient.getInstance().setScreen(new ShifterScreen(playerStatus));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.SHIFTER_DISGUISE_PACKET, (client, handler, buf, sender) -> {
            UUID disguisedUuid = buf.readUuid();
            String fakeName = buf.readString();

            int propCount = buf.readInt();
            GameProfile newProfile = new GameProfile(disguisedUuid, fakeName);

            for (int i = 0; i < propCount; i++) {
                String name = buf.readString();
                String value = buf.readString();
                String sig = buf.readString();
                newProfile.getProperties().put(name, new Property(name, value, sig));
            }

            client.execute(() -> {
                PlayerListEntry entry = new PlayerListEntry(newProfile, false);
                MinecraftClient.getInstance().getNetworkHandler().getPlayerList().removeIf(p -> p.getProfile().getId().equals(disguisedUuid));
                MinecraftClient.getInstance().getNetworkHandler().getPlayerList().add(entry);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "open_sabotage_gui"), (client, handler, buf, responseSender) -> {
            client.execute(() -> client.setScreen(new SabotageScreenClient()));
        });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "door_list_response"),
                (client, handler, buf, responseSender) -> {
                    int count = buf.readInt();
                    List<String> doors = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        doors.add(buf.readString());
                    }
                    client.execute(() -> {
                        if (client.currentScreen instanceof SabotageScreenClient screen) {
                            screen.receiveDoorList(doors);
                        }
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(me.tg.amongcraft.SabotageTaskBlock.OPEN_GUI_PACKET,
                (client, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    String task = buf.readString();
                    String map = buf.readString();
                    boolean[] levers = new boolean[5];
                    for (int i = 0; i < 5; i++) {
                        levers[i] = buf.readBoolean();
                    }
                    LOGGER.info("client side recieved:" + pos + task + map);

                    client.execute(() -> {
                        Screen screen = switch (task) {
                            case "reactor-1", "reactor-2" -> new ReactorScreen(pos, task);
                            case "o2-1", "o2-2" -> new O2Screen(pos, task);
                            case "lights" -> new LightsScreen(pos, levers);
                            case "comms" -> new CommsScreen(pos);
                            default -> null;
                        };

                        if (screen != null) {
                            MinecraftClient.getInstance().setScreen(screen);
                        }
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(SERVER_SABOTAGE_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            String task = buf.readString();
            int index = buf.readInt();
            boolean state = buf.readBoolean();

            client.execute(() -> {
                LightsScreen.applyLeverUpdate(index, state);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.OPEN_MONITOR_SCREEN, (client, handler, buf, responseSender) -> {
            String identifier = buf.readString();
            boolean type = buf.readBoolean();
            int maxCams = buf.readInt();
            BlockPos pos = buf.readBlockPos();

            client.execute(() -> {
                MinecraftClient.getInstance().setScreen(new MonitorCameraScreen(identifier, type, maxCams, pos));
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CameraRenderManager.getInstance().tick();
        });

        ClientPlayNetworking.registerGlobalReceiver(CameraSystem.CAMERA_PACKET_ID, (client, handler, buf, responseSender) -> {
            int monitorCount = buf.readInt();
            List<CameraSystem.MonitorData> monitors = new ArrayList<>();

            for (int i = 0; i < monitorCount; i++) {
                BlockPos pos = buf.readBlockPos();
                boolean type = buf.readBoolean();
                int maxCams = buf.readInt();
                int selectedCamIndex = buf.readInt();

                int camCount = buf.readInt();
                List<CameraSystem.CameraData> cameras = new ArrayList<>();

                for (int j = 0; j < camCount; j++) {
                    String identifier = buf.readString();
                    int num = buf.readInt();
                    BlockPos camPos = buf.readBlockPos();
                    CameraRenderManager.getInstance().registerCamera(identifier, num, camPos);
                    CameraSystem.CameraData data = new CameraSystem.CameraData();
                    data.identifier = identifier;
                    data.num = num;
                    data.pos = camPos;
                    cameras.add(data);
                }
                CameraSystem.MonitorData mon = new CameraSystem.MonitorData();
                mon.pos = pos;
                mon.type = type;
                mon.maxCams = maxCams;
                mon.selectedCamIndex = selectedCamIndex;
                mon.cameras = cameras.toArray(new CameraSystem.CameraData[0]);
                monitors.add(mon);
            }

            client.execute(() -> {
                CameraRenderManager.getInstance().updateMonitors(monitors);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientCutscenePlayer.CUTSCENE_PACKET, (client, handler, buf, responseSender) -> {
            String cutsceneId = buf.readString();
            int count = buf.readInt();
            List<UUID> actorUuids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                actorUuids.add(buf.readUuid());
            }
            client.execute(() -> playCutscene(cutsceneId, actorUuids));
        });
        ClientPlayNetworking.registerGlobalReceiver(Amongcraft.OPEN_ORDER_EDITOR, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> {
                ItemStack held = client.player.getMainHandStack();
                client.setScreen(new TaskEditorScreen(pos, held));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CUTSCENE_PACKET_ID, (client, handler, buf, responseSender) -> {
            Identifier background = buf.readIdentifier();
            Identifier sound = buf.readIdentifier();
            boolean fading = buf.readBoolean();

            int size = buf.readVarInt();
            Set<UUID> players = new HashSet<>();
            for (int i = 0; i < size; i++) {
                players.add(buf.readUuid());
            }

            boolean redColor = buf.readBoolean();

            client.execute(() -> client.setScreen(
                    new CutsceneScreen(background, sound, fading, players.isEmpty() ? null : players, redColor)
            ));
        });
        ClientPlayNetworking.registerGlobalReceiver(SHAPESHIFT_PACKET, (client, handler, buf, sender) -> {
            UUID playerUUID = buf.readUuid();
            boolean isShapeshift = buf.readBoolean();
            String textureValue = isShapeshift ? buf.readString() : null;
            String textureSignature = isShapeshift ? buf.readString() : null;
            String disguisedName = isShapeshift ? buf.readString() : null;  // Read disguised name if shapeshift

            client.execute(() -> {
                if (client.world == null) return;
                var player = client.world.getPlayerByUuid(playerUUID);
                if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

                if (isShapeshift) {
                    // Construct temporary profile with new skin
                    GameProfile tempProfile = new GameProfile(playerUUID, clientPlayer.getGameProfile().getName());
                    tempProfile.getProperties().put("textures", new Property("textures", textureValue, textureSignature));

                    // Load and cache the skin
                    client.getSkinProvider().loadSkin(tempProfile, (textureType, textureId, profileTexture) -> {
                        System.out.println("[DisguiseReceiver] Texture loaded: type=" + textureType + ", id=" + textureId);

                        if (textureType == MinecraftProfileTexture.Type.SKIN) {
                            DisguiseSkinCache.setCustomSkinFor(playerUUID, textureId);
                            DisguiseSkinCache.setDisguisedName(playerUUID, disguisedName);  // Save disguised name here
                            System.out.println("[DisguiseReceiver] Stored disguised skin and name for " + playerUUID);
                        } else {
                            System.out.println("[DisguiseReceiver] Ignored texture type: " + textureType);
                        }
                    }, true);
                } else {
                    // Revert disguise
                    DisguiseSkinCache.setCustomSkinFor(playerUUID, null);
                    DisguiseSkinCache.setDisguisedName(playerUUID, null);  // Clear disguised name
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!registered && client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();

                prevCallback = GLFW.glfwSetMouseButtonCallback(windowHandle, new GLFWMouseButtonCallback() {
                    @Override
                    public void invoke(long window, int button, int action, int mods) {
                        MinecraftClient mc = MinecraftClient.getInstance();

                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
                            if (mc.player != null &&
                                    mc.player.isSpectator() &&
                                    mc.currentScreen == null &&
                                    mc.crosshairTarget instanceof EntityHitResult entityHitResult &&
                                    entityHitResult.getEntity() instanceof PlayerEntity targetPlayer) {

                                UUID uuid = targetPlayer.getUuid();
                                PacketByteBuf buf = PacketByteBufs.create();
                                buf.writeUuid(uuid);
                                ClientPlayNetworking.send(PROTECT_PACKET_ID, buf);
                            }
                        }

                        if (prevCallback != null) {
                            prevCallback.invoke(window, button, action, mods);
                        }
                    }
                });

                registered = true;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!peutron) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null || client.player == null) return;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            Identifier texture = new Identifier("amongcraft", "textures/gui/peutron.png");

            int textureWidth = 1139;
            int textureHeight = 340;

            // Target display width (25% of screen), preserving aspect ratio
            int targetWidth = screenWidth / 4;
            int targetHeight = (int) ((float) targetWidth * textureHeight / textureWidth);

            int x = screenWidth - targetWidth - 10;
            int y = 10;

            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1f, 1f, 1f, 0.6f); // 60% opacity

            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(x, y, 0);
            float scaleX = (float) targetWidth / textureWidth;
            float scaleY = (float) targetHeight / textureHeight;
            drawContext.getMatrices().scale(scaleX, scaleY, 1f);

            drawContext.drawTexture(texture, 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);

            drawContext.getMatrices().pop();

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // Reset color
        });

        ClientPlayNetworking.registerGlobalReceiver(PEUTRON_PACKET, (client, handler, buf, sender) -> {
            localpeutron = buf.readBoolean();
        });
    }

    public static void loadSkinAsync(UUID playerUUID, Consumer<Identifier> callback) {
        new Thread(() -> {
            try {
                // 1. Download profile
                String uuid = playerUUID.toString().replace("-", "");
                URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                InputStreamReader reader = new InputStreamReader(url.openStream());

                JsonObject profile = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray properties = profile.getAsJsonArray("properties");

                String encoded = null;

                for (JsonElement element : properties) {
                    JsonObject propObj = element.getAsJsonObject();
                    if ("textures".equals(propObj.get("name").getAsString())) {
                        encoded = propObj.get("value").getAsString();
                        break;
                    }
                }

                if (encoded == null) throw new RuntimeException("No textures found");

                // 2. Decode and get skin URL
                String decoded = new String(Base64.getDecoder().decode(encoded));
                JsonObject texturesJson = JsonParser.parseString(decoded).getAsJsonObject();
                JsonObject textures = texturesJson.getAsJsonObject("textures");

                String skinUrl = textures.getAsJsonObject("SKIN").get("url").getAsString();

                // 3. Download and register skin
                NativeImage image = NativeImage.read(new URL(skinUrl).openStream());
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);

                Identifier skinId = MinecraftClient.getInstance()
                        .getTextureManager()
                        .registerDynamicTexture("player_skin/" + uuid, texture);

                callback.accept(skinId);
            } catch (Exception e) {
                e.printStackTrace();

                Identifier defaultSkin = new Identifier("amongcraft", "textures/entity/defaultskin.png");
                callback.accept(defaultSkin);
            }
        }).start();
    }




    public static class SettingsScreen extends Screen {

        private int scrollY = 0;
        private int totalContentHeight = 0;
        private final JsonObject settingsJson;
        private final Map<String, TextFieldWidget> fields = new HashMap<>();
        public static final Identifier SAVE_SETTINGS = new Identifier("amongcraft", "save_settings");

        protected SettingsScreen(JsonObject settingsJson) {
            super(Text.literal("AmongCraft Settings"));
            this.settingsJson = settingsJson.deepCopy();
        }

        @Override
        protected void init() {
            this.fields.clear();
            this.children().removeIf(child -> child instanceof TextFieldWidget);

            // Save button on the left top
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
                onSave();
            }).dimensions(this.width / 2 - 110, 20, 100, 20).build());

            // Cancel button on the right top
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
                this.client.setScreen(null);
            }).dimensions(this.width / 2 + 10, 20, 100, 20).build());
        }

        private void renderJsonSection(JsonObject obj, String path, int depth, int[] y, DrawContext context, boolean createFields) {
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            VertexConsumerProvider vertexConsumers = context.getVertexConsumers();

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();

                if (value.isJsonObject()) {
                    int color = 0xFFFFFF - (depth * 0x111111);

                    String title = switch (depth) {
                        case 1 -> "§l" + key;
                        case 2 -> "§n" + key;
                        default -> key;
                    };

                    int yPos = y[0] - scrollY;
                    if (yPos + 20 >= 0 && yPos <= this.height) {
                        textRenderer.draw(
                                title, 20, yPos, color, true, matrix,
                                vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0
                        );
                    }
                    y[0] += 20;

                    renderJsonSection(value.getAsJsonObject(), path + key + ".", depth + 1, y, context, createFields);
                    continue;
                }

                if (key.startsWith("_")) continue;

                int yPos = y[0] - scrollY;

                int labelWidth = 150;
                int fieldWidth = 200;
                int fieldX = 20 + labelWidth + 10;

                if (createFields) {
                    TextFieldWidget textField = new TextFieldWidget(this.textRenderer, fieldX, yPos, fieldWidth, 20, Text.literal(key));
                    textField.setText(jsonElementToString(value));
                    this.addSelectableChild(textField);
                    this.addDrawableChild(textField);
                    fields.put(path + key, textField);
                } else {
                    TextFieldWidget textField = fields.get(path + key);
                    if (textField != null) {
                        textField.setY(yPos);
                        textField.setX(fieldX);
                    }
                }


                y[0] += 25;

                // Show comment if available
                String commentKey = "_" + key;
                if (obj.has(commentKey)) {
                    String comment = jsonElementToString(obj.get(commentKey));
                    int commentY = y[0] - scrollY;
                    if (commentY + 15 >= 0 && commentY <= this.height) {
                        textRenderer.draw(
                                comment, 25, commentY, 0x888888, true, matrix,
                                vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0
                        );
                    }
                    y[0] += 15;
                }
            }
        }


        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            scrollY -= amount * 20;
            scrollY = Math.max(0, Math.min(scrollY, Math.max(0, totalContentHeight - this.height + 20)));
            return true;
        }


        private String jsonElementToString(JsonElement element) {
            if (element.isJsonPrimitive()) {
                JsonPrimitive prim = element.getAsJsonPrimitive();
                if (prim.isString()) return prim.getAsString();
                else if (prim.isNumber()) return prim.getAsNumber().toString();
                else if (prim.isBoolean()) return Boolean.toString(prim.getAsBoolean());
            }
            return element.toString();
        }

        private JsonElement stringToJsonElement(String str) {
            try {
                if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                    return new JsonPrimitive(Boolean.parseBoolean(str));
                }
                try {
                    if (str.contains(".")) {
                        return new JsonPrimitive(Double.parseDouble(str));
                    } else {
                        return new JsonPrimitive(Integer.parseInt(str));
                    }
                } catch (NumberFormatException ignored) {}
                try {
                    return JsonParser.parseString(str);
                } catch (Exception ignored) {}
                return new JsonPrimitive(str);
            } catch (Exception e) {
                return new JsonPrimitive(str);
            }
        }

        public static void addNestedKey(JsonObject root, String dottedKey, JsonElement value) {
            String[] parts = dottedKey.split("\\.");
            JsonObject current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (!current.has(part) || !current.get(part).isJsonObject()) {
                    current.add(part, new JsonObject());
                }
                current = current.getAsJsonObject(part);
            }
            current.add(parts[parts.length - 1], value);
        }

        private void onSave() {
            // Start with a deep copy of the original JSON to preserve comments and untouched values
            JsonObject newSettings = settingsJson.deepCopy();

            for (String key : fields.keySet()) {
                String text = fields.get(key).getText();
                JsonElement parsed = stringToJsonElement(text);
                addNestedKey(newSettings, key, parsed);
            }

            System.out.println("New Settings JSON: " + newSettings.toString());
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeString(newSettings.toString());
            ClientPlayNetworking.send(SAVE_SETTINGS, buf);

            this.client.setScreen(null);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

            // Calculate layout and create widgets only once on first render or init
            if (fields.isEmpty()) {
                int[] y = new int[] {30};
                renderJsonSection(this.settingsJson, "", 1, y, context, true);
                totalContentHeight = y[0];
            } else {
                int[] y = new int[] {30};
                renderJsonSection(this.settingsJson, "", 1, y, context, false);
                totalContentHeight = y[0];
            }

            super.render(context, mouseX, mouseY, delta);

            // Draw field labels next to fields
            for (Map.Entry<String, TextFieldWidget> entry : fields.entrySet()) {
                String key = entry.getKey();
                TextFieldWidget field = entry.getValue();

                int labelX = 20;
                int labelY = field.getY() + 6;
                if (labelY >= 0 && labelY <= this.height) {
                    // Get only last segment after last dot
                    String shortKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                    context.drawTextWithShadow(this.textRenderer, shortKey, labelX, labelY, 0xAAAAAA);
                }

            }
        }


        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    public class TaskDoneC2SPacket {
        public static void send(String taskId, BlockPos pos) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(taskId);
            buf.writeBlockPos(pos);
            LOGGER.info("sending task packet done: " + taskId + "@" + pos.toString());
            ClientPlayNetworking.send(TASKDONE_ID, buf);
        }

        public static void register() {
            ClientPlayNetworking.registerGlobalReceiver(Amongcraft.MEETING_PACKET, (client, handler, buf, responseSender) -> {
                try {
                    Amongcraft.LOGGER.info("RECIEVED MEETING_PACKET (LOGGER)");
                    System.out.println("RECIEVED MEETING_PACKET (println)");
                    UUID callerUuid = buf.readUuid();

                    int aliveCount = buf.readInt();
                    Set<UUID> alivePlayers = new HashSet<>();
                    for (int i = 0; i < aliveCount; i++) {
                        alivePlayers.add(buf.readUuid());
                    }
                    Amongcraft.LOGGER.info("buffer: caller UUID: " + callerUuid + " and alive Players: " + alivePlayers);

                    client.execute(() -> {
                        MinecraftClient.getInstance().setScreen(new EmergencyMeetingScreen(callerUuid));
                        // Example: use the UUIDs
                        System.out.println("Meeting called by: " + callerUuid);
                        System.out.println("Alive players: " + alivePlayers);

                        // Pass data to your EmergencyMeetingScreen or store it as needed
                        Amongcraft.LOGGER.info("is the current screen emergencymeetingscreen?");
                        if (client.currentScreen instanceof EmergencyMeetingScreen screen) {
                            Amongcraft.LOGGER.info("YES!");
                            screen.onMeetingStarted(callerUuid, alivePlayers);
                        } else {
                            Amongcraft.LOGGER.info("NO..");
                        }
                    });
                } catch (Exception e) {
                    Amongcraft.LOGGER.error("Error handling MEETING_PACKET", e);
                }
            });

        }
    }
}
