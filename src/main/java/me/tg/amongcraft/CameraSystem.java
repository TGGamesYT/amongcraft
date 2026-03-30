package me.tg.amongcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CameraSystem {

    public static final Identifier CAMERA_PACKET_ID = new Identifier("amongcraft", "camera_update");

    public static final Block CAMERA_BLOCK = new CameraBlock(Block.Settings.create().strength(2f));
    public static final Block MONITOR_BLOCK = new MonitorBlock(Block.Settings.create().strength(3f));


    private MinecraftServer server;

    static Path saveFile;

    // Data storage
    private static final Map<String, List<CameraData>> cameras = new HashMap<>();
    private static final Map<String, List<MonitorData>> monitors = new HashMap<>();

    // For quick lookup of positions -> data to remove on block break
    private static final Map<BlockPos, CameraData> cameraPosMap = new HashMap<>();
    private static final Map<BlockPos, MonitorData> monitorPosMap = new HashMap<>();

    // Must be called on world load or server start manually if you want
    public void loadData(MinecraftServer server) {
        this.server = server;
        this.saveFile = server.getSavePath(WorldSavePath.ROOT).resolve("camerasystem_data.dat");
        cameras.clear();
        monitors.clear();
        cameraPosMap.clear();
        monitorPosMap.clear();

        if (Files.exists(saveFile)) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(saveFile))) {
                NbtCompound root = NbtIo.readCompressed(in);
                loadFromNbt(root);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFromNbt(NbtCompound root) {
        NbtList camsList = root.getList("cameras", 10);
        for (int i = 0; i < camsList.size(); i++) {
            NbtCompound camNbt = camsList.getCompound(i);
            CameraData cam = new CameraData();
            cam.identifier = camNbt.getString("identifier");
            cam.num = camNbt.getInt("num");
            cam.pos = NbtHelper.toBlockPos(camNbt.getCompound("pos"));
            cam.pitch = camNbt.getFloat("pitch");
            cam.yaw = camNbt.getFloat("yaw");
            cameras.computeIfAbsent(cam.identifier, k -> new ArrayList<>()).add(cam);
            cameraPosMap.put(cam.pos, cam);
        }

        NbtList monsList = root.getList("monitors", 10);
        for (int i = 0; i < monsList.size(); i++) {
            NbtCompound monNbt = monsList.getCompound(i);
            MonitorData mon = new MonitorData();
            mon.identifier = monNbt.getString("identifier");
            mon.pos = NbtHelper.toBlockPos(monNbt.getCompound("pos"));
            mon.type = monNbt.getBoolean("type");
            mon.maxCams = monNbt.getInt("maxCams");
            mon.selectedCamIndex = monNbt.getInt("selectedCamIndex");
            monitors.computeIfAbsent(mon.identifier, k -> new ArrayList<>()).add(mon);
            monitorPosMap.put(mon.pos, mon);
        }
    }

    private static void saveData() {
        try {
            NbtCompound root = new NbtCompound();

            NbtList camsList = new NbtList();
            cameras.values().forEach(list -> list.forEach(cam -> {
                NbtCompound camNbt = new NbtCompound();
                camNbt.putString("identifier", cam.identifier);
                camNbt.putInt("num", cam.num);
                camNbt.put("pos", NbtHelper.fromBlockPos(cam.pos));
                camNbt.putFloat("pitch", cam.pitch);
                camNbt.putFloat("yaw", cam.yaw);
                camsList.add(camNbt);
            }));
            root.put("cameras", camsList);

            NbtList monsList = new NbtList();
            monitors.values().forEach(list -> list.forEach(mon -> {
                NbtCompound monNbt = new NbtCompound();
                monNbt.putString("identifier", mon.identifier);
                monNbt.put("pos", NbtHelper.fromBlockPos(mon.pos));
                monNbt.putBoolean("type", mon.type);
                monNbt.putInt("maxCams", mon.maxCams);
                monNbt.putInt("selectedCamIndex", mon.selectedCamIndex);
                monsList.add(monNbt);
            }));
            root.put("monitors", monsList);

            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(saveFile))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static CameraSystem INSTANCE;

    public static CameraSystem getInstance() {
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new CameraSystem();
    }

    private CameraSystem() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server != null) {
                this.server = server;
                onEndTick(server);
            }
        });
    }

    // Server tick updates camera yaw and sends updates to players
    public void onEndTick(MinecraftServer server) {
        if (this.server == null) this.server = server;

        // Rotate cameras
        cameras.values().forEach(list -> list.forEach(cam -> {
            cam.yaw += 1.5f;
            if (cam.yaw > 360f) cam.yaw -= 360f;
        }));

        // Send updates to players who are in the same world as any monitors
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            World world = player.getWorld();

            // Collect monitors in player's world
            List<MonitorData> worldMonitors = new ArrayList<>();
            for (List<MonitorData> monList : monitors.values()) {
                for (MonitorData mon : monList) {
                    if (mon.pos.getSquaredDistance(player.getPos()) < 256 * 256 // 256 block radius for example
                            && world.getDimension().equals(player.getWorld().getDimension())) {
                        worldMonitors.add(mon);
                    }
                }
            }

            if (!worldMonitors.isEmpty()) {
                sendCameraUpdatePacket(player, worldMonitors);
            }
        }
    }

    // Call this when a camera block is placed
    public void onCameraBlockPlaced(BlockPos pos, String identifier, int num) {
        if (num < 1 || num > 7) throw new IllegalArgumentException("Camera num must be 1-7");
        CameraData cam = new CameraData();
        cam.pos = pos;
        cam.identifier = identifier;
        cam.num = num;
        cam.pitch = 20f;
        cam.yaw = 0f;
        cameras.computeIfAbsent(identifier, k -> new ArrayList<>()).add(cam);
        cameraPosMap.put(pos, cam);
        saveData();
    }

    // Call this when a monitor block is placed
    public void onMonitorBlockPlaced(BlockPos pos, String identifier, boolean type, int maxCams) {
        if (!type && (maxCams < 4 || maxCams > 7)) throw new IllegalArgumentException("maxCams must be 4-7 if type=0b");
        MonitorData mon = new MonitorData();
        mon.pos = pos;
        mon.identifier = identifier;
        mon.type = type;
        mon.maxCams = maxCams;
        mon.selectedCamIndex = 0;
        monitors.computeIfAbsent(identifier, k -> new ArrayList<>()).add(mon);
        monitorPosMap.put(pos, mon);
        saveData();
    }

    // Call when block is broken, remove from data
    public static void onBlockBroken(BlockPos pos) {
        if (cameraPosMap.containsKey(pos)) {
            CameraData cam = cameraPosMap.remove(pos);
            List<CameraData> list = cameras.get(cam.identifier);
            if (list != null) {
                list.remove(cam);
                if (list.isEmpty()) cameras.remove(cam.identifier);
            }
            saveData();
        }
        if (monitorPosMap.containsKey(pos)) {
            MonitorData mon = monitorPosMap.remove(pos);
            List<MonitorData> list = monitors.get(mon.identifier);
            if (list != null) {
                list.remove(mon);
                if (list.isEmpty()) monitors.remove(mon.identifier);
            }
            saveData();
        }
    }

    private void sendCameraUpdatePacket(ServerPlayerEntity player, List<MonitorData> monitorsInRange) {
        PacketByteBuf buf = new PacketByteBuf(PacketByteBufs.create());
        buf.writeInt(monitorsInRange.size());

        for (MonitorData mon : monitorsInRange) {
            buf.writeBlockPos(mon.pos);
            buf.writeBoolean(mon.type);
            buf.writeInt(mon.maxCams);
            buf.writeInt(mon.selectedCamIndex);

            // Get cameras for this monitor's identifier, limited to maxCams
            List<CameraData> cams = cameras.getOrDefault(mon.identifier, Collections.emptyList());
            int camsCount = Math.min(cams.size(), mon.maxCams);
            buf.writeInt(camsCount);

            for (int i = 0; i < camsCount; i++) {
                CameraData cam = cams.get(i);
                buf.writeString(cam.identifier);
                buf.writeInt(cam.num);
                buf.writeBlockPos(cam.pos);
            }
        }

        ServerPlayNetworking.send(player, CAMERA_PACKET_ID, buf);
    }

    // Data classes

    public static class CameraData {
        public String identifier;
        public int num;
        public BlockPos pos;
        public float pitch;
        public float yaw;
    }

    public static class MonitorData {
        public String identifier;
        public BlockPos pos;
        public boolean type;
        public int maxCams;
        public int selectedCamIndex;
        public CameraData[] cameras;

        public PacketByteBuf toPacket(BlockPos pos) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(identifier);
            buf.writeBoolean(type);
            buf.writeInt(maxCams);
            buf.writeBlockPos(pos);
            return buf;
        }

    }

    public static class CameraBlock extends Block {
        public CameraBlock(Settings settings) {
            super(settings);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
            if (!world.isClient) {
                NbtCompound nbt = itemStack.getOrCreateNbt();
                String identifier = nbt.getString("identifier");
                int camNum = nbt.getInt("num");
                CameraSystem.getInstance().onCameraBlockPlaced(pos, identifier, camNum);
            }
            super.onPlaced(world, pos, state, placer, itemStack);
        }

        @Override
        public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.isOf(newState.getBlock())) {
                if (!world.isClient) {
                    CameraSystem.onBlockBroken(pos);
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    public static class MonitorBlock extends Block {
        public MonitorBlock(Settings settings) {
            super(settings);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
            if (!world.isClient) {
                NbtCompound nbt = itemStack.getOrCreateNbt();
                String identifier = nbt.getString("identifier");
                boolean type = nbt.getBoolean("type");
                int maxCams = nbt.getInt("maxCams");
                CameraSystem.getInstance().onMonitorBlockPlaced(pos, identifier, type, maxCams);
            }
            super.onPlaced(world, pos, state, placer, itemStack);
        }

        @Override
        public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
            if (!state.isOf(newState.getBlock())) {
                if (!world.isClient) {
                    CameraSystem.onBlockBroken(pos);
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
                                  BlockHitResult hit) {
            if (!world.isClient) {
                MonitorData monitor = monitorPosMap.get(pos);
                if (monitor != null) {
                    // Send open monitor screen packet to client with relevant data
                    ServerPlayNetworking.send(
                            (ServerPlayerEntity) player,
                            Amongcraft.OPEN_MONITOR_SCREEN,
                            monitor.toPacket(pos)
                    );
                }
            }
            return ActionResult.SUCCESS;
        }
    }
}
