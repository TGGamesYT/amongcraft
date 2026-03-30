package me.tg.amongcraft;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;


public class TaskProgressTracker {

    private static final Gson GSON = new Gson();
    static final Map<UUID, Map<String, Set<String>>> playerTaskProgress = new HashMap<>();
    private static MapState currentTaskMapState = null;
    private static ItemStack currentTaskMapItem = null;
    private static final Map<UUID, Set<String>> assignedTaskIds = new HashMap<>();

    public static String currentMap = "default";

    static MinecraftServer server;

    public static void init(MinecraftServer serverInstance, String mapName) {
        server = serverInstance;
        currentMap = mapName;
        playerTaskProgress.clear();
        assignedTaskIds.clear();

        for (UUID playerId : AmongCraftCommands.crewmates) {
            generateTasksForPlayer(playerId);
        }
    }

    public static void tick() {
        if (server == null ) return; // || !GameState.isRunning()
        if (Objects.equals(SettingsManager.get("task-updates").getAsString(), "always")) {
        updateXpBars();
        }
    }

    public static Map<String, Vec3d> getTaskPositions(MinecraftServer server) {
        Map<String, Vec3d> taskPositions = new HashMap<>();

        try {
            Path file = server.getSavePath(WorldSavePath.ROOT).resolve("amongcraft/taskblocks.json");
            if (!Files.exists(file)) return taskPositions;

            Reader reader = Files.newBufferedReader(file);
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String posString = entry.getKey(); // e.g. "123,64,456"
                JsonElement value = entry.getValue();

                if (value.isJsonObject()) {
                    JsonObject obj = value.getAsJsonObject();
                    String mapName = obj.has("map") ? obj.get("map").getAsString() : "";
                    String taskId = obj.has("task") ? obj.get("task").getAsString() : "";

                    if (!mapName.equals(currentMap)) continue;

                    String[] coords = posString.split(",");
                    if (coords.length != 3) continue;

                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    double z = Double.parseDouble(coords[2]);

                    taskPositions.put(taskId, new Vec3d(x, y, z));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return taskPositions;
    }

    public static MapState createTaskMapForCurrentMap(ServerPlayerEntity player, int scale) {
        try {
            ServerWorld world = player.getServerWorld();

            int centerX = (int) player.getX();
            int centerZ = (int) player.getZ();
            byte scaleByte = (byte) scale;

            boolean showIcons = false;
            boolean unlimitedTracking = false;

            RegistryKey<World> dimension = world.getRegistryKey();

            // This creates a map item and internally sets up the map state
            ItemStack mapStack = FilledMapItem.createMap(world, centerX, centerZ, scaleByte, showIcons, unlimitedTracking);
            MapState mapState = FilledMapItem.getMapState(mapStack, world);

            if (mapState == null) return null;

            // Add banners for each task
            Map<String, Vec3d> tasks = getTaskPositions(world.getServer());
            for (Map.Entry<String, Vec3d> entry : tasks.entrySet()) {
                Vec3d pos = entry.getValue();

                int mapX = (int) ((pos.x - centerX) / (1 << scale));
                int mapZ = (int) ((pos.z - centerZ) / (1 << scale));

                mapState.addBanner(world, new BlockPos(mapX, 0, mapZ));
            }

            currentTaskMapState = mapState;
            currentTaskMapItem = mapStack;

            return mapState;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void giveTaskMap(MinecraftServer server) {
        if (currentTaskMapItem == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getInventory().offerOrDrop(currentTaskMapItem.copy());
        }
    }

    public static void removeTaskMap(MinecraftServer server) {
        if (currentTaskMapItem == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getInventory().remove(
                    stack -> stack.getItem() == currentTaskMapItem.getItem(),
                    Integer.MAX_VALUE,
                    player.getInventory()
            );
        }
    }




    public static void updateXpBars() {
        Set<String> requiredTasks = getAllTasksForMap(currentMap, true);
        int totalTasks = requiredTasks.size() * AmongCraftCommands.crewmates.size();

        if (totalTasks == 0) return;

        int doneTasks = 0;

        for (UUID playerId : AmongCraftCommands.crewmates) {
            Map<String, Set<String>> taskMap = playerTaskProgress.getOrDefault(playerId, Collections.emptyMap());
            Set<String> completed = taskMap.getOrDefault(currentMap, Collections.emptySet());
            for (String taskId : requiredTasks) {
                if (completed.contains(taskId)) {
                    doneTasks++;
                }
            }
        }

        float progress = (float) doneTasks / totalTasks;
        int xpPoints = Math.round(progress * 6);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.setExperienceLevel(0);
            player.setExperiencePoints(xpPoints);
        }
    }


    public static void markTaskAsDone(ServerPlayerEntity player, String taskId, BlockPos blockPos) {
        String coords = blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("taskblocks.json");

        Amongcraft.LOGGER.info("running markasDone(ServerEntity " + player.getName() + ", String " + taskId + ", BlockPos " + blockPos + ")");

        try {
            Amongcraft.LOGGER.info("1");
            if (!Files.exists(file)) {
                Amongcraft.LOGGER.warn("Task blocks file not found");
                return;
            }
            Amongcraft.LOGGER.info("2");
            JsonObject json = JsonParser.parseReader(Files.newBufferedReader(file)).getAsJsonObject();
            JsonElement element = json.get(coords);
            if (element == null || !element.isJsonObject()) {
                Amongcraft.LOGGER.warn("No task found at position " + coords);
                return;
            }

            Amongcraft.LOGGER.info("2");

            JsonObject taskData = element.getAsJsonObject();
            if (!taskData.has("task") || !taskData.has("map")) return;
            Amongcraft.LOGGER.info("3");
            String map = taskData.get("map").getAsString();
            String baseTaskId = taskData.get("task").getAsString();
            if (!map.equals(currentMap)) return;
            Amongcraft.LOGGER.info("4");
            String fullTaskId = baseTaskId + "@" + coords;

            UUID playerId = player.getUuid();
            playerTaskProgress.putIfAbsent(playerId, new HashMap<>());
            Map<String, Set<String>> taskMap = playerTaskProgress.get(playerId);
            taskMap.putIfAbsent(currentMap, new HashSet<>());

            boolean newTask = taskMap.get(currentMap).add(fullTaskId);

            if (newTask) {
                Amongcraft.LOGGER.info("5");
                String playerName = player.getName().getString();
                Set<String> requiredTasks = getAllTasksForMap(currentMap, true);
                int totalPerPlayer = requiredTasks.size();
                int playerDone = taskMap.get(currentMap).size();

                Amongcraft.LOGGER.info(playerName + " completed task: " + fullTaskId);
                Amongcraft.LOGGER.info(playerName + " has completed " + playerDone + " out of " + totalPerPlayer + " tasks");

                // Count all completed tasks by crewmates
                int totalDone = 0;
                for (UUID crewmateId : AmongCraftCommands.crewmates) {
                    Map<String, Set<String>> crewmateTasks = playerTaskProgress.getOrDefault(crewmateId, Collections.emptyMap());
                    Set<String> doneTasks = crewmateTasks.getOrDefault(currentMap, Collections.emptySet());
                    for (String task : requiredTasks) {
                        if (doneTasks.contains(task)) {
                            totalDone++;
                        }
                    }
                }

                int totalPossible = totalPerPlayer * AmongCraftCommands.crewmates.size();
                Amongcraft.LOGGER.info("All crewmates have completed " + totalDone + " out of " + totalPossible + " tasks");

                checkWinCondition();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static boolean hasPlayerCompletedTask(ServerPlayerEntity player, BlockPos pos) {
        String coords = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("taskblocks.json");

        try {
            if (!Files.exists(file)) return false;
            JsonObject json = JsonParser.parseReader(Files.newBufferedReader(file)).getAsJsonObject();
            JsonElement element = json.get(coords);
            if (element == null || !element.isJsonObject()) return false;

            JsonObject taskData = element.getAsJsonObject();
            if (!taskData.has("task") || !taskData.has("map")) return false;

            String map = taskData.get("map").getAsString();
            String taskId = taskData.get("task").getAsString();
            if (!map.equals(currentMap)) return false;

            String fullTaskId = taskId + "@" + coords;

            UUID playerId = player.getUuid();
            return playerTaskProgress
                    .getOrDefault(playerId, Collections.emptyMap())
                    .getOrDefault(currentMap, Collections.emptySet())
                    .contains(fullTaskId);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void checkWinCondition() {
        Set<String> requiredTasks = getAllTasksForMap(currentMap, true);
        Amongcraft.LOGGER.info("Required tasks for map " + currentMap + ": " + requiredTasks);
        if (requiredTasks.isEmpty()) {
            Amongcraft.LOGGER.warn("No tasks found for map: " + currentMap);
            return;
        }

        for (UUID playerId : AmongCraftCommands.crewmates) {
            playerTaskProgress.putIfAbsent(playerId, new HashMap<>());
            Map<String, Set<String>> taskMap = playerTaskProgress.get(playerId);

            taskMap.putIfAbsent(currentMap, new HashSet<>());
            Set<String> completed = taskMap.get(currentMap);

            if (!completed.containsAll(requiredTasks)) {
                return;
            }
        }

        triggerCrewmateWin();
    }

    private static Set<String> getAllTasksForMap(String map, boolean getcoords) {
        Set<String> tasks = new HashSet<>();
        for (UUID player : AmongCraftCommands.crewmates) {
            tasks.addAll(assignedTaskIds.getOrDefault(player, Collections.emptySet()));
        }
        return tasks;
    }



    private static void triggerCrewmateWin() {
        Amongcraft.LOGGER.info("All crewmates completed their tasks! Crewmates win!");
        server.getPlayerManager().broadcast(Text.literal("§aCrewmates win!"), false);
        AmongCraftWinListener.killAll(server);
        GameState.setRunning(false);
    }

    public static void resetProgress() {
        playerTaskProgress.clear();
    }
    public static void generateTasksForPlayer(UUID playerId) {
        Map<String, List<String>> tasksByRarity = new HashMap<>();
        tasksByRarity.put("common", new ArrayList<>());
        tasksByRarity.put("short", new ArrayList<>());
        tasksByRarity.put("long", new ArrayList<>());

        try {
            Path file = server.getSavePath(WorldSavePath.ROOT).resolve("taskblocks.json");
            if (!Files.exists(file)) return;

            JsonObject json = JsonParser.parseReader(Files.newBufferedReader(file)).getAsJsonObject();

            // Map from identifier -> all positions
            Map<String, List<String>> taskGroups = new HashMap<>();
            Map<String, String> identifierRarity = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String coords = entry.getKey();
                JsonObject data = entry.getValue().getAsJsonObject();

                if (!data.has("map") || !data.has("identifier") || !data.has("rarity")) continue;
                if (!data.get("map").getAsString().equals(currentMap)) continue;

                String identifier = data.get("identifier").getAsString();
                String rarity = data.get("rarity").getAsString();

                taskGroups.computeIfAbsent(identifier, k -> new ArrayList<>()).add(identifier + "@" + coords);
                identifierRarity.put(identifier, rarity);
            }

            // Bucket identifiers by rarity
            for (Map.Entry<String, String> entry : identifierRarity.entrySet()) {
                String identifier = entry.getKey();
                String rarity = entry.getValue().toLowerCase();
                if (tasksByRarity.containsKey(rarity)) {
                    tasksByRarity.get(rarity).add(identifier);
                }
            }

            int commonCount = SettingsManager.get("tasks-common").getAsInt();
            int shortCount = SettingsManager.get("tasks-short").getAsInt();
            int longCount = SettingsManager.get("tasks-long").getAsInt();

            Set<String> selectedIdentifiers = new HashSet<>();
            Random rand = new Random();

            selectedIdentifiers.addAll(pickRandomIdentifiers(tasksByRarity.get("common"), commonCount, rand));
            selectedIdentifiers.addAll(pickRandomIdentifiers(tasksByRarity.get("short"), shortCount, rand));
            selectedIdentifiers.addAll(pickRandomIdentifiers(tasksByRarity.get("long"), longCount, rand));

            Set<String> fullTaskIds = new HashSet<>();
            for (String identifier : selectedIdentifiers) {
                List<String> fulls = taskGroups.get(identifier);
                if (fulls != null) fullTaskIds.addAll(fulls);
            }

            assignedTaskIds.put(playerId, fullTaskIds);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> pickRandomIdentifiers(List<String> pool, int count, Random rand) {
        Collections.shuffle(pool, rand);
        return pool.subList(0, Math.min(count, pool.size()));
    }

}
