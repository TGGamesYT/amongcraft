package me.tg.amongcraft;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.Unpooled;
import me.tg.amongcraft.mixin.PlayerListS2CPacketAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.state.property.Properties;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static me.tg.amongcraft.AmongCraftCommands.getPlayerRole;
import static me.tg.amongcraft.AmongCraftCommands.impostors;
import static me.tg.amongcraft.MeetingManager.handleVote;
import static me.tg.amongcraft.SabotageTaskBlock.generateRandomLightState;
import static me.tg.amongcraft.TaskPacket.sendTaskOpenPacket;
import static me.tg.amongcraft.TaskProgressTracker.*;


public class Amongcraft implements ModInitializer {

        public static final String MODID = "amongcraft";

        // BLOCKS
        public static final Block START_BUTTON_BLOCK = new StartBlock();
        public static final Block TASK_BUTTON_BLOCK = new TaskBlock();
        public static final Block WENT_BLOCK = new WentBlock();

        // ITEMS
        public static final Item KILLER_KNIFE = new KillerKnifeItem();
    public static final Item SHERIFF_GUN = new GunItem();
        public static final Item WENT_LINKER = new WentLinkerItem();
        public static final Item USE = new TaskTriggerItem(new FabricItemSettings().maxCount(1));
    public static final Item REPORT_ITEM = new GenericItem(new FabricItemSettings().maxCount(1));
    public static final Item TABLET_ITEM = new TabletItem(new FabricItemSettings().maxCount(1));
    public static final Item PHANTOM_ITEM = new PhantherItem(new FabricItemSettings().maxCount(1));
    public static final Item SHAPESHIFTER = new ShifterItem(new FabricItemSettings().maxCount(1));
    public static final Item TASKORDERITEM = new TaskOrdererItem(new FabricItemSettings().maxCount(1));
    public static final Block DOOR_BLOCK = new AmongcraftDoorBlock(AbstractBlock.Settings.create().noCollision().nonOpaque().strength(0.0f));
    public static final Block SABOTAGE_TASK = new SabotageTaskBlock(
            Block.Settings.create().strength(1.5f).nonOpaque()
    );
    public static final ItemGroup AMONGCRAFT_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(Amongcraft.WENT_BLOCK)) // Use one of your items as the icon
            .displayName(Text.translatable("itemGroup.amongcraft"))
            .entries((context, entries) -> {
                entries.add(Amongcraft.EMERGENCY_BUTTON);
                entries.add(Amongcraft.KILLER_KNIFE);
                entries.add(AmongMapManager.LOBBY_SPAWN_BLOCK);
                entries.add(AmongMapManager.MAP_SPAWN_BLOCK);
                entries.add(Amongcraft.REPORT_ITEM);
                entries.add(SabotageManager.SABOTAGE_ITEM);
                entries.add(Amongcraft.SETTINGS_ITEM);
                entries.add(Amongcraft.START_BUTTON_BLOCK);
                entries.add(Amongcraft.SWITCH_WENT);
                entries.add(Amongcraft.TASK_BUTTON_BLOCK);
                entries.add(Amongcraft.USE);
                entries.add(Amongcraft.WENT_BLOCK);
                entries.add(Amongcraft.WENT_LINKER);
                entries.add(Amongcraft.TABLET_ITEM);
                entries.add(Amongcraft.PHANTOM_ITEM);
                entries.add(Amongcraft.SHERIFF_GUN);
                entries.add(Amongcraft.SHAPESHIFTER);
                entries.add(DOOR_BLOCK);
                entries.add(SABOTAGE_TASK);
                entries.add(CameraSystem.CAMERA_BLOCK);
                entries.add(CameraSystem.MONITOR_BLOCK);
                entries.add(Amongcraft.TASKORDERITEM);
            })
            .build();
    public static final Identifier OPEN_BROWSER_PACKET_ID = new Identifier(MODID, "open_browser");

    public static final Item SWITCH_WENT = new SwitchWentItem();
        public static final Item SETTINGS_ITEM = new CommandItem("/amongcraft settings");
        public static boolean peutron = false;

        public static final Map<BlockPos, List<BlockPos>> wentLinks = new HashMap<>();
        public static final Map<UUID, BlockPos> selectedWent = new HashMap<>();
    private static final ConcurrentHashMap<String, Long> doorCooldowns = new ConcurrentHashMap<>();
    private static final List<ScheduledTask> tasks = new ArrayList<>();

    public static EntityType<DeadBodyEntity> CORPSE_ENTITY_TYPE;
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static final Identifier MEETING_PACKET = new Identifier("amongcraft", "open_meeting");
    public static final Identifier VOTE_END_PACKET = new Identifier("amongcraft", "vote_end");
    public static final Identifier MEETING_PHASE_PACKET = new Identifier("amongcraft", "meeting_phase");
    public static final Identifier END_MEETING_PACKET = new Identifier("amongcraft", "end_meeting");
    public static final Identifier TABLET_SYNC_PACKET_ID = new Identifier("amongcraft", "tablet_sync");
    public static final Identifier SHIFTER_PACKET = new Identifier("amongcraft", "shifter_packet");
    public static final Identifier SHIFTER_SELECT_PACKET = new Identifier("amongcraft", "shifter_select_packet");
    public static final Identifier SHIFTER_DISGUISE_PACKET = new Identifier("amongcraft", "shifter_disguise_packet");
    public static final Identifier CLIENT_SABOTAGE_PACKET = new Identifier("amongcraft", "sabotage_task_client_packet");
    public static final Identifier SERVER_SABOTAGE_SYNC_PACKET = new Identifier("amongcraft", "sabotage_lights_sync");
    public static final Identifier SABOTAGE_RESOLVE = new Identifier("amongcraft", "sabotage_resolve_try");
    public static final Identifier TASKDONE_ID = new Identifier("amongcraft", "task_done");
    public static final Identifier OPEN_MONITOR_SCREEN = new Identifier("amongcraft", "open_monitor");
    public static final Identifier OPEN_ORDER_EDITOR = new Identifier("amongcraft", "open_task_order_editor");
    public static final Identifier SAVE_TASK_ORDER = new Identifier("amongcraft", "save_task_order");
    public static final Identifier CUTSCENE_PACKET_ID = new Identifier("amongcraft", "cutscene");
    public static final Identifier SHAPESHIFT_PACKET = new Identifier("amongcraft", "shapeshift");
    public static final Identifier PROTECT_PACKET_ID = new Identifier("amongcraft", "angel_protect");
    public static final Identifier PEUTRON_PACKET = new Identifier("amongcraft", "peutron");

    public static final Map<UUID, Integer> meetingsCalled = new HashMap<>();
    public static long lastMeetingTime = 0;



    public static final Block EMERGENCY_BUTTON = new emergency();

    public static final Identifier CLICK_ID = new Identifier("amongcraft", "ui.click");
    public static final Identifier HOVER_ID = new Identifier("amongcraft", "ui.hover");

    public static SoundEvent CLICK = SoundEvent.of(CLICK_ID);
    public static SoundEvent HOVER = SoundEvent.of(HOVER_ID);


    private static Block register(String name, Block block) {
            return Registry.register(Registries.BLOCK, new Identifier(MODID, name), block);
        }
        private static Item register(String name, Item item) {
            return Registry.register(Registries.ITEM, new Identifier(MODID, name), item);
        }
        private static void register(String name, Block block, Item.Settings settings) {
            register(name, block);
            register(name, new BlockItem(block, settings));
        }

        @Override
        public void onInitialize() {
            register("start_button", START_BUTTON_BLOCK, new FabricItemSettings());
            register("task_button", TASK_BUTTON_BLOCK, new FabricItemSettings());
            register("went", WENT_BLOCK, new FabricItemSettings());
            register("map_spawn", AmongMapManager.MAP_SPAWN_BLOCK, new FabricItemSettings());
            register("lobby_spawn", AmongMapManager.LOBBY_SPAWN_BLOCK, new FabricItemSettings());
            register("emergency_button", EMERGENCY_BUTTON, new FabricItemSettings());
            register("door_block", DOOR_BLOCK, new FabricItemSettings());
            register("sabotage_task", SABOTAGE_TASK, new FabricItemSettings());
            register("camera", CameraSystem.CAMERA_BLOCK, new FabricItemSettings());
            register("monitor", CameraSystem.MONITOR_BLOCK, new FabricItemSettings());

            register("task_order", TASKORDERITEM);
            register("killer_knife", KILLER_KNIFE);
            register("use", USE);
            register("went_linker", WENT_LINKER);
            register("switch_went", SWITCH_WENT);
            register("settings", SETTINGS_ITEM);
            register("sabotage", SabotageManager.SABOTAGE_ITEM);
            register("report", REPORT_ITEM);
            register("tablet", TABLET_ITEM);
            register("phantom", PHANTOM_ITEM);
            register("gun", SHERIFF_GUN);
            register("shapeshifter", SHAPESHIFTER);
            Registry.register(Registries.ITEM_GROUP, new Identifier("amongcraft", "group"), AMONGCRAFT_GROUP);

            CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
                AmongCraftCommands.register(dispatcher);
            });


            AmongCraftWinListener.register();
            AmongCraftListeners.register();
            SettingsManager.load();
            AmongCraftPackets.register();
            AmongMapManager.register();
            TaskPacket.init();
            MeetingManager.register();
            DeathListener.register();
            CameraSystem.init();
            CORPSE_ENTITY_TYPE = Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("amongcraft", "dead_body"),
                    FabricEntityTypeBuilder.create(SpawnGroup.MISC, DeadBodyEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                            .trackRangeBlocks(8)
                            .trackedUpdateRate(10)
                            .build()
            );
            ServerPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "vote"), (server, player, handler, buf, responseSender) -> {
                UUID target = buf.readUuid();
                server.execute(() -> {
                    handleVote(player, target.equals(Util.NIL_UUID) ? null : target);
                    Amongcraft.LOGGER.info("Recieved vote serverside");
                });
            });
            ServerTickEvents.END_SERVER_TICK.register(MeetingManager::tick);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                TaskProgressTracker.tick();
                TickDelayedExecutor.onServerTick(server);
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (ServerWorld world : server.getWorlds()) {
                    SwitchWentItem.handlePlayerJumpOut(world);
                }
            });


            UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
                if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
                if (!(world instanceof ServerWorld)) return ActionResult.PASS;

                // Make sure player is holding the report item
                if (!player.getStackInHand(hand).isOf(REPORT_ITEM)) return ActionResult.PASS;

                // Check for entity type
                if (entity instanceof DeadBodyEntity) {
                    MeetingManager.callMeeting(serverPlayer, true);
                    return ActionResult.SUCCESS;
                }

                return ActionResult.PASS;
            });
            ServerLifecycleEvents.SERVER_STARTED.register(AmongMapManager::getfile);
            ServerPlayNetworking.registerGlobalReceiver(Amongcraft.SHIFTER_SELECT_PACKET, (server, player, handler, buf, sender) -> {
                UUID selectedUUID = buf.readUuid();

                server.execute(() -> {
                    ServerPlayerEntity toMimic = server.getPlayerManager().getPlayer(selectedUUID);
                    if (toMimic == null) return;

                    // Change profile info (name + skin)
                    disguisePlayer(player, toMimic);
                });
            });
            ServerPlayNetworking.registerGlobalReceiver(Amongcraft.PROTECT_PACKET_ID, (server, player, handler, buf, responseSender) -> {
                UUID targetUuid = buf.readUuid();

                server.execute(() -> {
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
                    if (target == null) {
                        return;
                    }

                    // Only allow Angel role
                    String role = AmongCraftCommands.getPlayerRole(player.getUuid());
                    if (!"Angel".equalsIgnoreCase(role)) {
                        player.sendMessage(Text.literal("§cOnly Angels can protect players."), true);
                        return;
                    }

                    // Only in proper game modes
                    if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) return;
                    if (target.interactionManager.getGameMode() != GameMode.SURVIVAL) return;

                    long now = System.currentTimeMillis();
                    long cooldownUntil = AngelProtectionManager.cooldownUntil.getOrDefault(player.getUuid(), 0L);

                    if (cooldownUntil > now) {
                        long secondsLeft = (cooldownUntil - now) / 1000;
                        player.sendMessage(Text.literal("§cYou are on cooldown for " + secondsLeft + " more seconds."), true);
                        return;
                    }

                    // Protection logic
                    int protectSeconds = SettingsManager.get("angel-protect-time").getAsInt();
                    int cooldownSeconds = SettingsManager.get("angel-protect-cooldown").getAsInt();

                    long protectUntil = now + protectSeconds * 1000L;
                    long cooldownTime = now + cooldownSeconds * 1000L;

                    AngelProtectionManager.protectedUntil.put(target.getUuid(), protectUntil);
                    AngelProtectionManager.cooldownUntil.put(player.getUuid(), cooldownTime);

                    target.sendMessage(Text.literal("§aYou are protected by an Angel!").formatted(Formatting.AQUA), false);
                    player.sendMessage(Text.literal("§eYou protected §a" + target.getName().getString() + "§e!"), false);
                });
            });
            Registry.register(Registries.SOUND_EVENT, CLICK_ID, CLICK);
            Registry.register(Registries.SOUND_EVENT, HOVER_ID, HOVER);
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                AmongcraftDoorBlock.server = server;
                TaskProgressTracker.server = server;
            });

            ServerPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "toggle_door"), (server, player, handler, buf, responseSender) -> {
                String id = buf.readString(); // Door ID
                UUID uuid = player.getUuid();

                server.execute(() -> {
                    // Only impostors can toggle doors
                    if (!impostors.contains(uuid)) {
                        player.sendMessage(Text.literal("§cYou do not have permission to toggle doors."), false);
                        return;
                    }

                    long currentTime = System.currentTimeMillis();
                    long lastClosedTime = doorCooldowns.getOrDefault(id, 0L);

                    if (currentTime - lastClosedTime < 2 * 60 * 1000L) { // 2 minute cooldown
                        long secondsLeft = ((2 * 60 * 1000L) - (currentTime - lastClosedTime)) / 1000;
                        player.sendMessage(Text.literal("§eThat door is still on cooldown for " + secondsLeft + " seconds."), false);
                        return;
                    }

                    // Set door to closed (false)
                    AmongcraftDoorBlock.setDoor(id, true);
                    doorCooldowns.put(id, currentTime);
                    player.sendMessage(Text.literal("§aDoor " + id + " toggled!"), false);

                    // Schedule reopening in 1 minute
                    schedule(20 * 60, () -> {
                        AmongcraftDoorBlock.setDoor(id, false);
                    });

                });
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                Iterator<ScheduledTask> iter = tasks.iterator();
                while (iter.hasNext()) {
                    ScheduledTask task = iter.next();
                    task.ticksLeft--;
                    if (task.ticksLeft <= 0) {
                        task.runnable.run();
                        iter.remove();
                    }
                }
            });
            ServerPlayNetworking.registerGlobalReceiver(CLIENT_SABOTAGE_PACKET, (server, player, handler, buf, responseSender) -> {
                BlockPos pos = buf.readBlockPos();
                String task = buf.readString();      // e.g., "lights"
                int index = buf.readInt();           // lever index 0-4
                boolean state = buf.readBoolean();   // true = on

                server.execute(() -> {
                    // Reject if sabotage is not active
                    if (!SabotageManager.isSabotaged(task)) {
                        Amongcraft.LOGGER.info("Sabotage '{}' is not active — ignoring lever change", task);
                        return;
                    }

                    World world = player.getWorld();
                    JsonObject data = BlockNBTStore.load(world, pos);
                    if (data == null) data = new JsonObject();

                    JsonObject lightState = data.has("light_state") ? data.getAsJsonObject("light_state") : new JsonObject();
                    lightState.addProperty("lever_" + index, state);

                    boolean allOn = true;
                    for (int i = 0; i < 5; i++) {
                        if (!(lightState.has("lever_" + i) && lightState.get("lever_" + i).getAsBoolean())) {
                            allOn = false;
                            break;
                        }
                    }

                    if (allOn) {
                        lightState = generateRandomLightState(); // Use new logic to re-randomize safely
                        SabotageManager.resolveSabotage("lights");
                    }

                    data.add("light_state", lightState);
                    BlockNBTStore.save(world, pos, data);

                    Amongcraft.LOGGER.info("Server: Player {} updated light {} to {} at {}", player.getName().getString(), index, state, pos);

                    // Send updated state to all players
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        PacketByteBuf syncBuf = new PacketByteBuf(Unpooled.buffer());
                        syncBuf.writeString(task);
                        syncBuf.writeInt(index);
                        syncBuf.writeBoolean(state);

                        ServerPlayNetworking.send(p, SERVER_SABOTAGE_SYNC_PACKET, syncBuf);
                    }

                    // Check if all 5 levers are ON
                    allOn = true;
                    for (int i = 0; i < 5; i++) {
                        if (!lightState.has("lever_" + i) || !lightState.get("lever_" + i).getAsBoolean()) {
                            allOn = false;
                            break;
                        }
                    }

                    if (allOn) {
                        Amongcraft.LOGGER.info("All levers ON for sabotage '{}', resolving...", task);
                        SabotageManager.resolveSabotage(task);
                    }
                });
            });
            ServerTickEvents.END_SERVER_TICK.register(server -> me.tg.amongcraft.TickScheduler.tick());
            ServerPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "sabotage_trigger"), (server, player, handler, buf, responseSender) -> {
                String sabotageType = buf.readString(); // e.g., "lights", "reactor", etc.

                server.execute(() -> {
                    switch (sabotageType) {
                        case "lights" -> {
                            System.out.println("Starting light sabotage...");
                            SabotageManager.triggerSabotage("lights");

                            me.tg.amongcraft.TickScheduler.schedule(() -> {
                                if (!SabotageManager.isSabotaged("lights")) return false;

                                for (ServerPlayerEntity crew : server.getPlayerManager().getPlayerList()) {
                                    if (AmongCraftCommands.crewmates.contains(crew.getUuid())) {
                                        // Re-apply 2 seconds of blindness every second
                                        crew.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
                                    }
                                }

                                return true; // Reschedule in 1 second
                            }, 20); // 20 ticks = 1 second
                        }

                        case "reactor", "o2" -> {
                            System.out.println("Starting " + sabotageType + " sabotage...");
                            SabotageManager.triggerSabotage(sabotageType);

                            final long startTime = System.currentTimeMillis();
                            final long durationMillis = 2 * 60 * 1000; // 2 minutes

                            me.tg.amongcraft.TickScheduler.schedule(() -> {
                                if (!SabotageManager.isSabotaged(sabotageType)) return false;

                                long elapsed = System.currentTimeMillis() - startTime;
                                long remaining = durationMillis - elapsed;

                                if (remaining <= 0) {
                                    // Timeout reached and sabotage not fixed
                                    for (UUID uuid : AmongCraftCommands.crewmates) {
                                        ServerPlayerEntity crew = server.getPlayerManager().getPlayer(uuid);
                                        if (crew != null && !crew.isDead()) {
                                            AmongCraftCommands.setPlayerDead(crew);
                                        }
                                    }
                                    return false;
                                }

                                long seconds = remaining / 1000;
                                long minutes = seconds / 60;
                                seconds = seconds % 60;

                                String msg = sabotageType.equals("reactor")
                                        ? "Reactor will explode in " + minutes + " minute" + (minutes == 1 ? "" : "s") + " and " + seconds + " second" + (seconds == 1 ? "" : "s")
                                        : "Oxygen will run out in " + minutes + " minute" + (minutes == 1 ? "" : "s") + " and " + seconds + " second" + (seconds == 1 ? "" : "s");

                                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                    p.sendMessage(Text.literal(msg), true); // true = action bar
                                }

                                return true; // Reschedule every second
                            }, 20); // 20 ticks = 1 second
                        }

                        case "comms" -> {
                            System.out.println("Starting comms sabotage...");
                            SabotageManager.triggerSabotage("comms");
                        }

                        default -> System.out.println("Unknown sabotage: " + sabotageType);
                    }


                });
            });
            ServerPlayNetworking.registerGlobalReceiver(new Identifier("amongcraft", "sabotage_resolve_try"),
                    (server, player, handler, buf, responseSender) -> {
                        String fullTaskName = buf.readString(); // e.g., o2-1, reactor-2, comms

                        server.execute(() -> {
                            String baseTask = fullTaskName.split("-")[0];

                            if (!SabotageManager.isSabotaged(baseTask)) return;

                            switch (baseTask) {
                                case "o2" -> {
                                    markDone(fullTaskName); // e.g., o2-1 or o2-2
                                    if (isBothO2PartsDone()) {
                                        SabotageManager.resolveSabotage("o2");
                                        reset();
                                    }
                                }
                                case "comms" -> {
                                    SabotageManager.resolveSabotage("comms");
                                }
                                case "reactor" -> {
                                    markReactorHold(fullTaskName, System.currentTimeMillis());
                                    if (reactorHeldForTwoSeconds()) {
                                        SabotageManager.resolveSabotage("reactor");
                                        reset();
                                    }
                                }
                            }
                        });
                    });
            ServerPlayNetworking.registerGlobalReceiver(TASKDONE_ID, (server, player, handler, buf, responseSender) -> {
                String taskId = buf.readString();
                BlockPos pos = buf.readBlockPos();
                server.execute(() -> {
                    TaskProgressTracker.markTaskAsDone(player, taskId, pos);
                });
            });
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                ServerWorld world = server.getOverworld(); // or another dimension
                Path dir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("amongcraft");
                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                CameraSystem.saveFile = dir.resolve("cameras.dat");
            });

            ServerPlayNetworking.registerGlobalReceiver(SAVE_TASK_ORDER, (server, player, handler, buf, responseSender) -> {
                BlockPos pos = buf.readBlockPos();
                String id = buf.readString();
                int lvl = buf.readInt();
                String rarity = buf.readString();
                String map = buf.readString();

                ServerWorld world = (ServerWorld) player.getWorld();
                server.execute(() -> {
                    TaskOrdererItem.setOrder(world, pos, id, lvl, rarity, map);
                    player.sendMessage(Text.literal("§aSaved task order for block at " + pos), false);
                });
            });
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                WentLinkerItem.setSavePath(server);
                WentLinkerItem.loadLinks();
            });
            ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
                for (ServerWorld world : server.getWorlds()) {
                for (ServerPlayerEntity player : world.getPlayers()) {
                    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                    buf.writeBoolean(peutron);
                    ServerPlayNetworking.send(player, PEUTRON_PACKET, buf);
                }}
            });
        }


    public static final Identifier SHOW_ANIMATION_PACKET_ID = new Identifier("cutscene_animation_mod", "show_animation");

    public static void showAnimation(ServerPlayerEntity target, Identifier animationJson, ServerPlayerEntity playerOfSkin, Identifier background) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(playerOfSkin.getUuid());
        buf.writeIdentifier(animationJson);
        buf.writeIdentifier(background);
        ServerPlayNetworking.send(target, SHOW_ANIMATION_PACKET_ID, buf);
    }



    // — BLOCK: COMMAND EXECUTOR —
        public static class StartBlock extends Block {
        public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
            private final String command;
            public StartBlock() {
                super(FabricBlockSettings.create().strength(1.0f));
                this.command = "/amongcraft start";
                this.setDefaultState(this.stateManager.getDefaultState()
                        .with(FACING, Direction.NORTH));
            }
            @Override
            public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
                if (!world.isClient && player instanceof ServerPlayerEntity spe) {
                    spe.server.getCommandManager().executeWithPrefix(spe.getCommandSource().withLevel(2), command);
                }
                return ActionResult.SUCCESS;
            }

            @Override
            protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
                builder.add(FACING);
            }

            @Override
            public BlockState getPlacementState(ItemPlacementContext ctx) {
                Direction playerFacing = ctx.getHorizontalPlayerFacing();

                return this.getDefaultState()
                        .with(FACING, playerFacing);
            }
        }

        // — BLOCK: TASK BUTTON —
        public static class TaskBlock extends Block {
            public static final EnumProperty<TaskType> TASK = EnumProperty.of("task", TaskType.class);


            public TaskBlock() {
                super(FabricBlockSettings.create()
                        .strength(1.0f)
                        .sounds(BlockSoundGroup.WOOD));
                this.setDefaultState(this.stateManager.getDefaultState()
                        .with(TASK, TaskType.DEFAULT)
                        .with(FACING, Direction.NORTH));
            }

            @Override
            public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
                super.onPlaced(world, pos, state, placer, itemStack);

                if (!world.isClient && itemStack.hasNbt()) {
                    NbtCompound nbt = itemStack.getNbt();
                    if (nbt.contains("task")) {
                        String taskId = nbt.getString("task");
                        String mapId = nbt.getString("map");

                        ServerWorld serverWorld = (ServerWorld) world;
                        saveTaskToJson(pos, taskId, mapId, serverWorld.getServer());

                        // Spawn name tag display above the block
                        spawnFloatingTaskName(serverWorld, pos, taskId);
                    }
                }
            }

            private void spawnFloatingTaskName(ServerWorld world, BlockPos pos, String taskId) {

                ArmorStandEntity tag = new ArmorStandEntity(world, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
                tag.setInvisible(true);
                tag.setCustomName(Text.literal("§eTask: §f" + taskId));
                tag.setCustomNameVisible(true);
                tag.setNoGravity(true);

                NbtCompound nbt = new NbtCompound();
                tag.writeNbt(nbt);
                nbt.putBoolean("Marker", true);
                tag.readNbt(nbt);

                world.spawnEntity(tag);
            }

            public enum TaskType implements StringIdentifiable {
                DEFAULT("default"),
                ALIGN_ENGINE_OUTPUT("align_engine_output"),
                CALIBRATE("calibrate"),
                CHART_COURSE("chart_course"),
                CHUTE("chute"),
                CLEAN_VENT("clean_vent"),
                CLEAR_ASTEROIDS("clear_asteroids"),
                DIVERT_POWER_1("divert_power_1"),
                DIVERT_POWER_2("divert_power_2"),
                DOWNLOAD("download"),
                FUELCAN("fuelcan"),
                INSPECT_SAMPLE("inspect_sample"),
                O2_FILTER("o2_filter"),
                SCAN("scan"),
                SHIELDS("shields"),
                STABILIZE_STEERING("stabilize_steering"),
                START_REACTOR("start_reactor"),
                SWIPE_CARD("swipe_card"),
                TASK1("task1"),
                TASK2("task2"),
                UNLOCK_MANIFOLDS("unlock_manifolds"),
                UPLOAD("upload"),
                WIRES("wiring"),
                FUELENGINE("fuelengine");

                private final String name;

                TaskType(String name) {
                    this.name = name;
                }

                @Override
                public String asString() {
                    return name;
                }
            }

            public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;


            @Override
            protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
                builder.add(TASK, FACING);
            }

            @Override
            public BlockState getPlacementState(ItemPlacementContext ctx) {
                ItemStack stack = ctx.getStack();
                Direction playerFacing = ctx.getHorizontalPlayerFacing();
                TaskType type = TaskType.DEFAULT; // default

                if (stack.hasNbt() && stack.getNbt().contains("task")) {
                    try {
                        type = TaskType.valueOf(stack.getNbt().getString("task").toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }

                return this.getDefaultState()
                        .with(TASK, type)
                        .with(FACING, playerFacing);
            }
            //shapes
            // base 2
            VoxelShape shape0 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.1875f, 0.9375f);
            VoxelShape shape1 = VoxelShapes.cuboid(0.125f, 0.1875f, 0.125f, 0.875f, 0.25f, 0.875f);
            VoxelShape shape2 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.1875f, 0.9375f);
            VoxelShape shape3 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.1875f, 0.9375f);
            VoxelShape shape4 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.0625f, 0.9375f, 0.1875f, 0.9375f);
            VoxelShape shape5 = VoxelShapes.cuboid(0.125f, 0.1875f, 0.125f, 0.875f, 0.25f, 0.875f);
            VoxelShape shape6 = VoxelShapes.cuboid(0.125f, 0.1875f, 0.125f, 0.875f, 0.25f, 0.875f);
            VoxelShape shape7 = VoxelShapes.cuboid(0.125f, 0.1875f, 0.125f, 0.875f, 0.25f, 0.875f);
            VoxelShape shape8 = VoxelShapes.cuboid(0.0f, 0.0f, 0.875f, 0.1875f, 0.125f, 1.0625f);
            VoxelShape shape9 = VoxelShapes.cuboid(-0.0625f, 0.0f, 0.8125f, 0.125f, 0.125f, 1.0f);
            VoxelShape shape10 = VoxelShapes.cuboid(0.0f, 0.0f, 0.0625f, 0.1875f, 0.125f, 0.25f);
            VoxelShape shape11 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.0f, 0.25f, 0.125f, 0.1875f);
            VoxelShape shape12 = VoxelShapes.cuboid(0.75f, 0.0f, 0.0f, 0.9375f, 0.125f, 0.1875f);
            VoxelShape shape13 = VoxelShapes.cuboid(0.8125f, 0.0f, 0.0625f, 1.0f, 0.125f, 0.25f);
            VoxelShape shape14 = VoxelShapes.cuboid(0.8125f, 0.0f, 0.8125f, 1.0f, 0.125f, 1.0f);
            VoxelShape shape15 = VoxelShapes.cuboid(0.75f, 0.0f, 0.875f, 0.9375f, 0.125f, 1.0625f);
            VoxelShape SCAN_SHAPE = VoxelShapes.union(shape0, shape1, shape2, shape3, shape4, shape5, shape6, shape7, shape8, shape9, shape10, shape11, shape12, shape13, shape14, shape15);
            VoxelShape shape16 = VoxelShapes.cuboid(0.0625f, 0.125f, 0.9375f, 0.9375f, 0.875f, 1.0f);
            VoxelShape WIRES_SHAPE = VoxelShapes.union(shape16);
            // engine
            VoxelShape shape17 = VoxelShapes.cuboid(0.0625f, 0.153545f, 0.731494f, 0.9375f, 0.216241f, 0.825113f);
            VoxelShape shape18 = VoxelShapes.cuboid(0.0625f, 0.240159f, 0.76737f, 0.9375f, 0.302855f, 0.860989f);
            VoxelShape shape19 = VoxelShapes.cuboid(0.0625f, 0.326773f, 0.803247f, 0.9375f, 0.389469f, 0.896866f);
            VoxelShape shape20 = VoxelShapes.cuboid(0.0625f, 0.413386f, 0.839123f, 0.9375f, 0.476082f, 0.932742f);
            VoxelShape shape21 = VoxelShapes.cuboid(0.0625f, 0.5f, 0.875f, 0.9375f, 0.562696f, 0.968619f);
            VoxelShape shape22 = VoxelShapes.cuboid(0.0625f, 0.586614f, 0.910877f, 0.9375f, 0.64931f, 1.004496f);
            VoxelShape shape23 = VoxelShapes.cuboid(0.0625f, 0.673227f, 0.946753f, 0.9375f, 0.735923f, 1.040372f);
            VoxelShape shape24 = VoxelShapes.cuboid(0.0625f, 0.759841f, 0.98263f, 0.9375f, 0.822537f, 1.076249f);
            VoxelShape ALIGN_ENGINE_OUTPUT_SHAPE = VoxelShapes.union(shape17, shape18, shape19, shape20, shape21, shape22, shape23, shape24);
            // calibrate
            VoxelShape shape25 = VoxelShapes.cuboid(0.125f, 0.0f, 0.9375f, 0.875f, 0.125f, 1.0f);
            VoxelShape shape26 = VoxelShapes.cuboid(0.125f, 0.125f, 0.9375f, 0.875f, 0.25f, 1.0f);
            VoxelShape shape27 = VoxelShapes.cuboid(0.125f, 0.25f, 0.9375f, 0.875f, 0.375f, 1.0f);
            VoxelShape shape28 = VoxelShapes.cuboid(0.125f, 0.375f, 0.9375f, 0.875f, 0.5f, 1.0f);
            VoxelShape shape29 = VoxelShapes.cuboid(0.125f, 0.5f, 0.9375f, 0.875f, 0.625f, 1.0f);
            VoxelShape shape30 = VoxelShapes.cuboid(0.125f, 0.625f, 0.9375f, 0.875f, 0.75f, 1.0f);
            VoxelShape shape31 = VoxelShapes.cuboid(0.125f, 0.75f, 0.9375f, 0.875f, 0.875f, 1.0f);
            VoxelShape shape32 = VoxelShapes.cuboid(0.125f, 0.875f, 0.9375f, 0.875f, 1.0f, 1.0f);
            VoxelShape CALIBRATE_SHAPE = VoxelShapes.union(shape25, shape26, shape27, shape28, shape29, shape30, shape31, shape32);
            // chart course
            VoxelShape shape33 = VoxelShapes.cuboid(-0.0625f, -0.033955f, 0.668994f, 1.0625f, 0.028741f, 0.762613f);
            VoxelShape shape34 = VoxelShapes.cuboid(-0.0625f, 0.052659f, 0.70487f, 1.0625f, 0.115355f, 0.798489f);
            VoxelShape shape35 = VoxelShapes.cuboid(-0.0625f, 0.139273f, 0.740747f, 1.0625f, 0.201969f, 0.834366f);
            VoxelShape shape36 = VoxelShapes.cuboid(-0.0625f, 0.225886f, 0.776623f, 1.0625f, 0.288582f, 0.870242f);
            VoxelShape shape37 = VoxelShapes.cuboid(-0.0625f, 0.3125f, 0.8125f, 1.0625f, 0.375196f, 0.906119f);
            VoxelShape shape38 = VoxelShapes.cuboid(-0.0625f, 0.399114f, 0.848377f, 1.0625f, 0.46181f, 0.941996f);
            VoxelShape shape39 = VoxelShapes.cuboid(-0.0625f, 0.485727f, 0.884253f, 1.0625f, 0.548423f, 0.977872f);
            VoxelShape shape40 = VoxelShapes.cuboid(-0.0625f, 0.572341f, 0.92013f, 1.0625f, 0.635037f, 1.013749f);
            VoxelShape CHART_COURSE_SHAPE = VoxelShapes.union(shape33, shape34, shape35, shape36, shape37, shape38, shape39, shape40);

            VoxelShape shape52 = VoxelShapes.cuboid(0.3125f, 0.125f, 0.875f, 0.9375f, 0.875f, 1.0f); // Back column
            VoxelShape shape41 = VoxelShapes.cuboid(0.0625f, 0.375f, 0.9375f, 0.25f, 0.625f, 1.0f);   // Left mid-side
            VoxelShape shape42 = VoxelShapes.cuboid(0.1875f, 0.4613f, 0.779f, 0.25f, 0.6712f, 1.0f);  // Inner center bar
            VoxelShape shape43 = VoxelShapes.cuboid(0.0625f, 0.4613f, 0.779f, 0.125f, 0.6712f, 1.0f); // Left inner bar
            VoxelShape shape44 = VoxelShapes.cuboid(0.0f, 0.638f, 0.735f, 0.3125f, 0.715f, 0.823f);   // Base slab
            VoxelShape shape45 = VoxelShapes.cuboid(0.875f, 0.125f, 0.875f, 0.9375f, 0.875f, 0.9375f); // Rear post
            VoxelShape shape46 = VoxelShapes.cuboid(0.3125f, 0.125f, 0.875f, 0.375f, 0.875f, 0.9375f); // Front small post
            VoxelShape shape47 = VoxelShapes.cuboid(0.375f, 0.125f, 0.875f, 0.875f, 0.875f, 0.9375f); // Center slab
            VoxelShape shape48 = VoxelShapes.cuboid(0.1875f, 0.5f, 0.79f, 0.25f, 0.67f, 0.98f);       // Diagonal sim approx
            VoxelShape shape49 = VoxelShapes.cuboid(0.0625f, 0.5f, 0.79f, 0.125f, 0.67f, 0.98f);     // Diagonal sim mirror
            VoxelShape shape50 = VoxelShapes.cuboid(0.0f, 0.65f, 0.75f, 0.3125f, 0.71f, 0.81f);      // Mid slab fill
            VoxelShape shape51 = VoxelShapes.cuboid(0.1875f, 0.5f, 0.75f, 0.25f, 0.65f, 0.79f);      // Bottom taper

            VoxelShape CHUTE_SHAPE = VoxelShapes.union(
                    shape52, shape41, shape42, shape43, shape44, shape45,
                    shape46, shape47, shape48, shape49, shape50, shape51
            );
            //asteroids
            VoxelShape shape113 = VoxelShapes.cuboid(-0.0625f, 0.0f, -0.375f, 1.0625f, 0.054688f, 0.75f);
            VoxelShape shape114 = VoxelShapes.cuboid(-0.0625f, 0.054688f, -0.375f, 1.0625f, 0.109375f, 0.75f);
            VoxelShape shape115 = VoxelShapes.cuboid(-0.0625f, 0.109375f, -0.375f, 1.0625f, 0.164062f, 0.75f);
            VoxelShape shape116 = VoxelShapes.cuboid(-0.0625f, 0.164062f, -0.375f, 1.0625f, 0.21875f, 0.75f);
            VoxelShape shape117 = VoxelShapes.cuboid(-0.0625f, 0.21875f, -0.375f, 1.0625f, 0.273438f, 0.75f);
            VoxelShape shape118 = VoxelShapes.cuboid(-0.0625f, 0.273438f, -0.375f, 1.0625f, 0.328125f, 0.75f);
            VoxelShape shape119 = VoxelShapes.cuboid(-0.0625f, 0.328125f, -0.375f, 1.0625f, 0.382812f, 0.75f);
            VoxelShape shape120 = VoxelShapes.cuboid(-0.0625f, 0.382812f, -0.375f, 1.0625f, 0.4375f, 0.75f);
            VoxelShape shape121 = VoxelShapes.cuboid(-0.0625f, 0.4375f, -0.375f, 1.0625f, 0.5625f, -0.125f);
            VoxelShape shape122 = VoxelShapes.cuboid(-0.0625f, 0.5625f, -0.375f, 1.0625f, 0.6875f, -0.125f);
            VoxelShape shape123 = VoxelShapes.cuboid(-0.0625f, 0.6875f, -0.375f, 1.0625f, 0.8125f, -0.125f);
            VoxelShape shape124 = VoxelShapes.cuboid(-0.0625f, 0.8125f, -0.375f, 1.0625f, 0.9375f, -0.125f);
            VoxelShape shape125 = VoxelShapes.cuboid(-0.0625f, 0.9375f, -0.375f, 1.0625f, 1.0625f, -0.125f);
            VoxelShape shape126 = VoxelShapes.cuboid(-0.0625f, 1.0625f, -0.375f, 1.0625f, 1.1875f, -0.125f);
            VoxelShape shape127 = VoxelShapes.cuboid(-0.0625f, 1.1875f, -0.375f, 1.0625f, 1.3125f, -0.125f);
            VoxelShape shape128 = VoxelShapes.cuboid(-0.0625f, 1.3125f, -0.375f, 1.0625f, 1.4375f, -0.125f);
            VoxelShape shape129 = VoxelShapes.cuboid(0.0625f, 0.8125f, 0.625f, 0.1875f, 0.859375f, 0.75f);
            VoxelShape shape130 = VoxelShapes.cuboid(0.0625f, 0.859375f, 0.625f, 0.1875f, 0.90625f, 0.75f);
            VoxelShape shape131 = VoxelShapes.cuboid(0.0625f, 0.90625f, 0.625f, 0.1875f, 0.953125f, 0.75f);
            VoxelShape shape132 = VoxelShapes.cuboid(0.0625f, 0.953125f, 0.625f, 0.1875f, 1.0f, 0.75f);
            VoxelShape shape133 = VoxelShapes.cuboid(0.0625f, 1.0f, 0.625f, 0.1875f, 1.046875f, 0.75f);
            VoxelShape shape134 = VoxelShapes.cuboid(0.0625f, 1.046875f, 0.625f, 0.1875f, 1.09375f, 0.75f);
            VoxelShape shape135 = VoxelShapes.cuboid(0.0625f, 1.09375f, 0.625f, 0.1875f, 1.140625f, 0.75f);
            VoxelShape shape136 = VoxelShapes.cuboid(0.0625f, 1.140625f, 0.625f, 0.1875f, 1.1875f, 0.75f);
            VoxelShape shape137 = VoxelShapes.cuboid(0.8125f, 0.8125f, 0.625f, 0.9375f, 0.859375f, 0.75f);
            VoxelShape shape138 = VoxelShapes.cuboid(0.8125f, 0.859375f, 0.625f, 0.9375f, 0.90625f, 0.75f);
            VoxelShape shape139 = VoxelShapes.cuboid(0.8125f, 0.90625f, 0.625f, 0.9375f, 0.953125f, 0.75f);
            VoxelShape shape140 = VoxelShapes.cuboid(0.8125f, 0.953125f, 0.625f, 0.9375f, 1.0f, 0.75f);
            VoxelShape shape141 = VoxelShapes.cuboid(0.8125f, 1.0f, 0.625f, 0.9375f, 1.046875f, 0.75f);
            VoxelShape shape142 = VoxelShapes.cuboid(0.8125f, 1.046875f, 0.625f, 0.9375f, 1.09375f, 0.75f);
            VoxelShape shape143 = VoxelShapes.cuboid(0.8125f, 1.09375f, 0.625f, 0.9375f, 1.140625f, 0.75f);
            VoxelShape shape144 = VoxelShapes.cuboid(0.8125f, 1.140625f, 0.625f, 0.9375f, 1.1875f, 0.75f);
            VoxelShape shape145 = VoxelShapes.cuboid(0.0f, 1.10056f, 1.208996f, 1.0f, 1.208827f, 1.253842f);
            VoxelShape shape146 = VoxelShapes.cuboid(0.0f, 1.208827f, 1.16415f, 1.0f, 1.317094f, 1.208996f);
            VoxelShape shape147 = VoxelShapes.cuboid(0.0f, 1.317094f, 1.119305f, 1.0f, 1.425362f, 1.16415f);
            VoxelShape shape148 = VoxelShapes.cuboid(0.0f, 1.425362f, 1.074459f, 1.0f, 1.533629f, 1.119305f);
            VoxelShape shape149 = VoxelShapes.cuboid(0.0f, 1.533629f, 1.029613f, 1.0f, 1.641896f, 1.074459f);
            VoxelShape shape150 = VoxelShapes.cuboid(0.0f, 1.641896f, 0.984767f, 1.0f, 1.750163f, 1.029613f);
            VoxelShape shape151 = VoxelShapes.cuboid(0.0f, 1.750163f, 0.939922f, 1.0f, 1.85843f, 0.984767f);
            VoxelShape shape152 = VoxelShapes.cuboid(0.0f, 1.85843f, 0.895076f, 1.0f, 1.966697f, 0.939922f);
            VoxelShape CLEAR_ASTEROIDS_SHAPE = VoxelShapes.union(shape113, shape114, shape115, shape116, shape117, shape118, shape119, shape120, shape121, shape122, shape123, shape124, shape125, shape126, shape127, shape128, shape129, shape130, shape131, shape132, shape133, shape134, shape135, shape136, shape137, shape138, shape139, shape140, shape141, shape142, shape143, shape144, shape145, shape146, shape147, shape148, shape149, shape150, shape151, shape152);

            VoxelShape shape153 = VoxelShapes.cuboid(0.0625f, 0.125f, 0.9375f, 0.9375f, 0.21875f, 1.0f);
            VoxelShape shape154 = VoxelShapes.cuboid(0.0625f, 0.21875f, 0.9375f, 0.9375f, 0.3125f, 1.0f);
            VoxelShape shape155 = VoxelShapes.cuboid(0.0625f, 0.3125f, 0.9375f, 0.9375f, 0.40625f, 1.0f);
            VoxelShape shape156 = VoxelShapes.cuboid(0.0625f, 0.40625f, 0.9375f, 0.9375f, 0.5f, 1.0f);
            VoxelShape shape157 = VoxelShapes.cuboid(0.0625f, 0.5f, 0.9375f, 0.9375f, 0.59375f, 1.0f);
            VoxelShape shape158 = VoxelShapes.cuboid(0.0625f, 0.59375f, 0.9375f, 0.9375f, 0.6875f, 1.0f);
            VoxelShape shape159 = VoxelShapes.cuboid(0.0625f, 0.6875f, 0.9375f, 0.9375f, 0.78125f, 1.0f);
            VoxelShape shape160 = VoxelShapes.cuboid(0.0625f, 0.78125f, 0.9375f, 0.9375f, 0.875f, 1.0f);
            VoxelShape DIVERT_POWER_SHAPE = VoxelShapes.union(shape153, shape154, shape155, shape156, shape157, shape158, shape159, shape160);

            VoxelShape shape161 = VoxelShapes.cuboid(0.375f, 0.0f, 0.25f, 0.625f, 0.0625f, 0.75f);
            VoxelShape shape162 = VoxelShapes.cuboid(0.375f, 0.0625f, 0.25f, 0.625f, 0.125f, 0.75f);
            VoxelShape shape163 = VoxelShapes.cuboid(0.375f, 0.125f, 0.25f, 0.625f, 0.1875f, 0.75f);
            VoxelShape shape164 = VoxelShapes.cuboid(0.375f, 0.1875f, 0.25f, 0.625f, 0.25f, 0.75f);
            VoxelShape shape165 = VoxelShapes.cuboid(0.375f, 0.25f, 0.25f, 0.625f, 0.3125f, 0.75f);
            VoxelShape shape166 = VoxelShapes.cuboid(0.375f, 0.3125f, 0.25f, 0.625f, 0.375f, 0.75f);
            VoxelShape shape167 = VoxelShapes.cuboid(0.375f, 0.375f, 0.25f, 0.625f, 0.4375f, 0.75f);
            VoxelShape shape168 = VoxelShapes.cuboid(0.375f, 0.4375f, 0.25f, 0.625f, 0.5f, 0.75f);
            VoxelShape shape169 = VoxelShapes.cuboid(0.375f, 0.5f, 0.3125f, 0.625f, 0.507812f, 0.75f);
            VoxelShape shape170 = VoxelShapes.cuboid(0.375f, 0.507812f, 0.3125f, 0.625f, 0.515625f, 0.75f);
            VoxelShape shape171 = VoxelShapes.cuboid(0.375f, 0.515625f, 0.3125f, 0.625f, 0.523438f, 0.75f);
            VoxelShape shape172 = VoxelShapes.cuboid(0.375f, 0.523438f, 0.3125f, 0.625f, 0.53125f, 0.75f);
            VoxelShape shape173 = VoxelShapes.cuboid(0.375f, 0.53125f, 0.3125f, 0.625f, 0.539062f, 0.75f);
            VoxelShape shape174 = VoxelShapes.cuboid(0.375f, 0.539062f, 0.3125f, 0.625f, 0.546875f, 0.75f);
            VoxelShape shape175 = VoxelShapes.cuboid(0.375f, 0.546875f, 0.3125f, 0.625f, 0.554688f, 0.75f);
            VoxelShape shape176 = VoxelShapes.cuboid(0.375f, 0.554688f, 0.3125f, 0.625f, 0.5625f, 0.75f);
            VoxelShape shape177 = VoxelShapes.cuboid(0.375f, 0.5625f, 0.375f, 0.625f, 0.570312f, 0.75f);
            VoxelShape shape178 = VoxelShapes.cuboid(0.375f, 0.570312f, 0.375f, 0.625f, 0.578125f, 0.75f);
            VoxelShape shape179 = VoxelShapes.cuboid(0.375f, 0.578125f, 0.375f, 0.625f, 0.585938f, 0.75f);
            VoxelShape shape180 = VoxelShapes.cuboid(0.375f, 0.585938f, 0.375f, 0.625f, 0.59375f, 0.75f);
            VoxelShape shape181 = VoxelShapes.cuboid(0.375f, 0.59375f, 0.375f, 0.625f, 0.601562f, 0.75f);
            VoxelShape shape182 = VoxelShapes.cuboid(0.375f, 0.601562f, 0.375f, 0.625f, 0.609375f, 0.75f);
            VoxelShape shape183 = VoxelShapes.cuboid(0.375f, 0.609375f, 0.375f, 0.625f, 0.617188f, 0.75f);
            VoxelShape shape184 = VoxelShapes.cuboid(0.375f, 0.617188f, 0.375f, 0.625f, 0.625f, 0.75f);
            VoxelShape shape185 = VoxelShapes.cuboid(0.4375f, 0.6875f, 0.375f, 0.5625f, 0.695312f, 0.75f);
            VoxelShape shape186 = VoxelShapes.cuboid(0.4375f, 0.695312f, 0.375f, 0.5625f, 0.703125f, 0.75f);
            VoxelShape shape187 = VoxelShapes.cuboid(0.4375f, 0.703125f, 0.375f, 0.5625f, 0.710938f, 0.75f);
            VoxelShape shape188 = VoxelShapes.cuboid(0.4375f, 0.710938f, 0.375f, 0.5625f, 0.71875f, 0.75f);
            VoxelShape shape189 = VoxelShapes.cuboid(0.4375f, 0.71875f, 0.375f, 0.5625f, 0.726562f, 0.75f);
            VoxelShape shape190 = VoxelShapes.cuboid(0.4375f, 0.726562f, 0.375f, 0.5625f, 0.734375f, 0.75f);
            VoxelShape shape191 = VoxelShapes.cuboid(0.4375f, 0.734375f, 0.375f, 0.5625f, 0.742188f, 0.75f);
            VoxelShape shape192 = VoxelShapes.cuboid(0.4375f, 0.742188f, 0.375f, 0.5625f, 0.75f, 0.75f);
            VoxelShape shape193 = VoxelShapes.cuboid(0.4375f, 0.625f, 0.375f, 0.5625f, 0.632812f, 0.4375f);
            VoxelShape shape194 = VoxelShapes.cuboid(0.4375f, 0.632812f, 0.375f, 0.5625f, 0.640625f, 0.4375f);
            VoxelShape shape195 = VoxelShapes.cuboid(0.4375f, 0.640625f, 0.375f, 0.5625f, 0.648438f, 0.4375f);
            VoxelShape shape196 = VoxelShapes.cuboid(0.4375f, 0.648438f, 0.375f, 0.5625f, 0.65625f, 0.4375f);
            VoxelShape shape197 = VoxelShapes.cuboid(0.4375f, 0.65625f, 0.375f, 0.5625f, 0.664062f, 0.4375f);
            VoxelShape shape198 = VoxelShapes.cuboid(0.4375f, 0.664062f, 0.375f, 0.5625f, 0.671875f, 0.4375f);
            VoxelShape shape199 = VoxelShapes.cuboid(0.4375f, 0.671875f, 0.375f, 0.5625f, 0.679688f, 0.4375f);
            VoxelShape shape200 = VoxelShapes.cuboid(0.4375f, 0.679688f, 0.375f, 0.5625f, 0.6875f, 0.4375f);
            VoxelShape shape201 = VoxelShapes.cuboid(0.4375f, 0.625f, 0.6875f, 0.5625f, 0.632812f, 0.75f);
            VoxelShape shape202 = VoxelShapes.cuboid(0.4375f, 0.632812f, 0.6875f, 0.5625f, 0.640625f, 0.75f);
            VoxelShape shape203 = VoxelShapes.cuboid(0.4375f, 0.640625f, 0.6875f, 0.5625f, 0.648438f, 0.75f);
            VoxelShape shape204 = VoxelShapes.cuboid(0.4375f, 0.648438f, 0.6875f, 0.5625f, 0.65625f, 0.75f);
            VoxelShape shape205 = VoxelShapes.cuboid(0.4375f, 0.65625f, 0.6875f, 0.5625f, 0.664062f, 0.75f);
            VoxelShape shape206 = VoxelShapes.cuboid(0.4375f, 0.664062f, 0.6875f, 0.5625f, 0.671875f, 0.75f);
            VoxelShape shape207 = VoxelShapes.cuboid(0.4375f, 0.671875f, 0.6875f, 0.5625f, 0.679688f, 0.75f);
            VoxelShape shape208 = VoxelShapes.cuboid(0.4375f, 0.679688f, 0.6875f, 0.5625f, 0.6875f, 0.75f);
            VoxelShape shape209 = VoxelShapes.cuboid(0.4375f, 0.411612f, 0.349112f, 0.5625f, 0.527621f, 0.409879f);
            VoxelShape shape210 = VoxelShapes.cuboid(0.4375f, 0.439233f, 0.32149f, 0.5625f, 0.555243f, 0.382257f);
            VoxelShape shape211 = VoxelShapes.cuboid(0.4375f, 0.466854f, 0.293869f, 0.5625f, 0.582864f, 0.354636f);
            VoxelShape shape212 = VoxelShapes.cuboid(0.4375f, 0.494476f, 0.266248f, 0.5625f, 0.610485f, 0.327015f);
            VoxelShape shape213 = VoxelShapes.cuboid(0.4375f, 0.522097f, 0.238626f, 0.5625f, 0.638107f, 0.299393f);
            VoxelShape shape214 = VoxelShapes.cuboid(0.4375f, 0.549718f, 0.211005f, 0.5625f, 0.665728f, 0.271772f);
            VoxelShape shape215 = VoxelShapes.cuboid(0.4375f, 0.57734f, 0.183384f, 0.5625f, 0.69335f, 0.24415f);
            VoxelShape shape216 = VoxelShapes.cuboid(0.4375f, 0.604961f, 0.155762f, 0.5625f, 0.720971f, 0.216529f);
            VoxelShape FUELCAN_SHAPE = VoxelShapes.union(shape161, shape162, shape163, shape164, shape165, shape166, shape167, shape168, shape169, shape170, shape171, shape172, shape173, shape174, shape175, shape176, shape177, shape178, shape179, shape180, shape181, shape182, shape183, shape184, shape185, shape186, shape187, shape188, shape189, shape190, shape191, shape192, shape193, shape194, shape195, shape196, shape197, shape198, shape199, shape200, shape201, shape202, shape203, shape204, shape205, shape206, shape207, shape208, shape209, shape210, shape211, shape212, shape213, shape214, shape215, shape216);

            VoxelShape shape217 = VoxelShapes.cuboid(-0.1875f, 0.125f, 0.5f, 0.9375f, 0.132812f, 0.9375f);
            VoxelShape shape218 = VoxelShapes.cuboid(-0.1875f, 0.132812f, 0.5f, 0.9375f, 0.140625f, 0.9375f);
            VoxelShape shape219 = VoxelShapes.cuboid(-0.1875f, 0.140625f, 0.5f, 0.9375f, 0.148438f, 0.9375f);
            VoxelShape shape220 = VoxelShapes.cuboid(-0.1875f, 0.148438f, 0.5f, 0.9375f, 0.15625f, 0.9375f);
            VoxelShape shape221 = VoxelShapes.cuboid(-0.1875f, 0.15625f, 0.5f, 0.9375f, 0.164062f, 0.9375f);
            VoxelShape shape222 = VoxelShapes.cuboid(-0.1875f, 0.164062f, 0.5f, 0.9375f, 0.171875f, 0.9375f);
            VoxelShape shape223 = VoxelShapes.cuboid(-0.1875f, 0.171875f, 0.5f, 0.9375f, 0.179688f, 0.9375f);
            VoxelShape shape224 = VoxelShapes.cuboid(-0.1875f, 0.179688f, 0.5f, 0.9375f, 0.1875f, 0.9375f);
            VoxelShape shape225 = VoxelShapes.cuboid(0.1875f, 0.1875f, 0.5625f, 0.875f, 0.195312f, 0.875f);
            VoxelShape shape226 = VoxelShapes.cuboid(0.1875f, 0.195312f, 0.5625f, 0.875f, 0.203125f, 0.875f);
            VoxelShape shape227 = VoxelShapes.cuboid(0.1875f, 0.203125f, 0.5625f, 0.875f, 0.210938f, 0.875f);
            VoxelShape shape228 = VoxelShapes.cuboid(0.1875f, 0.210938f, 0.5625f, 0.875f, 0.21875f, 0.875f);
            VoxelShape shape229 = VoxelShapes.cuboid(0.1875f, 0.21875f, 0.5625f, 0.875f, 0.226562f, 0.875f);
            VoxelShape shape230 = VoxelShapes.cuboid(0.1875f, 0.226562f, 0.5625f, 0.875f, 0.234375f, 0.875f);
            VoxelShape shape231 = VoxelShapes.cuboid(0.1875f, 0.234375f, 0.5625f, 0.875f, 0.242188f, 0.875f);
            VoxelShape shape232 = VoxelShapes.cuboid(0.1875f, 0.242188f, 0.5625f, 0.875f, 0.25f, 0.875f);
            VoxelShape shape233 = VoxelShapes.cuboid(-0.0625f, 0.1875f, 0.5625f, 0.0625f, 0.195312f, 0.8125f);
            VoxelShape shape234 = VoxelShapes.cuboid(-0.0625f, 0.195312f, 0.5625f, 0.0625f, 0.203125f, 0.8125f);
            VoxelShape shape235 = VoxelShapes.cuboid(-0.0625f, 0.203125f, 0.5625f, 0.0625f, 0.210938f, 0.8125f);
            VoxelShape shape236 = VoxelShapes.cuboid(-0.0625f, 0.210938f, 0.5625f, 0.0625f, 0.21875f, 0.8125f);
            VoxelShape shape237 = VoxelShapes.cuboid(-0.0625f, 0.21875f, 0.5625f, 0.0625f, 0.226562f, 0.8125f);
            VoxelShape shape238 = VoxelShapes.cuboid(-0.0625f, 0.226562f, 0.5625f, 0.0625f, 0.234375f, 0.8125f);
            VoxelShape shape239 = VoxelShapes.cuboid(-0.0625f, 0.234375f, 0.5625f, 0.0625f, 0.242188f, 0.8125f);
            VoxelShape shape240 = VoxelShapes.cuboid(-0.0625f, 0.242188f, 0.5625f, 0.0625f, 0.25f, 0.8125f);
            VoxelShape shape241 = VoxelShapes.cuboid(-0.1875f, 0.25f, 0.9375f, 0.9375f, 0.328125f, 1.0f);
            VoxelShape shape242 = VoxelShapes.cuboid(-0.1875f, 0.328125f, 0.9375f, 0.9375f, 0.40625f, 1.0f);
            VoxelShape shape243 = VoxelShapes.cuboid(-0.1875f, 0.40625f, 0.9375f, 0.9375f, 0.484375f, 1.0f);
            VoxelShape shape244 = VoxelShapes.cuboid(-0.1875f, 0.484375f, 0.9375f, 0.9375f, 0.5625f, 1.0f);
            VoxelShape shape245 = VoxelShapes.cuboid(-0.1875f, 0.5625f, 0.9375f, 0.9375f, 0.640625f, 1.0f);
            VoxelShape shape246 = VoxelShapes.cuboid(-0.1875f, 0.640625f, 0.9375f, 0.9375f, 0.71875f, 1.0f);
            VoxelShape shape247 = VoxelShapes.cuboid(-0.1875f, 0.71875f, 0.9375f, 0.9375f, 0.796875f, 1.0f);
            VoxelShape shape248 = VoxelShapes.cuboid(-0.1875f, 0.796875f, 0.9375f, 0.9375f, 0.875f, 1.0f);
            VoxelShape shape249 = VoxelShapes.cuboid(-0.019607f, 0.3125f, 0.204505f, 0.554917f, 0.382812f, 0.690641f);
            VoxelShape shape250 = VoxelShapes.cuboid(-0.019607f, 0.382812f, 0.204505f, 0.554917f, 0.453125f, 0.690641f);
            VoxelShape shape251 = VoxelShapes.cuboid(-0.019607f, 0.453125f, 0.204505f, 0.554917f, 0.523438f, 0.690641f);
            VoxelShape shape252 = VoxelShapes.cuboid(-0.019607f, 0.523438f, 0.204505f, 0.554917f, 0.59375f, 0.690641f);
            VoxelShape shape253 = VoxelShapes.cuboid(-0.019607f, 0.59375f, 0.204505f, 0.554917f, 0.664062f, 0.690641f);
            VoxelShape shape254 = VoxelShapes.cuboid(-0.019607f, 0.664062f, 0.204505f, 0.554917f, 0.734375f, 0.690641f);
            VoxelShape shape255 = VoxelShapes.cuboid(-0.019607f, 0.734375f, 0.204505f, 0.554917f, 0.804688f, 0.690641f);
            VoxelShape shape256 = VoxelShapes.cuboid(-0.019607f, 0.804688f, 0.204505f, 0.554917f, 0.875f, 0.690641f);
            VoxelShape INSPECT_SAMPLE_SHAPE = VoxelShapes.union(
                    shape217, shape218, shape219, shape220, shape221, shape222, shape223, shape224,
                    shape225, shape226, shape227, shape228, shape229, shape230, shape231, shape232,
                    shape233, shape234, shape235, shape236, shape237, shape238, shape239, shape240,
                    shape241, shape242, shape243, shape244, shape245, shape246, shape247, shape248
            );

            VoxelShape shape257 = VoxelShapes.cuboid(0.0625f, 0.125f, 0.8125f, 0.9375f, 0.21875f, 1.0f);
            VoxelShape shape258 = VoxelShapes.cuboid(0.0625f, 0.21875f, 0.8125f, 0.9375f, 0.3125f, 1.0f);
            VoxelShape shape259 = VoxelShapes.cuboid(0.0625f, 0.3125f, 0.8125f, 0.9375f, 0.40625f, 1.0f);
            VoxelShape shape260 = VoxelShapes.cuboid(0.0625f, 0.40625f, 0.8125f, 0.9375f, 0.5f, 1.0f);
            VoxelShape shape261 = VoxelShapes.cuboid(0.0625f, 0.5f, 0.8125f, 0.9375f, 0.59375f, 1.0f);
            VoxelShape shape262 = VoxelShapes.cuboid(0.0625f, 0.59375f, 0.8125f, 0.9375f, 0.6875f, 1.0f);
            VoxelShape shape263 = VoxelShapes.cuboid(0.0625f, 0.6875f, 0.8125f, 0.9375f, 0.78125f, 1.0f);
            VoxelShape shape264 = VoxelShapes.cuboid(0.0625f, 0.78125f, 0.8125f, 0.9375f, 0.875f, 1.0f);
            VoxelShape O2_FILTER_SHAPE = VoxelShapes.union(shape257, shape258, shape259, shape260, shape261, shape262, shape263, shape264);

            VoxelShape shape265 = VoxelShapes.cuboid(0.625f, 0.0f, 0.75f, 0.8125f, 0.125f, 0.9375f);
            VoxelShape shape266 = VoxelShapes.cuboid(0.625f, 0.125f, 0.75f, 0.8125f, 0.25f, 0.9375f);
            VoxelShape shape267 = VoxelShapes.cuboid(0.625f, 0.25f, 0.75f, 0.8125f, 0.375f, 0.9375f);
            VoxelShape shape268 = VoxelShapes.cuboid(0.625f, 0.375f, 0.75f, 0.8125f, 0.5f, 0.9375f);
            VoxelShape shape269 = VoxelShapes.cuboid(0.625f, 0.5f, 0.75f, 0.8125f, 0.625f, 0.9375f);
            VoxelShape shape270 = VoxelShapes.cuboid(0.625f, 0.625f, 0.75f, 0.8125f, 0.75f, 0.9375f);
            VoxelShape shape271 = VoxelShapes.cuboid(0.625f, 0.75f, 0.75f, 0.8125f, 0.875f, 0.9375f);
            VoxelShape shape272 = VoxelShapes.cuboid(0.625f, 0.875f, 0.75f, 0.8125f, 1.0f, 0.9375f);
            VoxelShape shape273 = VoxelShapes.cuboid(0.4375f, 1.0f, 0.6875f, 0.875f, 1.046875f, 0.9375f);
            VoxelShape shape274 = VoxelShapes.cuboid(0.4375f, 1.046875f, 0.6875f, 0.875f, 1.09375f, 0.9375f);
            VoxelShape shape275 = VoxelShapes.cuboid(0.4375f, 1.09375f, 0.6875f, 0.875f, 1.140625f, 0.9375f);
            VoxelShape shape276 = VoxelShapes.cuboid(0.4375f, 1.140625f, 0.6875f, 0.875f, 1.1875f, 0.9375f);
            VoxelShape shape277 = VoxelShapes.cuboid(0.4375f, 1.1875f, 0.6875f, 0.875f, 1.234375f, 0.9375f);
            VoxelShape shape278 = VoxelShapes.cuboid(0.4375f, 1.234375f, 0.6875f, 0.875f, 1.28125f, 0.9375f);
            VoxelShape shape279 = VoxelShapes.cuboid(0.4375f, 1.28125f, 0.6875f, 0.875f, 1.328125f, 0.9375f);
            VoxelShape shape280 = VoxelShapes.cuboid(0.4375f, 1.328125f, 0.6875f, 0.875f, 1.375f, 0.9375f);
            VoxelShape SHIELDS_SHAPE = VoxelShapes.union(shape265, shape266, shape267, shape268, shape269, shape270, shape271, shape272, shape273, shape274, shape275, shape276, shape277, shape278, shape279, shape280);

            VoxelShape shape281 = VoxelShapes.cuboid(-0.0625f, 0.0f, -0.375f, 1.0625f, 0.054688f, 0.75f);
            VoxelShape shape282 = VoxelShapes.cuboid(-0.0625f, 0.054688f, -0.375f, 1.0625f, 0.109375f, 0.75f);
            VoxelShape shape283 = VoxelShapes.cuboid(-0.0625f, 0.109375f, -0.375f, 1.0625f, 0.164062f, 0.75f);
            VoxelShape shape284 = VoxelShapes.cuboid(-0.0625f, 0.164062f, -0.375f, 1.0625f, 0.21875f, 0.75f);
            VoxelShape shape285 = VoxelShapes.cuboid(-0.0625f, 0.21875f, -0.375f, 1.0625f, 0.273438f, 0.75f);
            VoxelShape shape286 = VoxelShapes.cuboid(-0.0625f, 0.273438f, -0.375f, 1.0625f, 0.328125f, 0.75f);
            VoxelShape shape287 = VoxelShapes.cuboid(-0.0625f, 0.328125f, -0.375f, 1.0625f, 0.382812f, 0.75f);
            VoxelShape shape288 = VoxelShapes.cuboid(-0.0625f, 0.382812f, -0.375f, 1.0625f, 0.4375f, 0.75f);
            VoxelShape shape289 = VoxelShapes.cuboid(-0.0625f, 0.4375f, -0.375f, 1.0625f, 0.5625f, -0.125f);
            VoxelShape shape290 = VoxelShapes.cuboid(-0.0625f, 0.5625f, -0.375f, 1.0625f, 0.6875f, -0.125f);
            VoxelShape shape291 = VoxelShapes.cuboid(-0.0625f, 0.6875f, -0.375f, 1.0625f, 0.8125f, -0.125f);
            VoxelShape shape292 = VoxelShapes.cuboid(-0.0625f, 0.8125f, -0.375f, 1.0625f, 0.9375f, -0.125f);
            VoxelShape shape293 = VoxelShapes.cuboid(-0.0625f, 0.9375f, -0.375f, 1.0625f, 1.0625f, -0.125f);
            VoxelShape shape294 = VoxelShapes.cuboid(-0.0625f, 1.0625f, -0.375f, 1.0625f, 1.1875f, -0.125f);
            VoxelShape shape295 = VoxelShapes.cuboid(-0.0625f, 1.1875f, -0.375f, 1.0625f, 1.3125f, -0.125f);
            VoxelShape shape296 = VoxelShapes.cuboid(-0.0625f, 1.3125f, -0.375f, 1.0625f, 1.4375f, -0.125f);
            VoxelShape shape297 = VoxelShapes.cuboid(0.0625f, 0.8125f, 0.625f, 0.1875f, 0.859375f, 0.75f);
            VoxelShape shape298 = VoxelShapes.cuboid(0.0625f, 0.859375f, 0.625f, 0.1875f, 0.90625f, 0.75f);
            VoxelShape shape299 = VoxelShapes.cuboid(0.0625f, 0.90625f, 0.625f, 0.1875f, 0.953125f, 0.75f);
            VoxelShape shape300 = VoxelShapes.cuboid(0.0625f, 0.953125f, 0.625f, 0.1875f, 1.0f, 0.75f);
            VoxelShape shape301 = VoxelShapes.cuboid(0.0625f, 1.0f, 0.625f, 0.1875f, 1.046875f, 0.75f);
            VoxelShape shape302 = VoxelShapes.cuboid(0.0625f, 1.046875f, 0.625f, 0.1875f, 1.09375f, 0.75f);
            VoxelShape shape303 = VoxelShapes.cuboid(0.0625f, 1.09375f, 0.625f, 0.1875f, 1.140625f, 0.75f);
            VoxelShape shape304 = VoxelShapes.cuboid(0.0625f, 1.140625f, 0.625f, 0.1875f, 1.1875f, 0.75f);
            VoxelShape shape305 = VoxelShapes.cuboid(0.8125f, 0.8125f, 0.625f, 0.9375f, 0.859375f, 0.75f);
            VoxelShape shape306 = VoxelShapes.cuboid(0.8125f, 0.859375f, 0.625f, 0.9375f, 0.90625f, 0.75f);
            VoxelShape shape307 = VoxelShapes.cuboid(0.8125f, 0.90625f, 0.625f, 0.9375f, 0.953125f, 0.75f);
            VoxelShape shape308 = VoxelShapes.cuboid(0.8125f, 0.953125f, 0.625f, 0.9375f, 1.0f, 0.75f);
            VoxelShape shape309 = VoxelShapes.cuboid(0.8125f, 1.0f, 0.625f, 0.9375f, 1.046875f, 0.75f);
            VoxelShape shape310 = VoxelShapes.cuboid(0.8125f, 1.046875f, 0.625f, 0.9375f, 1.09375f, 0.75f);
            VoxelShape shape311 = VoxelShapes.cuboid(0.8125f, 1.09375f, 0.625f, 0.9375f, 1.140625f, 0.75f);
            VoxelShape shape312 = VoxelShapes.cuboid(0.8125f, 1.140625f, 0.625f, 0.9375f, 1.1875f, 0.75f);
            VoxelShape shape313 = VoxelShapes.cuboid(-1.0f, 0.0f, 1.0f, 2.0f, 0.085938f, 2.0f);
            VoxelShape shape314 = VoxelShapes.cuboid(-1.0f, 0.085938f, 1.0f, 2.0f, 0.171875f, 2.0f);
            VoxelShape shape315 = VoxelShapes.cuboid(-1.0f, 0.171875f, 1.0f, 2.0f, 0.257812f, 2.0f);
            VoxelShape shape316 = VoxelShapes.cuboid(-1.0f, 0.257812f, 1.0f, 2.0f, 0.34375f, 2.0f);
            VoxelShape shape317 = VoxelShapes.cuboid(-1.0f, 0.34375f, 1.0f, 2.0f, 0.429688f, 2.0f);
            VoxelShape shape318 = VoxelShapes.cuboid(-1.0f, 0.429688f, 1.0f, 2.0f, 0.515625f, 2.0f);
            VoxelShape shape319 = VoxelShapes.cuboid(-1.0f, 0.515625f, 1.0f, 2.0f, 0.601562f, 2.0f);
            VoxelShape shape320 = VoxelShapes.cuboid(-1.0f, 0.601562f, 1.0f, 2.0f, 0.6875f, 2.0f);
            VoxelShape shape321 = VoxelShapes.cuboid(-1.0f, 0.643306f, 1.044194f, 2.0f, 1.355937f, 1.745777f);
            VoxelShape shape322 = VoxelShapes.cuboid(-1.0f, 0.64883f, 1.03867f, 2.0f, 1.361461f, 1.740252f);
            VoxelShape shape323 = VoxelShapes.cuboid(-1.0f, 0.654354f, 1.033146f, 2.0f, 1.366985f, 1.734728f);
            VoxelShape shape324 = VoxelShapes.cuboid(-1.0f, 0.659879f, 1.027621f, 2.0f, 1.37251f, 1.729204f);
            VoxelShape shape325 = VoxelShapes.cuboid(-1.0f, 0.665403f, 1.022097f, 2.0f, 1.378034f, 1.72368f);
            VoxelShape shape326 = VoxelShapes.cuboid(-1.0f, 0.670927f, 1.016573f, 2.0f, 1.383558f, 1.718155f);
            VoxelShape shape327 = VoxelShapes.cuboid(-1.0f, 0.676451f, 1.011049f, 2.0f, 1.389083f, 1.712631f);
            VoxelShape shape328 = VoxelShapes.cuboid(-1.0f, 0.681976f, 1.005524f, 2.0f, 1.394607f, 1.707107f);
            VoxelShape STABILIZE_STEERING_SHAPE = VoxelShapes.union(shape281, shape282, shape283, shape284, shape285, shape286, shape287, shape288, shape289, shape290, shape291, shape292, shape293, shape294, shape295, shape296, shape297, shape298, shape299, shape300, shape301, shape302, shape303, shape304, shape305, shape306, shape307, shape308, shape309, shape310, shape311, shape312, shape313, shape314, shape315, shape316, shape317, shape318, shape319, shape320, shape321, shape322, shape323, shape324, shape325, shape326, shape327, shape328);

            VoxelShape shape329 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.125f, 0.9375f, 0.070312f, 1.0f);
            VoxelShape shape330 = VoxelShapes.cuboid(0.0625f, 0.070312f, 0.125f, 0.9375f, 0.140625f, 1.0f);
            VoxelShape shape331 = VoxelShapes.cuboid(0.0625f, 0.140625f, 0.125f, 0.9375f, 0.210938f, 1.0f);
            VoxelShape shape332 = VoxelShapes.cuboid(0.0625f, 0.210938f, 0.125f, 0.9375f, 0.28125f, 1.0f);
            VoxelShape shape333 = VoxelShapes.cuboid(0.0625f, 0.28125f, 0.125f, 0.9375f, 0.351562f, 1.0f);
            VoxelShape shape334 = VoxelShapes.cuboid(0.0625f, 0.351562f, 0.125f, 0.9375f, 0.421875f, 1.0f);
            VoxelShape shape335 = VoxelShapes.cuboid(0.0625f, 0.421875f, 0.125f, 0.9375f, 0.492188f, 1.0f);
            VoxelShape shape336 = VoxelShapes.cuboid(0.0625f, 0.492188f, 0.125f, 0.9375f, 0.5625f, 1.0f);
            VoxelShape shape337 = VoxelShapes.cuboid(0.0625f, 0.567258f, 0.148918f, 0.9375f, 0.909323f, 0.954323f);
            VoxelShape shape338 = VoxelShapes.cuboid(0.0625f, 0.574475f, 0.145928f, 0.9375f, 0.916541f, 0.951333f);
            VoxelShape shape339 = VoxelShapes.cuboid(0.0625f, 0.581693f, 0.142938f, 0.9375f, 0.923759f, 0.948343f);
            VoxelShape shape340 = VoxelShapes.cuboid(0.0625f, 0.588911f, 0.139949f, 0.9375f, 0.930977f, 0.945353f);
            VoxelShape shape341 = VoxelShapes.cuboid(0.0625f, 0.596129f, 0.136959f, 0.9375f, 0.938195f, 0.942364f);
            VoxelShape shape342 = VoxelShapes.cuboid(0.0625f, 0.603347f, 0.133969f, 0.9375f, 0.945412f, 0.939374f);
            VoxelShape shape343 = VoxelShapes.cuboid(0.0625f, 0.610564f, 0.130979f, 0.9375f, 0.95263f, 0.936384f);
            VoxelShape shape344 = VoxelShapes.cuboid(0.0625f, 0.617782f, 0.12799f, 0.9375f, 0.959848f, 0.933395f);
            VoxelShape shape345 = VoxelShapes.cuboid(0.0625f, 0.5625f, 0.125f, 0.0625f, 0.617188f, 0.9375f);
            VoxelShape shape346 = VoxelShapes.cuboid(0.0625f, 0.617188f, 0.125f, 0.0625f, 0.671875f, 0.9375f);
            VoxelShape shape347 = VoxelShapes.cuboid(0.0625f, 0.671875f, 0.125f, 0.0625f, 0.726562f, 0.9375f);
            VoxelShape shape348 = VoxelShapes.cuboid(0.0625f, 0.726562f, 0.125f, 0.0625f, 0.78125f, 0.9375f);
            VoxelShape shape349 = VoxelShapes.cuboid(0.0625f, 0.78125f, 0.125f, 0.0625f, 0.835938f, 0.9375f);
            VoxelShape shape350 = VoxelShapes.cuboid(0.0625f, 0.835938f, 0.125f, 0.0625f, 0.890625f, 0.9375f);
            VoxelShape shape351 = VoxelShapes.cuboid(0.0625f, 0.890625f, 0.125f, 0.0625f, 0.945312f, 0.9375f);
            VoxelShape shape352 = VoxelShapes.cuboid(0.0625f, 0.945312f, 0.125f, 0.0625f, 1.0f, 0.9375f);
            VoxelShape shape353 = VoxelShapes.cuboid(0.9375f, 0.5625f, 0.125f, 0.9375f, 0.617188f, 0.9375f);
            VoxelShape shape354 = VoxelShapes.cuboid(0.9375f, 0.617188f, 0.125f, 0.9375f, 0.671875f, 0.9375f);
            VoxelShape shape355 = VoxelShapes.cuboid(0.9375f, 0.671875f, 0.125f, 0.9375f, 0.726562f, 0.9375f);
            VoxelShape shape356 = VoxelShapes.cuboid(0.9375f, 0.726562f, 0.125f, 0.9375f, 0.78125f, 0.9375f);
            VoxelShape shape357 = VoxelShapes.cuboid(0.9375f, 0.78125f, 0.125f, 0.9375f, 0.835938f, 0.9375f);
            VoxelShape shape358 = VoxelShapes.cuboid(0.9375f, 0.835938f, 0.125f, 0.9375f, 0.890625f, 0.9375f);
            VoxelShape shape359 = VoxelShapes.cuboid(0.9375f, 0.890625f, 0.125f, 0.9375f, 0.945312f, 0.9375f);
            VoxelShape shape360 = VoxelShapes.cuboid(0.9375f, 0.945312f, 0.125f, 0.9375f, 1.0f, 0.9375f);
            VoxelShape shape361 = VoxelShapes.cuboid(0.0625f, 0.5625f, 0.9375f, 0.9375f, 0.617188f, 1.0f);
            VoxelShape shape362 = VoxelShapes.cuboid(0.0625f, 0.617188f, 0.9375f, 0.9375f, 0.671875f, 1.0f);
            VoxelShape shape363 = VoxelShapes.cuboid(0.0625f, 0.671875f, 0.9375f, 0.9375f, 0.726562f, 1.0f);
            VoxelShape shape364 = VoxelShapes.cuboid(0.0625f, 0.726562f, 0.9375f, 0.9375f, 0.78125f, 1.0f);
            VoxelShape shape365 = VoxelShapes.cuboid(0.0625f, 0.78125f, 0.9375f, 0.9375f, 0.835938f, 1.0f);
            VoxelShape shape366 = VoxelShapes.cuboid(0.0625f, 0.835938f, 0.9375f, 0.9375f, 0.890625f, 1.0f);
            VoxelShape shape367 = VoxelShapes.cuboid(0.0625f, 0.890625f, 0.9375f, 0.9375f, 0.945312f, 1.0f);
            VoxelShape shape368 = VoxelShapes.cuboid(0.0625f, 0.945312f, 0.9375f, 0.9375f, 1.0f, 1.0f);
            VoxelShape START_REACTOR_SHAPE = VoxelShapes.union(shape329, shape330, shape331, shape332, shape333, shape334, shape335, shape336, shape337, shape338, shape339, shape340, shape341, shape342, shape343, shape344, shape345, shape346, shape347, shape348, shape349, shape350, shape351, shape352, shape353, shape354, shape355, shape356, shape357, shape358, shape359, shape360, shape361, shape362, shape363, shape364, shape365, shape366, shape367, shape368);

            VoxelShape shape369 = VoxelShapes.cuboid(0.0625f, 0.4375f, 0.25f, 0.9375f, 0.460938f, 0.625f);
            VoxelShape shape370 = VoxelShapes.cuboid(0.0625f, 0.460938f, 0.25f, 0.9375f, 0.484375f, 0.625f);
            VoxelShape shape371 = VoxelShapes.cuboid(0.0625f, 0.484375f, 0.25f, 0.9375f, 0.507812f, 0.625f);
            VoxelShape shape372 = VoxelShapes.cuboid(0.0625f, 0.507812f, 0.25f, 0.9375f, 0.53125f, 0.625f);
            VoxelShape shape373 = VoxelShapes.cuboid(0.0625f, 0.53125f, 0.25f, 0.9375f, 0.554688f, 0.625f);
            VoxelShape shape374 = VoxelShapes.cuboid(0.0625f, 0.554688f, 0.25f, 0.9375f, 0.578125f, 0.625f);
            VoxelShape shape375 = VoxelShapes.cuboid(0.0625f, 0.578125f, 0.25f, 0.9375f, 0.601562f, 0.625f);
            VoxelShape shape376 = VoxelShapes.cuboid(0.0625f, 0.601562f, 0.25f, 0.9375f, 0.625f, 0.625f);
            VoxelShape SWIPE_CARD_SHAPE = VoxelShapes.union(shape369, shape370, shape371, shape372, shape373, shape374, shape375, shape376);

            VoxelShape shape377 = VoxelShapes.cuboid(0.8125f, 0.0f, 0.9375f, 0.875f, 0.148438f, 1.0f);
            VoxelShape shape378 = VoxelShapes.cuboid(0.8125f, 0.148438f, 0.9375f, 0.875f, 0.296875f, 1.0f);
            VoxelShape shape379 = VoxelShapes.cuboid(0.8125f, 0.296875f, 0.9375f, 0.875f, 0.445312f, 1.0f);
            VoxelShape shape380 = VoxelShapes.cuboid(0.8125f, 0.445312f, 0.9375f, 0.875f, 0.59375f, 1.0f);
            VoxelShape shape381 = VoxelShapes.cuboid(0.8125f, 0.59375f, 0.9375f, 0.875f, 0.742188f, 1.0f);
            VoxelShape shape382 = VoxelShapes.cuboid(0.8125f, 0.742188f, 0.9375f, 0.875f, 0.890625f, 1.0f);
            VoxelShape shape383 = VoxelShapes.cuboid(0.8125f, 0.890625f, 0.9375f, 0.875f, 1.039062f, 1.0f);
            VoxelShape shape384 = VoxelShapes.cuboid(0.8125f, 1.039062f, 0.9375f, 0.875f, 1.1875f, 1.0f);
            VoxelShape shape385 = VoxelShapes.cuboid(0.9375f, 0.0f, 0.9375f, 1.0f, 0.179688f, 1.0f);
            VoxelShape shape386 = VoxelShapes.cuboid(0.9375f, 0.179688f, 0.9375f, 1.0f, 0.359375f, 1.0f);
            VoxelShape shape387 = VoxelShapes.cuboid(0.9375f, 0.359375f, 0.9375f, 1.0f, 0.539062f, 1.0f);
            VoxelShape shape388 = VoxelShapes.cuboid(0.9375f, 0.539062f, 0.9375f, 1.0f, 0.71875f, 1.0f);
            VoxelShape shape389 = VoxelShapes.cuboid(0.9375f, 0.71875f, 0.9375f, 1.0f, 0.898438f, 1.0f);
            VoxelShape shape390 = VoxelShapes.cuboid(0.9375f, 0.898438f, 0.9375f, 1.0f, 1.078125f, 1.0f);
            VoxelShape shape391 = VoxelShapes.cuboid(0.9375f, 1.078125f, 0.9375f, 1.0f, 1.257812f, 1.0f);
            VoxelShape shape392 = VoxelShapes.cuboid(0.9375f, 1.257812f, 0.9375f, 1.0f, 1.4375f, 1.0f);
            VoxelShape shape393 = VoxelShapes.cuboid(0.3125f, 1.1875f, 0.9375f, 0.875f, 1.21875f, 1.0f);
            VoxelShape shape394 = VoxelShapes.cuboid(0.3125f, 1.21875f, 0.9375f, 0.875f, 1.25f, 1.0f);
            VoxelShape shape395 = VoxelShapes.cuboid(0.3125f, 1.25f, 0.9375f, 0.875f, 1.28125f, 1.0f);
            VoxelShape shape396 = VoxelShapes.cuboid(0.3125f, 1.28125f, 0.9375f, 0.875f, 1.3125f, 1.0f);
            VoxelShape shape397 = VoxelShapes.cuboid(0.3125f, 1.3125f, 0.9375f, 0.875f, 1.34375f, 1.0f);
            VoxelShape shape398 = VoxelShapes.cuboid(0.3125f, 1.34375f, 0.9375f, 0.875f, 1.375f, 1.0f);
            VoxelShape shape399 = VoxelShapes.cuboid(0.3125f, 1.375f, 0.9375f, 0.875f, 1.40625f, 1.0f);
            VoxelShape shape400 = VoxelShapes.cuboid(0.3125f, 1.40625f, 0.9375f, 0.875f, 1.4375f, 1.0f);
            VoxelShape shape401 = VoxelShapes.cuboid(0.8125f, 1.4375f, 0.9375f, 1.0f, 1.445312f, 1.0f);
            VoxelShape shape402 = VoxelShapes.cuboid(0.8125f, 1.445312f, 0.9375f, 1.0f, 1.453125f, 1.0f);
            VoxelShape shape403 = VoxelShapes.cuboid(0.8125f, 1.453125f, 0.9375f, 1.0f, 1.460938f, 1.0f);
            VoxelShape shape404 = VoxelShapes.cuboid(0.8125f, 1.460938f, 0.9375f, 1.0f, 1.46875f, 1.0f);
            VoxelShape shape405 = VoxelShapes.cuboid(0.8125f, 1.46875f, 0.9375f, 1.0f, 1.476562f, 1.0f);
            VoxelShape shape406 = VoxelShapes.cuboid(0.8125f, 1.476562f, 0.9375f, 1.0f, 1.484375f, 1.0f);
            VoxelShape shape407 = VoxelShapes.cuboid(0.8125f, 1.484375f, 0.9375f, 1.0f, 1.492188f, 1.0f);
            VoxelShape shape408 = VoxelShapes.cuboid(0.8125f, 1.492188f, 0.9375f, 1.0f, 1.5f, 1.0f);
            VoxelShape UNLOCK_MANIFOLDS_SHAPE = VoxelShapes.union(shape377, shape378, shape379, shape380, shape381, shape382, shape383, shape384, shape385, shape386, shape387, shape388, shape389, shape390, shape391, shape392, shape393, shape394, shape395, shape396, shape397, shape398, shape399, shape400, shape401, shape402, shape403, shape404, shape405, shape406, shape407, shape408);

            VoxelShape shape409 = VoxelShapes.cuboid(0.1875f, 0.03f, 0.705f, 0.9375f, 0.41f, 0.93f);   // Main rising slope (merged shapes 409–412)
            VoxelShape shape410 = VoxelShapes.cuboid(0.1875f, 0.41f, 0.87f, 0.9375f, 0.82f, 1.1f);     // Top slope segment (merged 413–416)

            VoxelShape shape411 = VoxelShapes.cuboid(0.0f, 0.11f, 0.75f, 0.1875f, 0.15f, 0.82f);       // Left base segment (merged 417–419)
            VoxelShape shape412 = VoxelShapes.cuboid(0.0f, 0.15f, 0.77f, 0.1875f, 0.19f, 0.84f);       // Mid-left layer (merged 420–422)
            VoxelShape shape413 = VoxelShapes.cuboid(0.0f, 0.19f, 0.79f, 0.1875f, 0.23f, 0.86f);       // Top-left layer (merged 423–424)

            VoxelShape shape414 = VoxelShapes.cuboid(0.1875f, 0.0f, 0.7f, 0.9375f, 0.03f, 0.93f);      // Bottom lip

            VoxelShape FUEL_ENGINE_SHAPE = VoxelShapes.union(
                    shape409, shape410, shape411, shape412, shape413, shape414
            );
            @Override
            public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
                TaskType task = state.get(TaskBlock.TASK);
                Direction facing = state.get(TaskBlock.FACING);

                VoxelShape baseShape;
                switch (task) {
                    case DEFAULT:
                        baseShape = VoxelShapes.fullCube();
                        break;
                    case ALIGN_ENGINE_OUTPUT:
                        baseShape = ALIGN_ENGINE_OUTPUT_SHAPE;
                        break;
                    case CALIBRATE:
                        baseShape = CALIBRATE_SHAPE;
                        break;
                    case CHART_COURSE:
                        baseShape = CHART_COURSE_SHAPE;
                        break;
                    case CHUTE:
                        baseShape = CHUTE_SHAPE;
                        break;
                    case CLEAN_VENT:
                        baseShape = VoxelShapes.fullCube();
                        break;
                    case CLEAR_ASTEROIDS:
                        baseShape = CLEAR_ASTEROIDS_SHAPE;
                        break;
                    case DIVERT_POWER_1:
                        baseShape = DIVERT_POWER_SHAPE;
                        break;
                    case DIVERT_POWER_2:
                        baseShape = DIVERT_POWER_SHAPE;
                        break;
                    case DOWNLOAD:
                        baseShape = WIRES_SHAPE;
                        break;
                    case FUELCAN:
                        baseShape = FUELCAN_SHAPE;
                        break;
                    case FUELENGINE:
                        baseShape = FUEL_ENGINE_SHAPE;
                        break;
                    case INSPECT_SAMPLE:
                        baseShape = INSPECT_SAMPLE_SHAPE;
                        break;
                    case O2_FILTER:
                        baseShape = O2_FILTER_SHAPE;
                        break;
                    case SCAN:
                        baseShape = SCAN_SHAPE;
                        break;
                    case SHIELDS:
                        baseShape = SHIELDS_SHAPE;
                        break;
                    case STABILIZE_STEERING:
                        baseShape = STABILIZE_STEERING_SHAPE;
                        break;
                    case START_REACTOR:
                        baseShape = START_REACTOR_SHAPE;
                        break;
                    case SWIPE_CARD:
                        baseShape = SWIPE_CARD_SHAPE;
                        break;
                    case TASK1:
                        baseShape = VoxelShapes.fullCube();
                        break;
                    case TASK2:
                        baseShape = VoxelShapes.fullCube();
                        break;
                    case UNLOCK_MANIFOLDS:
                        baseShape = UNLOCK_MANIFOLDS_SHAPE;
                        break;
                    case UPLOAD:
                        baseShape = WIRES_SHAPE;
                        break;
                    case WIRES:
                        baseShape = WIRES_SHAPE;
                        break;
                    default:
                        return VoxelShapes.fullCube();
                }

                return rotateShape(facing, baseShape);
            }
        }
    public static VoxelShape rotateShape(Direction direction, VoxelShape shape) {
        VoxelShape[] buffer = new VoxelShape[] { shape, VoxelShapes.empty() };

        int times = (direction.getHorizontal()) % 4;
        for (int i = 0; i < times; i++) {
            shape = buffer[0];
            buffer[0] = VoxelShapes.empty();

            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                buffer[0] = VoxelShapes.union(buffer[0],
                        VoxelShapes.cuboid(
                                1 - maxZ, minY, minX,
                                1 - minZ, maxY, maxX
                        ));
            });
        }

        return buffer[0];
    }



    private static void saveTaskToJson(BlockPos pos, String taskId, String mapId, MinecraftServer server) {
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/taskblocks.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, Map<String, String>> taskMap = new HashMap<>();

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Type type = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
                Object parsed = gson.fromJson(reader, Object.class);
                if (parsed instanceof Map) {
                    taskMap = gson.fromJson(gson.toJson(parsed), type);
                } else {
                    // If it's not a map (e.g., string), reset to empty map
                    taskMap = new HashMap<>();
                }
            } catch (IOException | JsonSyntaxException e) {
                taskMap = new HashMap<>();
            }
        }

        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Map<String, String> data = new HashMap<>();
        data.put("task", taskId);
        data.put("map", mapId);

        taskMap.put(key, data);

        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(taskMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class emergency extends Block {
        VoxelShape shape0 = VoxelShapes.cuboid(0.0f, 0.0f, 0.0f, 1.0f, 0.0625f, 1.0f);
        VoxelShape shape1 = VoxelShapes.cuboid(0.3125f, 0.0625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f);
        VoxelShape shape2 = VoxelShapes.cuboid(0.3125f, 0.0625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f);
        VoxelShape shape3 = VoxelShapes.cuboid(0.3125f, 0.0625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f);
        VoxelShape shape4 = VoxelShapes.cuboid(0.3125f, 0.0625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f);
        VoxelShape shape5 = VoxelShapes.cuboid(0.125f, 0.0625f, 0.125f, 0.1875f, 0.375f, 0.1875f);
        VoxelShape shape6 = VoxelShapes.cuboid(0.125f, 0.0625f, 0.8125f, 0.1875f, 0.375f, 0.875f);
        VoxelShape shape7 = VoxelShapes.cuboid(0.8125f, 0.0625f, 0.125f, 0.875f, 0.375f, 0.1875f);
        VoxelShape shape8 = VoxelShapes.cuboid(0.8125f, 0.0625f, 0.8125f, 0.875f, 0.375f, 0.875f);
        VoxelShape shape9 = VoxelShapes.cuboid(0.125f, 0.375f, 0.8125f, 0.875f, 0.4375f, 0.875f);
        VoxelShape shape10 = VoxelShapes.cuboid(0.125f, 0.375f, 0.125f, 0.875f, 0.4375f, 0.1875f);
        VoxelShape shape11 = VoxelShapes.cuboid(0.8125f, 0.375f, 0.1875f, 0.875f, 0.4375f, 0.8125f);
        VoxelShape shape12 = VoxelShapes.cuboid(0.125f, 0.375f, 0.1875f, 0.1875f, 0.4375f, 0.8125f);
        VoxelShape COLLISION_SHAPE = VoxelShapes.union(shape0, shape1, shape2, shape3, shape4, shape5, shape6, shape7, shape8, shape9, shape10, shape11, shape12);

        public emergency() {
            super(FabricBlockSettings.create().strength(1.0f).sounds(BlockSoundGroup.METAL).nonOpaque());
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
            return COLLISION_SHAPE;
        }
    }

    // — BLOCK: WENT —
        public static class WentBlock extends Block {
        VoxelShape shape0 = VoxelShapes.cuboid(0.09375f, 0.0f, 0.15625f, 0.875f, 0.09375f, 0.84375f);
        VoxelShape shape1 = VoxelShapes.cuboid(0.0625f, 0.0f, 0.1875f, 0.09375f, 0.09375f, 0.8125f);
        VoxelShape shape2 = VoxelShapes.cuboid(0.875f, 0.0f, 0.1875f, 0.90625f, 0.09375f, 0.84375f);
        VoxelShape shape3 = VoxelShapes.cuboid(0.03125f, 0.0f, 0.21875f, 0.0625f, 0.09375f, 0.78125f);
        VoxelShape shape4 = VoxelShapes.cuboid(0.90625f, 0.0f, 0.21875f, 0.9375f, 0.09375f, 0.8125f);
        VoxelShape shape5 = VoxelShapes.cuboid(0.9375f, 0.0f, 0.25f, 0.96875f, 0.09375f, 0.78125f);
        VoxelShape shape6 = VoxelShapes.cuboid(0.125f, 0.0f, 0.84375f, 0.875f, 0.09375f, 0.875f);
        VoxelShape COLLISION_SHAPE = VoxelShapes.union(shape0, shape1, shape2, shape3, shape4, shape5, shape6);
        public WentBlock() {
                super(FabricBlockSettings.create().strength(1.0f));
            }
        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, ShapeContext context) {
            return COLLISION_SHAPE;
        }
        }

    public static boolean isUnlocked(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        TaskOrdererItem.TaskOrder thisOrder = TaskOrdererItem.getOrder(world, pos);
        if (thisOrder == null || thisOrder.level() == 0) return true;

        Map<BlockPos, TaskOrdererItem.TaskOrder> allOrders = TaskOrdererItem.loadOrders(world);

        for (Map.Entry<BlockPos, TaskOrdererItem.TaskOrder> entry : allOrders.entrySet()) {
            BlockPos otherPos = entry.getKey();
            TaskOrdererItem.TaskOrder otherOrder = entry.getValue();
            String coords = pos.getX() + "," + pos.getY() + "," + pos.getZ();
            Path file = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/taskblocks.json");
            JsonObject json;
            try {
                json = JsonParser.parseReader(Files.newBufferedReader(file)).getAsJsonObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            JsonElement element = json.get(coords);
            if (element == null || !element.isJsonObject()) {
                Amongcraft.LOGGER.warn("No task found at position " + coords);
                return false;
            }
            JsonObject taskData = element.getAsJsonObject();
            if (!taskData.has("task") || !taskData.has("map")) return false;
            String baseTaskId = taskData.get("task").getAsString();
            String fullTaskId = baseTaskId + "@" + coords;
            UUID playerId = player.getUuid();
            Map<String, Set<String>> taskMap = playerTaskProgress.get(playerId);
            boolean newTask = taskMap.get(currentMap).add(fullTaskId);
            if (otherOrder.identifier().equals(thisOrder.identifier())
                    && otherOrder.level() < thisOrder.level()
                    && !newTask) {
                return false; // A prerequisite task is not completed
            }
        }

        return true;
    }

    public static class TaskOrdererItem extends Item {

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        public TaskOrdererItem(Settings settings) {
            super(settings);
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext context) {
            World world = context.getWorld();
            BlockPos pos = context.getBlockPos();
            PlayerEntity player = context.getPlayer();

            if (!world.isClient && world.getBlockState(pos).getBlock() instanceof TaskBlock && player instanceof ServerPlayerEntity serverPlayer) {
                openTaskEditorScreen(serverPlayer, pos);
            }

            return ActionResult.SUCCESS;
        }

        private void openTaskEditorScreen(ServerPlayerEntity player, BlockPos pos) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            ServerPlayNetworking.send(player, OPEN_ORDER_EDITOR, buf);
        }

        public static void setOrder(ServerWorld world, BlockPos pos, String identifier, int level, String rarity, String map) {
            Map<BlockPos, TaskOrder> mapData = loadOrders(world);
            mapData.put(pos, new TaskOrder(identifier, level, rarity, map));
            saveOrders(world, mapData);
        }


        public static TaskOrder getOrder(ServerWorld world, BlockPos pos) {
            return loadOrders(world).get(pos);
        }

        public static Map<BlockPos, TaskOrder> loadOrders(ServerWorld world) {
            Path path = getSaveFile(world);
            Map<BlockPos, TaskOrder> map = new HashMap<>();

            if (!Files.exists(path)) return map;

            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : obj.keySet()) {
                    BlockPos pos = BlockPos.fromLong(Long.parseLong(key));
                    JsonObject task = obj.getAsJsonObject(key);
                    String id = task.get("identifier").getAsString();
                    int level = task.get("level").getAsInt();
                    String rarity = task.has("rarity") ? task.get("rarity").getAsString() : "common";
                    String mapName = task.has("map") ? task.get("map").getAsString() : "default";
                    map.put(pos, new TaskOrder(id, level, rarity, mapName));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return map;
        }

        public static void saveOrders(ServerWorld world, Map<BlockPos, TaskOrder> map) {
            Path path = getSaveFile(world);
            JsonObject obj = new JsonObject();

            for (Map.Entry<BlockPos, TaskOrder> entry : map.entrySet()) {
                String key = Long.toString(entry.getKey().asLong());
                TaskOrder order = entry.getValue();
                JsonObject task = new JsonObject();
                task.addProperty("identifier", order.identifier());
                task.addProperty("level", order.level());
                task.addProperty("rarity", order.rarity());
                task.addProperty("map", order.map());
                obj.add(key, task);
            }

            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    GSON.toJson(obj, writer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private static Path getSaveFile(ServerWorld world) {
            return world.getServer().getSavePath(WorldSavePath.ROOT).resolve("amongcraft/taskorders.json");
        }

        public record TaskOrder(String identifier, int level, String rarity, String map) {}

    }


    // — ITEM: KILLER KNIFE —
        public static class KillerKnifeItem extends SwordItem {
            public KillerKnifeItem() {
                super(ToolMaterials.WOOD, 0, -2.4f, new FabricItemSettings().maxCount(1));
            }

            @Override
            public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
                if (!target.getWorld().isClient) {
                    if (attacker instanceof ServerPlayerEntity serverPlayer) {
                        makePlayerVisible(serverPlayer);
                    }

                    boolean protectedTarget = false;

                    if (target instanceof PlayerEntity p) {
                        if (AngelProtectionManager.isPlayerProtected(p.getUuid())) {
                            protectedTarget = true;
                            p.sendMessage(Text.literal("§bAn Angel protected you from death!"), false);
                        } else {
                            p.kill();
                        }
                    }

                    if (attacker instanceof PlayerEntity player) {
                        int cooldownTicks = SettingsManager.get("kill-cooldown").getAsInt() * 20;

                        player.addStatusEffect(new StatusEffectInstance(
                                StatusEffects.WEAKNESS,
                                cooldownTicks,
                                255,
                                false,
                                false
                        ));
                        player.getItemCooldownManager().set(this, cooldownTicks);

                        if (protectedTarget) {
                            player.sendMessage(Text.literal("§cYour kill was blocked by Angel protection!"), true);
                        } else {
                            player.sendMessage(Text.literal("Killer Knife cooldown..."), true);
                        }
                    }
                }
                return true;
            }
        }

    public static class TaskTriggerItem extends Item {
        public TaskTriggerItem(Settings settings) {
            super(settings);
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext context) {
            World world = context.getWorld();
            BlockPos pos = context.getBlockPos();
            PlayerEntity player = context.getPlayer();

            if (world.isClient || player == null) return ActionResult.SUCCESS;

            MinecraftServer server = ((ServerWorld) world).getServer();
            String taskId = getTaskFromJson(pos, server);

            if (taskId == null) {
                player.sendMessage(Text.literal("No task assigned to this block."), false);
                return ActionResult.FAIL;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                    if (!isUnlocked(serverPlayer, pos)) {
                        player.sendMessage(Text.literal("§cYou haven't unlocked this task yet."), false);
                        return ActionResult.FAIL;
                    }

                sendTaskOpenPacket(serverPlayer, taskId, pos);

                return ActionResult.SUCCESS;
            }
            return ActionResult.FAIL;
        }

        private String getTaskFromJson(BlockPos pos, MinecraftServer server) {
            Path file = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/taskblocks.json");
            String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();

            if (!Files.exists(file)) return null;

            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();

                if (obj.has(key)) {
                    JsonElement element = obj.get(key);

                    if (element.isJsonPrimitive()) {
                        // Old format: just a string
                        return element.getAsString();
                    } else if (element.isJsonObject()) {
                        // New format: object with "task" field
                        JsonObject dataObj = element.getAsJsonObject();
                        if (dataObj.has("task")) {
                            return dataObj.get("task").getAsString();
                        }
                    }
                }
            } catch (IOException ignored) {}

            return null;
        }

    }



    // — ITEM: COMMAND ITEM —
        public static class CommandItem extends Item {
            private final String command;

            public CommandItem(String command) {
                super(new FabricItemSettings().maxCount(1));
                this.command = command;
            }

            @Override
            public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
                if (!world.isClient && player instanceof ServerPlayerEntity spe) {
                    spe.server.getCommandManager().executeWithPrefix(spe.getCommandSource().withLevel(2), command);
                }

                return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
            }
        }


        // — ITEM: WENT LINKER —
        public static class WentLinkerItem extends Item {
            private static Path SAVE_PATH = null;
            private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

            public WentLinkerItem() {
                super(new FabricItemSettings().maxCount(1));
            }

            public static void setSavePath(MinecraftServer server) {
                if (SAVE_PATH == null) {
                    SAVE_PATH = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/vents.json");
                }
            }

            // Save links to disk (call after modifying wentLinks)
            public static void saveLinks() {
                try {
                    Files.createDirectories(SAVE_PATH.getParent());
                    JsonObject root = new JsonObject();

                    for (Map.Entry<BlockPos, List<BlockPos>> entry : wentLinks.entrySet()) {
                        JsonArray targetsArray = new JsonArray();
                        for (BlockPos target : entry.getValue()) {
                            targetsArray.add(posToJson(target));
                        }
                        root.add(posToString(entry.getKey()), targetsArray);
                    }
                    Files.writeString(SAVE_PATH, GSON.toJson(root));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Load links from disk (call on server start)
            public static void loadLinks() {
                try {
                    if (!Files.exists(SAVE_PATH)) return;
                    String jsonString = Files.readString(SAVE_PATH);
                    JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

                    wentLinks.clear();
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        BlockPos key = posFromString(entry.getKey());
                        List<BlockPos> list = new ArrayList<>();
                        JsonArray array = entry.getValue().getAsJsonArray();
                        for (JsonElement el : array) {
                            list.add(posFromJson(el.getAsJsonObject()));
                        }
                        wentLinks.put(key, list);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Helpers for serializing BlockPos
            private static JsonObject posToJson(BlockPos pos) {
                JsonObject obj = new JsonObject();
                obj.addProperty("x", pos.getX());
                obj.addProperty("y", pos.getY());
                obj.addProperty("z", pos.getZ());
                return obj;
            }

            private static BlockPos posFromJson(JsonObject obj) {
                return new BlockPos(
                        obj.get("x").getAsInt(),
                        obj.get("y").getAsInt(),
                        obj.get("z").getAsInt()
                );
            }

            private static String posToString(BlockPos pos) {
                return pos.getX() + "," + pos.getY() + "," + pos.getZ();
            }

            private static BlockPos posFromString(String str) {
                String[] parts = str.split(",");
                return new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
            }

            @Override
            public ActionResult useOnBlock(ItemUsageContext context) {
                PlayerEntity player = context.getPlayer();
                World world = context.getWorld();
                BlockPos clickedPos = context.getBlockPos();

                if (!world.isClient && player instanceof ServerPlayerEntity spe) {
                    if (world.getBlockState(clickedPos).getBlock() == WENT_BLOCK) {
                        UUID uuid = spe.getUuid();

                        if (!selectedWent.containsKey(uuid)) {
                            selectedWent.put(uuid, clickedPos);
                            player.sendMessage(Text.literal("Selected vent for linking."), false);
                        } else {
                            BlockPos from = selectedWent.remove(uuid);
                            BlockPos to = clickedPos;

                            if (from.equals(to)) {
                                player.sendMessage(Text.literal("Cannot link a vent to itself."), false);
                                return ActionResult.SUCCESS;
                            }

                            // Find all groups containing either of the two vents
                            Set<BlockPos> mergedGroup = new HashSet<>();
                            for (Map.Entry<BlockPos, List<BlockPos>> entry : wentLinks.entrySet()) {
                                BlockPos base = entry.getKey();
                                List<BlockPos> linked = entry.getValue();

                                if (base.equals(from) || base.equals(to) || linked.contains(from) || linked.contains(to)) {
                                    mergedGroup.add(base);
                                    mergedGroup.addAll(linked);
                                }
                            }

                            // If neither vent is in any existing group, just make a new link
                            if (mergedGroup.isEmpty()) {
                                wentLinks.put(from, new ArrayList<>(List.of(to)));
                                wentLinks.put(to, new ArrayList<>(List.of(from)));
                            } else {
                                mergedGroup.add(from);
                                mergedGroup.add(to);

                                // Update wentLinks: clear all involved first
                                for (BlockPos pos : mergedGroup) {
                                    wentLinks.remove(pos);
                                }

                                // Re-add everyone linked to everyone else
                                for (BlockPos pos : mergedGroup) {
                                    List<BlockPos> links = new ArrayList<>();
                                    for (BlockPos other : mergedGroup) {
                                        if (!other.equals(pos)) links.add(other);
                                    }
                                    wentLinks.put(pos, links);
                                }
                            }

                            player.sendMessage(Text.literal("Linked vents together."), false);
                            saveLinks();
                        }
                    }
                }

                return ActionResult.SUCCESS;
            }

        }

        // — ITEM: SWITCH WENT —
        public static class SwitchWentItem extends Item {

            // Tracks the last vent index used per player UUID
            private static final Map<UUID, Integer> ventIndices = new HashMap<>();

            // Tracks the current vent position the player is inside
            private static final Map<UUID, BlockPos> playerCurrentVent = new HashMap<>();

            public SwitchWentItem() {
                super(new FabricItemSettings().maxCount(1));
            }

            @Override
            public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
                if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
                if (!(player instanceof ServerPlayerEntity spe)) return TypedActionResult.fail(player.getStackInHand(hand));

                UUID uuid = spe.getUuid();

                if (spe.getItemCooldownManager().isCoolingDown(this)) {
                    spe.sendMessage(Text.literal("Item is on cooldown!"), true);
                    return TypedActionResult.fail(player.getStackInHand(hand));
                }

                BlockPos currentPos = spe.getBlockPos();

                Optional<BlockPos> sourceVent = wentLinks.keySet().stream()
                        .filter(pos -> pos.isWithinDistance(currentPos, 1.5))
                        .findFirst();

                if (sourceVent.isEmpty()) {
                    spe.sendMessage(Text.literal("No nearby vent found."), false);
                    return TypedActionResult.success(player.getStackInHand(hand), false);
                }

                BlockPos source = sourceVent.get();
                List<BlockPos> targets = new ArrayList<>(getFullVentGroup(source));
                targets.sort(Comparator.comparingInt(BlockPos::getX));


                // Cycle vent index
                int currentIndex = ventIndices.getOrDefault(uuid, -1);
                int nextIndex;

                if (currentIndex == -1) {
                    // First use: teleport into the vent the player is standing near
                    nextIndex = targets.indexOf(source);
                    if (nextIndex == -1) nextIndex = 0; // Fallback, shouldn't happen unless source missing
                } else {
                    // Subsequent uses: cycle through list
                    nextIndex = (currentIndex + 1) % targets.size();
                }

                BlockPos nextVent = targets.get(nextIndex);

                // Teleport player one block below next vent (inside it)
                BlockPos tpPos = nextVent.down();
                spe.teleport(tpPos.getX() + 0.5, tpPos.getY(), tpPos.getZ() + 0.5);

                // Add invisibility effect infinitely (hidden)
                spe.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, true));

                ventIndices.put(uuid, nextIndex);
                playerCurrentVent.put(uuid, nextVent);

                // Short cooldown to prevent spam clicking (1 sec)
                spe.getItemCooldownManager().set(this, 20);

                return TypedActionResult.success(player.getStackInHand(hand), false);
            }

            // Must be called every tick on server side or hooked into ServerTickEvents
            public static void handlePlayerJumpOut(ServerWorld serverWorld) {
                Iterator<Map.Entry<UUID, BlockPos>> iterator = playerCurrentVent.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, BlockPos> entry = iterator.next();
                    UUID uuid = entry.getKey();
                    BlockPos ventPos = entry.getValue();
                    ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(uuid);
                    if (player == null) {
                        iterator.remove();
                        ventIndices.remove(uuid);
                        continue;
                    }

                    // If player no longer has invisibility, cleanup
                    if (!player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                        iterator.remove();
                        ventIndices.remove(uuid);
                        continue;
                    }

                    BlockPos playerPos = player.getBlockPos();

                    boolean jumpedOut = playerPos.getY() > ventPos.getY() || !playerPos.isWithinDistance(ventPos, 1.5);

                    if (jumpedOut) {
                        player.removeStatusEffect(StatusEffects.INVISIBILITY);

                        int cooldown = SettingsManager.get("went-cooldown").getAsInt();

                        // Set 60-second cooldown after exiting vent
                        player.getItemCooldownManager().set(Amongcraft.SWITCH_WENT, 20*cooldown);

                        ventIndices.remove(uuid);
                        iterator.remove();
                    }
                }
            }
        }

    private static Set<BlockPos> getFullVentGroup(BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            BlockPos current = stack.pop();
            if (visited.add(current)) {
                List<BlockPos> neighbors = wentLinks.getOrDefault(current, Collections.emptyList());
                for (BlockPos neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        stack.push(neighbor);
                    }
                }
            }
        }

        return visited;
    }


    public static class TabletItem extends Item {
        public TabletItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
                PacketByteBuf buf = PacketByteBufs.create();

                List<UUID> all = new ArrayList<>();
                all.addAll(impostors);
                all.addAll(AmongCraftCommands.crewmates);

                buf.writeInt(all.size());
                for (UUID uuid : all) {
                    buf.writeUuid(uuid);
                    buf.writeBoolean(true); // alive
                }

                // Optionally: remove UUIDs of players who have died
                for (ServerPlayerEntity player : serverPlayer.getServer().getPlayerManager().getPlayerList()) {
                    if (!all.contains(player.getUuid())) {
                        buf.writeUuid(player.getUuid());
                        buf.writeBoolean(false); // dead
                    }
                }

                ServerPlayNetworking.send(serverPlayer, Amongcraft.TABLET_SYNC_PACKET_ID, buf);
            }

            return TypedActionResult.success(user.getStackInHand(hand));
        }
    }

    public static class PhantherItem extends Item {
        public PhantherItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(user.getStackInHand(hand));

            if (player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                makePlayerVisible(player);
            } else {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 999999, 0, false, false));
            }

            return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
        }
    }
    public static void makePlayerVisible(ServerPlayerEntity player) {
        if (player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
    }

    public static class GunItem extends SwordItem {
        public GunItem() {
            super(ToolMaterials.DIAMOND, 10, -2.4f, new FabricItemSettings().maxCount(1));
        }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (!target.getWorld().isClient) {
                if (target instanceof ServerPlayerEntity targetPlayer && attacker instanceof ServerPlayerEntity attackerPlayer) {
                    UUID targetUUID = targetPlayer.getUuid();

                    boolean targetIsCrewmate = AmongCraftCommands.crewmates.contains(targetUUID);

                    // Kill the target
                    targetPlayer.kill();

                    if (targetIsCrewmate) {
                        // Punish attacker for killing a crewmate
                        attackerPlayer.kill();
                    }

                    // Cooldown effect for the attacker
                    attackerPlayer.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.WEAKNESS,
                            SettingsManager.get("kill-cooldown").getAsInt() * 20,
                            255,
                            false,
                            false
                    ));
                    attackerPlayer.getItemCooldownManager().set(this, SettingsManager.get("kill-cooldown").getAsInt() * 20);
                    attackerPlayer.sendMessage(Text.literal("Gun cooldown..."), true);
                }
            }
            return true;
        }
    }

    public static class ShifterItem extends Item {
        public ShifterItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
                PacketByteBuf buf = PacketByteBufs.create();

                List<UUID> all = new ArrayList<>();
                all.addAll(impostors);
                all.addAll(AmongCraftCommands.crewmates);

                buf.writeInt(all.size());
                for (UUID uuid : all) {
                    buf.writeUuid(uuid);
                    buf.writeBoolean(true); // alive
                }

                // Include dead players
                for (ServerPlayerEntity player : serverPlayer.getServer().getPlayerManager().getPlayerList()) {
                    if (!all.contains(player.getUuid())) {
                        buf.writeUuid(player.getUuid());
                        buf.writeBoolean(false); // dead
                    }
                }

                ServerPlayNetworking.send(serverPlayer, Amongcraft.SHIFTER_PACKET, buf);
            }

            return TypedActionResult.success(user.getStackInHand(hand));
        }
    }

    public static final int DISGUISE_COOLDOWN_SECONDS = SettingsManager.get("Roles.Shapeshifter.shapeshift_cooldown").getAsInt();
    public static final int DISGUISE_DURATION_SECONDS = SettingsManager.get("Roles.Shapeshifter.shapeshift_duration").getAsInt();

    private static final Map<UUID, Long> cooldownMap = new HashMap<>();
    public static void disguisePlayer(ServerPlayerEntity player, ServerPlayerEntity target) {
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();

        // If player = target, unshift
        if (player.getUuid().equals(target.getUuid())) {
            ShapeshiftHandler.unshift(player);
            return;
        }

        // Cooldown check
        long now = System.currentTimeMillis();
        if (cooldownMap.containsKey(playerId)) {
            long lastUse = cooldownMap.get(playerId);
            long elapsed = now - lastUse;
            if (elapsed < DISGUISE_COOLDOWN_SECONDS * 1000L) {
                long secondsLeft = (DISGUISE_COOLDOWN_SECONDS * 1000L - elapsed) / 1000;
                player.sendMessage(Text.literal("You must wait " + secondsLeft + " seconds before disguising again."), false);
                return;
            }
        }

        // Set cooldown
        cooldownMap.put(playerId, now);

        // Call shapeshift
        ShapeshiftHandler.shapeshift(player, target.getUuid());

        // Auto-unshift after disguise duration
        TickDelayedExecutor.schedule(server, 20 * DISGUISE_DURATION_SECONDS, () -> {
            ShapeshiftHandler.unshift(player);
        });
    }





    public static class GenericItem extends Item {
        public GenericItem(Settings settings) {
            super(settings);
        }
    }
    public static void schedule(int delayTicks, Runnable task) {
        tasks.add(new ScheduledTask(task, delayTicks));
    }

    private static class ScheduledTask {
        Runnable runnable;
        int ticksLeft;

        ScheduledTask(Runnable runnable, int ticksLeft) {
            this.runnable = runnable;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final Set<String> completedO2Parts = new HashSet<>();
    private static final Map<String, Long> reactorHoldStart = new HashMap<>();

    public static void markDone(String part) {
        completedO2Parts.add(part);
    }

    public static boolean isBothO2PartsDone() {
        return completedO2Parts.contains("o2-1") && completedO2Parts.contains("o2-2");
    }

    public static void markReactorHold(String part, long timestamp) {
        reactorHoldStart.put(part, timestamp);
    }

    public static boolean reactorHeldForTwoSeconds() {
        if (!reactorHoldStart.containsKey("reactor-1") || !reactorHoldStart.containsKey("reactor-2")) return false;
        long now = System.currentTimeMillis();
        long t1 = reactorHoldStart.get("reactor-1");
        long t2 = reactorHoldStart.get("reactor-2");

        return Math.abs(t1 - t2) < 200 && (now - t1 >= 2000) && (now - t2 >= 2000);
    }

    public static void reset() {
        completedO2Parts.clear();
        reactorHoldStart.clear();
    }

}
