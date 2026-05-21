package me.tg.amongcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Auto-migrates legacy {@code amongcraft:task_button} blocks (the old
 * single blockstate-driven block) into the new per-task blocks.
 *
 * On every chunk load it reads {@code amongcraft/taskblocks.json} for that
 * world and, for each saved task position that falls inside the loaded chunk,
 * replaces a {@link LegacyTaskBlock} found there with the matching per-task
 * {@link Amongcraft.TaskBlock}, preserving the facing.
 *
 * This is cheap: it only inspects the known task positions, not every block.
 */
public final class TaskBlockMigrator {

    private TaskBlockMigrator() {}

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(TaskBlockMigrator::onChunkLoad);
    }

    private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        try {
            Path file = world.getServer()
                    .getSavePath(WorldSavePath.ROOT)
                    .resolve("amongcraft/taskblocks.json");
            if (!Files.exists(file)) return;

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) return;
                json = parsed.getAsJsonObject();
            }

            ChunkPos chunkPos = chunk.getPos();
            int minX = chunkPos.getStartX();
            int maxX = chunkPos.getEndX();
            int minZ = chunkPos.getStartZ();
            int maxZ = chunkPos.getEndZ();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String[] coords = entry.getKey().split(",");
                if (coords.length != 3) continue;

                int x;
                int y;
                int z;
                try {
                    x = Integer.parseInt(coords[0].trim());
                    y = Integer.parseInt(coords[1].trim());
                    z = Integer.parseInt(coords[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }

                // Only touch positions inside this chunk.
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;

                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof LegacyTaskBlock)) continue;

                Amongcraft.TaskBlock.TaskType type = state.get(LegacyTaskBlock.TASK);
                net.minecraft.util.math.Direction facing = state.get(LegacyTaskBlock.FACING);

                Block newBlock = Amongcraft.taskBlock(type);
                if (newBlock == null) continue;

                world.setBlockState(pos, newBlock.getDefaultState()
                        .with(Amongcraft.TaskBlock.FACING, facing));
            }
        } catch (Exception e) {
            Amongcraft.LOGGER.warn("Task block migration failed for chunk: {}", e.toString());
        }
    }
}
