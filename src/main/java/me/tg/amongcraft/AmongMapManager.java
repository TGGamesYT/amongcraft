package me.tg.amongcraft;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntityType;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static me.tg.amongcraft.AmongCraftCommands.*;
import static net.minecraft.server.command.CommandManager.literal;

public class AmongMapManager {

    public static final Map<String, Box> MAP_BOUNDS = new HashMap<>();
    public static final MapSpawnBlock MAP_SPAWN_BLOCK = new MapSpawnBlock();
    public static final LobbySpawnBlock LOBBY_SPAWN_BLOCK = new LobbySpawnBlock();
    public static final BlockEntityType<SpawnBlockEntity> SPAWN_BLOCK_ENTITY_TYPE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier("amongcraft", "spawn_block_entity"),
            FabricBlockEntityTypeBuilder.create(SpawnBlockEntity::new, AmongMapManager.MAP_SPAWN_BLOCK).build(null)
    );
    private static Path DATA_FILE;

    public static void getfile(MinecraftServer server) {
        DATA_FILE = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/data.json");
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("amongcraft")
                .then(literal("start")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> {
                            // Use the current map if no map argument was provided
                            return runStart(ctx, TaskProgressTracker.currentMap);
                        })
                        .then(CommandManager.argument("map", StringArgumentType.word())
                                .executes(ctx -> {
                                    String map = StringArgumentType.getString(ctx, "map");
                                    TaskProgressTracker.currentMap = map;
                                    return runStart(ctx, map);
                                })
                        )
                )
                .then(literal("map")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("define")
                                .then(CommandManager.argument("map", StringArgumentType.word())
                                        .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                                                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                                                        .executes(ctx -> {
                                                            try {
                                                                getfile(ctx.getSource().getServer());
                                                                String map = StringArgumentType.getString(ctx, "map");
                                                                BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                                                                BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");

                                                                AmongMapManager.saveMapBounds(ctx.getSource().getServer(), map, from, to);
                                                                ctx.getSource().sendFeedback(() -> Text.literal("§aMap " + map + " defined with bounds."), false);
                                                                return 1;
                                                            } catch (Exception e) {
                                                                e.printStackTrace(); // will go to logs/latest.log
                                                                ctx.getSource().sendFeedback(() -> Text.literal("§cAn internal error occurred: " + e.getMessage()), false);
                                                                return 0;
                                                            }
                                                        }))))))
                .then(literal("lobby")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> {
                            // Use the current map if no map argument was provided
                            return runLobby(ctx, TaskProgressTracker.currentMap);
                        })
                        .then(CommandManager.argument("map", StringArgumentType.word())
                                .executes(ctx -> {
                                    String map = StringArgumentType.getString(ctx, "map");
                                    TaskProgressTracker.currentMap = map;
                                    return runLobby(ctx, map);
                                })
                        )
                )
        );
    }

    private static int runStart(CommandContext<ServerCommandSource> ctx, String map) {
        try {
            if (!GameState.isRunning()) {
                JsonObject allRoles = SettingsManager.get("Roles").getAsJsonObject();
                MeetingManager.resetMeetingData();
                TaskProgressTracker.resetProgress();
                TaskProgressTracker.currentMap = map;

                MinecraftServer server = ctx.getSource().getServer();
                ServerWorld world = ctx.getSource().getWorld();
                List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

                List<BlockPos> spawnPoints = getAllMatchingSpawns(world, AmongMapManager.MAP_SPAWN_BLOCK, map);
                if (spawnPoints.isEmpty()) {
                    ctx.getSource().sendFeedback(() -> Text.literal("§cNo spawns for map: " + map), false);
                    return 0;
                }

                Collections.shuffle(spawnPoints);
                for (int i = 0; i < players.size(); i++) {
                    ServerPlayerEntity player = players.get(i);
                    BlockPos pos = spawnPoints.get(i % spawnPoints.size());
                    player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
                }

                Collections.shuffle(players);
                int impostorCount = Math.max(1, players.size() * AmongCraftCommands.getImpostorPercentage() / 100);
                Collections.shuffle(players);

                for (int i = 0; i < players.size(); i++) {
                    ServerPlayerEntity p = players.get(i);
                    if (i < impostorCount) setPlayerImpostor(p);
                    else setPlayerCrewmate(p);
                }

                for (ServerPlayerEntity player : players) {
                    TaskProgressTracker.createTaskMapForCurrentMap(player, 1);
                    player.changeGameMode(GameMode.SURVIVAL);
                    if (crewmates.contains(player.getUuid())) {
                        StatusEffectInstance weakness = new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 60 * 60 * 100, 254, false, false, false);
                        player.addStatusEffect(weakness);
                    }
                }

                TaskProgressTracker.giveTaskMap(server);
                TaskProgressTracker.init(server, map);

                Map<Integer, List<Map.Entry<String, JsonObject>>> groupedRoles = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : allRoles.entrySet()) {
                    String name = entry.getKey();
                    JsonObject roleData = entry.getValue().getAsJsonObject();
                    int base = roleData.get("role-of").getAsInt();
                    groupedRoles.computeIfAbsent(base, k -> new ArrayList<>()).add(Map.entry(name, roleData));
                }

                assignSubRoles(players.stream().filter(p -> crewmates.contains(p.getUuid())).toList(), groupedRoles.getOrDefault(0, List.of()));
                assignSubRoles(players.stream().filter(p -> impostors.contains(p.getUuid())).toList(), groupedRoles.getOrDefault(1, List.of()));
                GameState.setRunning(true);
                for (ServerPlayerEntity player : players) {
                    UUID playerUUID = player.getUuid();
                    server = player.getServer();

                    boolean isImpostor = impostors.contains(playerUUID);
                    String role = getPlayerRole(playerUUID);

                    // 0 ticks: intro
                    TickDelayedExecutor.schedule(server, 0, () -> {
                        AmongMapManager.sendCutsceneToPlayer(
                                player,
                                new Identifier("amongcraft", "start.png"),
                                new Identifier("amongcraft", "sus.start"),
                                true,
                                null,
                                false
                        );
                        System.out.println("[runStart] Intro cutscene sent to " + player.getName().getString());
                    });

                    // +6s (120 ticks): base role
                    TickDelayedExecutor.schedule(server, 120, () -> {
                        Identifier baseBg = isImpostor ? new Identifier("amongcraft", "impostor.png") : new Identifier("amongcraft", "crewmate.png");
                        Identifier baseSound = isImpostor ? new Identifier("amongcraft", "sus.role_impostor") : new Identifier("amongcraft", "sus.role_crew");

                        AmongMapManager.sendCutsceneToPlayer(
                                player,
                                baseBg,
                                baseSound,
                                true,
                                Set.of(playerUUID),
                                isImpostor
                        );
                        System.out.println("[runStart] Base role cutscene sent to " + player.getName().getString());
                    });

                    // +12s (240 ticks): specific role
                    if (role != null && !role.isEmpty()) {
                        TickDelayedExecutor.schedule(server, 240, () -> {
                            Identifier roleBg = new Identifier("amongcraft", role.toLowerCase() + ".png");
                            Identifier roleSound = new Identifier("amongcraft", "sus.role_" + role.toLowerCase());

                            AmongMapManager.sendCutsceneToPlayer(
                                    player,
                                    roleBg,
                                    roleSound,
                                    true,
                                    Set.of(playerUUID),
                                    isImpostor
                            );
                            System.out.println("[runStart] Sub-role cutscene (" + role + ") sent to " + player.getName().getString());
                        });
                    } else {
                        System.out.println("[runStart] No sub-role for " + player.getName().getString());
                    }
                }


                server.getPlayerManager().broadcast(Text.literal("§aGame started with " + impostorCount + " impostors, on map: " + map), false);
                return 1;
            } else {
                ctx.getSource().sendFeedback(() -> Text.literal("§aThe Game is Already Running!"), false);
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.getSource().sendFeedback(() -> Text.literal("§cAn internal error occurred: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int runLobby(CommandContext<ServerCommandSource> ctx, String map) {
        try {
            TaskProgressTracker.currentMap = map;
            ServerWorld world = ctx.getSource().getWorld();
            List<ServerPlayerEntity> players = ctx.getSource().getServer().getPlayerManager().getPlayerList();
            List<BlockPos> lobbySpawns = getAllMatchingSpawns(world, AmongMapManager.LOBBY_SPAWN_BLOCK, map);

            if (lobbySpawns.isEmpty()) {
                ctx.getSource().sendFeedback(() -> Text.literal("§cNo lobby spawns for map: " + map), false);
                return 0;
            }

            Collections.shuffle(lobbySpawns);
            for (int i = 0; i < players.size(); i++) {
                ServerPlayerEntity player = players.get(i);
                BlockPos pos = lobbySpawns.get(i % lobbySpawns.size());
                player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
            }

            ctx.getSource().sendFeedback(() -> Text.literal("§aPlayers teleported to lobby for map " + map), false);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            ctx.getSource().sendFeedback(() -> Text.literal("§cAn internal error occurred: " + e.getMessage()), false);
            return 0;
        }
    }

    public static void sendCutsceneToPlayer(
            ServerPlayerEntity player,
            Identifier background,
            Identifier sound,
            boolean fading,
            Set<UUID> targetUuids,
            boolean redColor
    ) {
        PacketByteBuf buf = new PacketByteBuf(PacketByteBufs.create());

        buf.writeIdentifier(background);
        buf.writeIdentifier(sound);
        buf.writeBoolean(fading);
        buf.writeVarInt(targetUuids.size());
        for (UUID uuid : targetUuids) {
            buf.writeUuid(uuid);
        }
        buf.writeBoolean(redColor);

        player.networkHandler.sendPacket(new CustomPayloadS2CPacket(Amongcraft.CUTSCENE_PACKET_ID, buf));
    }



    public static void assignSubRoles(List<ServerPlayerEntity> players, List<Map.Entry<String, JsonObject>> roles) {
        Random random = new Random();
        Set<UUID> alreadyAssigned = new HashSet<>();

        for (Map.Entry<String, JsonObject> roleEntry : roles) {
            String roleName = roleEntry.getKey();
            JsonObject data = roleEntry.getValue();

            int maxCount = data.has("count") ? data.get("count").getAsInt() : Integer.MAX_VALUE;
            int percentage = data.has("percentage") ? data.get("percentage").getAsInt() : 100;

            int assigned = 0;

            List<ServerPlayerEntity> shuffled = new ArrayList<>(players);
            Collections.shuffle(shuffled);

            for (ServerPlayerEntity player : shuffled) {
                if (alreadyAssigned.contains(player.getUuid())) continue;
                if (assigned >= maxCount) break;

                if (random.nextInt(100) < percentage) {
                    AmongCraftCommands.setPlayerRole(player.getUuid(), roleName);
                    assigned++;

                    // Give role-based items
                    giveRoleItems(player, roleName);

                    alreadyAssigned.add(player.getUuid());

                    // Notify player of their subrole
                    player.sendMessage(Text.literal("Your role is: ").append(Text.literal(roleName).formatted(Formatting.AQUA)), false);
                }
            }
        }

        // Assign base roles to players who didn't get a subrole
        for (ServerPlayerEntity player : players) {
            if (!AmongCraftCommands.getPlayerRole(player.getUuid()).equals("Unknown")) continue;

            String baseRole = getBaseRole(player);
            AmongCraftCommands.setPlayerRole(player.getUuid(), baseRole);

            // Notify player of their base role
            player.sendMessage(Text.literal("Your role is: ").append(Text.literal(baseRole).formatted(Formatting.GREEN)), false);
        }
    }

    public static void giveRoleItems(ServerPlayerEntity player, String role) {
        switch (role) {
            case "Engineer":
                giveItem(player, "amongcraft:switch_went");
                break;
            case "Angel":
                Amongcraft.LOGGER.info(player.getEntityName() +" is an angel");
                break;
            case "Noisemaker":
                Amongcraft.LOGGER.info(player.getEntityName() +" is a noise maker");
                break;
            case "Scientist":
                giveItem(player, "amongcraft:tablet");
                break;
            case "Sheriff":
                giveItem(player, "amongcraft:gun");
                break;
            case "Jester":
                Amongcraft.LOGGER.info(player.getEntityName() +" is a jester");
                break;
            case "Shapeshifter":
                giveItem(player, "amongcraft:shapeshifter");
                break;
            case "Phantom":
                giveItem(player, "amongcraft:phantom");
                break;
            default:
                break;
        }
    }


    public static String getBaseRole(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (impostors.contains(uuid)) return "Impostor";
        else if (crewmates.contains(uuid)) return "Crewmate";
        return "Unknown";
    }


    public static List<BlockPos> getAllMatchingSpawns(World world, Block blockType, String map) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(BlockPos.ORIGIN, 300, 255, 300)) {
            if (world.getBlockState(pos).getBlock() == blockType) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null && be instanceof SpawnBlockEntity) {
                    String mapTag = ((SpawnBlockEntity) be).getMapTag();
                    if (map.equals(mapTag)) {
                        positions.add(pos.toImmutable());
                    }
                }
            }
        }
        return positions;
    }

    public static boolean isPlayerOutsideBounds(ServerPlayerEntity player, String map) {
        if (!MAP_BOUNDS.containsKey(map)) return false;
        Box box = MAP_BOUNDS.get(map);
        return !box.contains(player.getPos());
    }

    // Simple blocks and block entities
    public static class MapSpawnBlock extends BlockWithEntity {
        public MapSpawnBlock() {
            super(FabricBlockSettings.create().strength(0.5f).nonOpaque().collidable(false));
        }

        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new SpawnBlockEntity(pos, state);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
            if (world instanceof ServerWorld serverWorld && !world.isClient) {
                String map = AmongMapManager.getMapForPosition(serverWorld, pos);
                if (map != null) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof AmongMapManager.SpawnBlockEntity spawnBe) {
                        spawnBe.setMapTag(map);

                        Block block = serverWorld.getBlockState(pos).getBlock();
                        boolean isLobby = block instanceof AmongMapManager.LobbySpawnBlock;

                        AmongMapManager.saveBlockPlacement(serverWorld.getServer(), map, pos, isLobby ? "lobby" : "spawn");
                    }
                }
            }
            super.onPlaced(world, pos, state, placer, itemStack);
        }
    }

    public static class LobbySpawnBlock extends BlockWithEntity {
        public LobbySpawnBlock() {
            super(FabricBlockSettings.create().strength(0.5f).nonOpaque().collidable(false));
        }

        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
            return new SpawnBlockEntity(pos, state);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
            if (world instanceof ServerWorld serverWorld && !world.isClient) {
                String map = AmongMapManager.getMapForPosition(serverWorld, pos);
                if (map != null) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof AmongMapManager.SpawnBlockEntity spawnBe) {
                        spawnBe.setMapTag(map);

                        Block block = serverWorld.getBlockState(pos).getBlock();
                        boolean isLobby = block instanceof AmongMapManager.LobbySpawnBlock;

                        AmongMapManager.saveBlockPlacement(serverWorld.getServer(), map, pos, isLobby ? "lobby" : "spawn");
                    }
                }
            }
            super.onPlaced(world, pos, state, placer, itemStack);
        }
    }

    public static class SpawnBlockEntity extends BlockEntity {
        private String mapTag = "";
        private boolean isLobby = false;

        public SpawnBlockEntity(BlockPos pos, BlockState state) {
            super(SPAWN_BLOCK_ENTITY_TYPE, pos, state);
        }

        public String getMapTag() {
            return mapTag;
        }

        public void setMapTag(String mapTag) {
            this.mapTag = mapTag;
            markDirty();
        }

        public boolean isLobby() {
            return isLobby;
        }

        public void setLobby(boolean isLobby) {
            this.isLobby = isLobby;
            markDirty();
        }

        @Override
        public void readNbt(NbtCompound tag) {
            super.readNbt(tag);
            this.mapTag = tag.getString("MapTag");
            this.isLobby = tag.getBoolean("IsLobby");
        }

        @Override
        protected void writeNbt(NbtCompound tag) {
            super.writeNbt(tag);
            tag.putString("MapTag", mapTag);
            tag.putBoolean("IsLobby", isLobby);
        }
    }

    public static void saveMapBounds(MinecraftServer server, String map, BlockPos from, BlockPos to) {
        JsonObject root = loadData();
        JsonObject bounds = root.has("bounds") ? root.getAsJsonObject("bounds") : new JsonObject();

        JsonArray min = new JsonArray();
        min.add(Math.min(from.getX(), to.getX()));
        min.add(Math.min(from.getY(), to.getY()));
        min.add(Math.min(from.getZ(), to.getZ()));

        JsonArray max = new JsonArray();
        max.add(Math.max(from.getX(), to.getX()));
        max.add(Math.max(from.getY(), to.getY()));
        max.add(Math.max(from.getZ(), to.getZ()));

        JsonObject box = new JsonObject();
        box.add("min", min);
        box.add("max", max);

        bounds.add(map, box);
        root.add("bounds", bounds);
        saveData(root);

        // Set NBT tags for all blocks in the region
        ServerWorld world = server.getOverworld(); // or getWorld from context
        BlockPos.stream(from, to).forEach(pos -> {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SpawnBlockEntity spawnBe) {
                spawnBe.setMapTag(map);
                saveBlockPlacement(server, map, pos, world.getBlockState(pos).getBlock() instanceof LobbySpawnBlock ? "lobby" : "spawn");
            }
        });
    }

    public static void saveBlockPlacement(MinecraftServer server, String map, BlockPos pos, String type) {
        JsonObject root = loadData();
        JsonArray blocks = root.has("blocks") ? root.getAsJsonArray("blocks") : new JsonArray();

        JsonObject entry = new JsonObject();
        entry.addProperty("map", map);
        entry.addProperty("type", type);

        JsonArray posArr = new JsonArray();
        posArr.add(pos.getX());
        posArr.add(pos.getY());
        posArr.add(pos.getZ());
        entry.add("pos", posArr);

        JsonObject nbt = new JsonObject();
        nbt.addProperty("MapTag", map);
        entry.add("nbt", nbt);

        blocks.add(entry);
        root.add("blocks", blocks);
        saveData(root);
    }

    public static String getMapForPosition(ServerWorld world, BlockPos pos) {
        JsonObject root = loadData();
        if (!root.has("bounds")) return null;

        JsonObject bounds = root.getAsJsonObject("bounds");

        for (Map.Entry<String, JsonElement> entry : bounds.entrySet()) {
            String map = entry.getKey();
            JsonObject box = entry.getValue().getAsJsonObject();
            Vec3i min = vecFromJson(box.getAsJsonArray("min"));
            Vec3i max = vecFromJson(box.getAsJsonArray("max"));

            if (pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
                return map;
            }
        }

        return null;
    }

    private static Vec3i vecFromJson(JsonArray arr) {
        return new Vec3i(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }

    private static JsonObject loadData() {
        try {
            if (Files.exists(DATA_FILE)) {
                return JsonParser.parseReader(Files.newBufferedReader(DATA_FILE)).getAsJsonObject();
            }
        } catch (IOException ignored) {}
        return new JsonObject();
    }

    private static void saveData(JsonObject data) {
        try {
            // Ensure parent directory exists
            if (!Files.exists(DATA_FILE.getParent())) {
                Files.createDirectories(DATA_FILE.getParent());
            }

            // Now write
            try (Writer writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
