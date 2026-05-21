package dev.tggamesyt.amongcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Auto-migrates legacy {@code amongcraft:task_button} blocks to the new
 * per-task blocks.
 *
 * <p>Block edits are deliberately NOT performed inside the chunk-load
 * callback: calling {@code setBlockState} while a chunk is still loading can
 * deadlock world loading (a neighbour update can trigger synchronous loading
 * of an adjacent chunk). Instead, candidate positions are queued during chunk
 * load and converted a few at a time on the normal server tick, when the
 * world is safe to modify.</p>
 */
public final class TaskBlockMigrator {

    private record Pending(ServerWorld world, BlockPos pos) {}

    private static final Queue<Pending> QUEUE = new ArrayDeque<>();
    private static final int PER_TICK = 64;

    private TaskBlockMigrator() {}

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(TaskBlockMigrator::onChunkLoad);
        ServerTickEvents.END_SERVER_TICK.register(TaskBlockMigrator::onTick);
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

            ChunkPos cp = chunk.getPos();
            int minX = cp.getStartX();
            int maxX = cp.getEndX();
            int minZ = cp.getStartZ();
            int maxZ = cp.getEndZ();

            for (String key : json.keySet()) {
                String[] c = key.split(",");
                if (c.length != 3) continue;
                int x;
                int y;
                int z;
                try {
                    x = Integer.parseInt(c[0].trim());
                    y = Integer.parseInt(c[1].trim());
                    z = Integer.parseInt(c[2].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                synchronized (QUEUE) {
                    QUEUE.add(new Pending(world, new BlockPos(x, y, z)));
                }
            }
        } catch (Exception e) {
            Amongcraft.LOGGER.warn("Task block migration scan failed: {}", e.toString());
        }
    }

    private static void onTick(MinecraftServer server) {
        for (int i = 0; i < PER_TICK; i++) {
            Pending p;
            synchronized (QUEUE) {
                p = QUEUE.poll();
            }
            if (p == null) return;
            try {
                migrate(p.world(), p.pos());
            } catch (Exception e) {
                Amongcraft.LOGGER.warn("Task block migration failed at {}: {}", p.pos(), e.toString());
            }
        }
    }

    private static void migrate(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof LegacyTaskBlock)) return;

        Amongcraft.TaskBlock.TaskType type = state.get(LegacyTaskBlock.TASK);
        Direction facing = state.get(LegacyTaskBlock.FACING);
        Block newBlock = Amongcraft.taskBlock(type);
        if (newBlock == null) return;

        world.setBlockState(pos, newBlock.getDefaultState()
                .with(Amongcraft.TaskBlock.FACING, facing), Block.NOTIFY_LISTENERS);
    }
}
