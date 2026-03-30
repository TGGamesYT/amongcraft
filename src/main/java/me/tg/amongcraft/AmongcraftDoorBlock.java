package me.tg.amongcraft;

import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class AmongcraftDoorBlock extends Block {

    public static final Map<String, List<DoorEntry>> DOOR_MAP = new HashMap<>();
    public static final File SAVE_FILE = new File("config/amongcraft/amongcraft_doors.json");
    private static boolean loaded = false;
    public static MinecraftServer server;

    public AmongcraftDoorBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState();
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, ItemStack stack) {
        if (world.isClient || !(world instanceof ServerWorld)) return;

        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains("block") || !tag.contains("id")) return;

        String blockNbt = tag.getString("block");
        String id = tag.getString("id");

        BlockState realBlock = parseBlockState(blockNbt);
        if (realBlock != null) {
            world.setBlockState(pos, realBlock, 3);

            loadDoors(); // ensure loaded

            DOOR_MAP.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new DoorEntry(pos.toImmutable(), blockNbt));

            saveDoors();
        }
    }

    // === UTILITIES ===

    private static final Map<String, Boolean> doorStates = new HashMap<>();

    public static void toggleDoor(String id) {
        boolean currentState = doorStates.getOrDefault(id, true);

        if (currentState) {
            setDoor(id, true); // turn off
            doorStates.put(id, true);
        } else {
            setDoor(id, false); // turn on
            doorStates.put(id, false);
        }
    }

    public static void setDoor(String id, boolean closed) {
        doorStates.put(id, closed);
        loadDoors();
        List<DoorEntry> doors = DOOR_MAP.get(id);
        if (doors == null) return;

        for (DoorEntry entry : doors) {
            ServerWorld world = getServerWorld(server, entry.pos);

            if (world == null) continue;

            BlockState current = world.getBlockState(entry.pos);
            if (closed) {
                BlockState desired = parseBlockState(entry.nbt);
                if (desired != null && current.isAir()) {
                    world.setBlockState(entry.pos, desired, 3);
                }
            } else {
                if (!current.isAir()) {
                    world.setBlockState(entry.pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    // === NBT PARSER ===

    public static BlockState parseBlockState(String nbtStr) {
        try {
            int i = nbtStr.indexOf('{');
            String id = i == -1 ? nbtStr : nbtStr.substring(0, i);
            Identifier blockId = new Identifier(id);
            Block block = Registries.BLOCK.get(blockId);
            if (block == Blocks.AIR) return Blocks.AIR.getDefaultState();

            NbtCompound fullTag = new NbtCompound();
            fullTag.putString("Name", blockId.toString());

            if (i != -1) {
                String tagStr = nbtStr.substring(i);
                NbtCompound rawProps = net.minecraft.nbt.StringNbtReader.parse(tagStr);
                fullTag.put("Properties", rawProps);
            }

            return NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), fullTag);
        } catch (Exception e) {
            System.err.println("Failed to parse block state from: " + nbtStr);
            e.printStackTrace();
            return null;
        }
    }


    // === FILE STORAGE ===

    public static void loadDoors() {
        if (loaded) return;
        loaded = true;

        if (!SAVE_FILE.exists()) return;
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String id : root.keySet()) {
                List<DoorEntry> list = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray(id)) {
                    JsonObject obj = el.getAsJsonObject();
                    BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                    String nbt = obj.get("nbt").getAsString();
                    list.add(new DoorEntry(pos, nbt));
                }
                DOOR_MAP.put(id, list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveDoors() {
        try {
            JsonObject root = new JsonObject();
            for (var entry : DOOR_MAP.entrySet()) {
                JsonArray arr = new JsonArray();
                for (DoorEntry d : entry.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("x", d.pos.getX());
                    o.addProperty("y", d.pos.getY());
                    o.addProperty("z", d.pos.getZ());
                    o.addProperty("nbt", d.nbt);
                    arr.add(o);
                }
                root.add(entry.getKey(), arr);
            }

            SAVE_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(SAVE_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === HELPERS ===

    public record DoorEntry(BlockPos pos, String nbt) {}

    public static ServerWorld getServerWorld(MinecraftServer server, BlockPos pos) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.isChunkLoaded(pos)) return world;
        }
        return null;
    }

    public static List<String> getAllDoorIds() {
        loadDoors();
        return new ArrayList<>(DOOR_MAP.keySet());
    }
}
