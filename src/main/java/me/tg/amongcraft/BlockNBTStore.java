package me.tg.amongcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BlockNBTStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, JsonObject> cache = new HashMap<>();

    private static File getDataFile(World world) {
        Path savePath = world.getServer().getSavePath(WorldSavePath.ROOT);
        return new File(savePath.toFile(), "amongcraft/sabotage_blocks.json");
    }

    public static void save(World world, BlockPos pos, JsonObject data) {
        try {
            File file = getDataFile(world);
            JsonObject root = file.exists() ? GSON.fromJson(new FileReader(file), JsonObject.class) : new JsonObject();

            root.add(pos.toShortString(), data);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            Amongcraft.LOGGER.error("Failed to save sabotage block data", e);
        }
    }

    public static JsonObject load(World world, BlockPos pos) {
        try {
            File file = getDataFile(world);
            if (!file.exists()) return null;

            JsonObject root = GSON.fromJson(new FileReader(file), JsonObject.class);
            return root.getAsJsonObject(pos.toShortString());
        } catch (Exception e) {
            Amongcraft.LOGGER.error("Failed to load sabotage block data", e);
            return null;
        }
    }
}
