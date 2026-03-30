package me.tg.amongcraft;

import me.tg.amongcraft.Amongcraft;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;

public class TaskPacket {
    private static final File TASK_JSON = new File("config/amongcraft/tasks.json");
    private static final Gson GSON = new Gson();
    static Map<String, String> taskMap = new HashMap<>();

    public static void init() {
        loadTasks();
    }

    private static void loadTasks() {
        if (TASK_JSON.exists()) {
            try {
                String content = Files.readString(TASK_JSON.toPath());
                taskMap = GSON.fromJson(content, Map.class);
            } catch (IOException e) {
                Amongcraft.LOGGER.error("Failed to load task JSON", e);
            }
        } else {
            // Create default if not exists
            taskMap.put("default", "https://tggamesyt.dev/tasks/default.html"); // done
            taskMap.put("align_engine_output", "https://tggamesyt.dev/tasks/align_engine_output.html"); // done
            taskMap.put("calibrate", "https://tggamesyt.dev/tasks/calibrate.html"); // done
            taskMap.put("chart_course", "https://tggamesyt.dev/tasks/chart_course.html"); // done
            taskMap.put("chute", "https://tggamesyt.dev/tasks/chute.html"); // done
            taskMap.put("clean_vent", "https://tggamesyt.dev/tasks/clean_vent.html"); // TODO make went open this
            taskMap.put("clear_asteroids", "https://tggamesyt.dev/tasks/clear_asteroids.html"); // done
            taskMap.put("divert_power_1", "https://tggamesyt.dev/tasks/divert_power_1.html"); // done
            taskMap.put("divert_power_2", "https://tggamesyt.dev/tasks/divert_power_2.html"); // done
            taskMap.put("download", "https://tggamesyt.dev/tasks/download.html"); // done
            taskMap.put("fuelcan", "https://tggamesyt.dev/tasks/fuelcan.html"); // done
            taskMap.put("inspect_sample", "https://tggamesyt.dev/tasks/inspect_sample.html"); // done
            taskMap.put("o2_filter", "https://tggamesyt.dev/tasks/o2_filter.html"); // done
            taskMap.put("scan", "https://tggamesyt.dev/tasks/scan.html?player=${player-name}"); // done
            taskMap.put("shields", "https://tggamesyt.dev/tasks/shields.html"); // done
            taskMap.put("stabilize_steering", "https://tggamesyt.dev/tasks/stabilize_steering.html"); // done
            taskMap.put("start_reactor", "https://tggamesyt.dev/tasks/start_reactor.html"); // done
            taskMap.put("swipe_card", "https://tggamesyt.dev/tasks/swipe_card.html"); // TODO admin table
            taskMap.put("task1", "https://tggamesyt.dev/tasks/task1.html"); // done
            taskMap.put("task2", "https://tggamesyt.dev/tasks/task2.html"); // done
            taskMap.put("unlock_manifolds", "https://tggamesyt.dev/tasks/unlock_manifolds.html"); // done
            taskMap.put("upload", "https://tggamesyt.dev/tasks/upload.html"); // done
            taskMap.put("wires", "https://tggamesyt.dev/tasks/wires.html"); // done
            taskMap.put("fuelengine", "https://tggamesyt.dev/tasks/fuelcan.html");
            saveTasks();
        }
    }

    public static void saveTasks() {
        try (FileWriter writer = new FileWriter(TASK_JSON)) {
            writer.write(GSON.toJson(taskMap));
        } catch (IOException e) {
            Amongcraft.LOGGER.error("Failed to save task JSON", e);
        }
    }

    public static void sendTaskOpenPacket(ServerPlayerEntity player, String taskId, BlockPos pos) {
        String url = taskMap.getOrDefault(taskId, "https://tg.is-a.dev/tasks/default.html");
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(url);
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, Amongcraft.OPEN_BROWSER_PACKET_ID, buf);
        Amongcraft.LOGGER.info("Sent Packet!");
    }
}
