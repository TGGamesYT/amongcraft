package me.tg.amongcraft;

import io.netty.buffer.Unpooled;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.BlockView;

import java.util.*;

public class SabotageManager {
    private static final Set<String> activeSabotages = new HashSet<>();
    private static final Map<String, List<BlockPos>> sabotagedDoors = new HashMap<>();
    private static final Map<String, Boolean> sabotageTasks = new HashMap<>();
    private static final Map<Integer, Boolean> lights = new HashMap<>();
    private static final Random random = new Random();

    static {
        for (int i = 0; i < 5; i++) lights.put(i, false);
    }

    public static void triggerSabotage(String type) {
        activeSabotages.add(type);
        if (type.equals("lights")) {
            for (int i = 0; i < 5; i++) lights.put(i, random.nextBoolean());
        }
    }

    public static boolean isSabotaged(String type) {
        return activeSabotages.contains(type);
    }

    public static void resolveSabotage(String type) {
        activeSabotages.remove(type);
    }

    public static void setDoorGroup(String id, List<BlockPos> positions) {
        sabotagedDoors.put(id, positions);
    }

    public static List<BlockPos> getDoorGroup(String id) {
        return sabotagedDoors.getOrDefault(id, List.of());
    }

    public static boolean allSwitchesUp() {
        return lights.values().stream().allMatch(v -> v);
    }

    public static void setLightSwitch(int index, boolean value) {
        lights.put(index, value);
        if (allSwitchesUp()) resolveSabotage("lights");
    }

    public static void cancelIfComplete(String type) {
        if (type.equals("reactor")) {
            if (Boolean.TRUE.equals(sabotageTasks.get("reactor-1")) &&
                    Boolean.TRUE.equals(sabotageTasks.get("reactor-2"))) {
                resolveSabotage("reactor");
            }
        } else if (type.equals("o2")) {
            if (Boolean.TRUE.equals(sabotageTasks.get("o2-1")) &&
                    Boolean.TRUE.equals(sabotageTasks.get("o2-2"))) {
                resolveSabotage("o2");
            }
        }
    }

    public static void markTaskDone(String task) {
        sabotageTasks.put(task, true);
        if (task.startsWith("reactor")) cancelIfComplete("reactor");
        if (task.startsWith("o2")) cancelIfComplete("o2");
        if (task.equals("comms")) resolveSabotage("comms");
    }

    public static boolean isCommsDown() {
        return activeSabotages.contains("comms");
    }

    public static final Item SABOTAGE_ITEM = new Item(new Item.Settings().maxCount(1)) {
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            if (!world.isClient && user instanceof ServerPlayerEntity player) {
                PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                player.networkHandler.sendPacket(new CustomPayloadS2CPacket(new Identifier("amongcraft", "open_sabotage_gui"), buf));
                List<String> allDoorIds = AmongcraftDoorBlock.getAllDoorIds(); // or however you store them
                PacketByteBuf doorsBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                doorsBuf.writeInt(allDoorIds.size());
                for (String id : allDoorIds) {
                    doorsBuf.writeString(id);
                }
                player.networkHandler.sendPacket(new CustomPayloadS2CPacket(new Identifier("amongcraft", "door_list_response"), doorsBuf));
            }

            return TypedActionResult.success(user.getStackInHand(hand));
        }
    };

}
