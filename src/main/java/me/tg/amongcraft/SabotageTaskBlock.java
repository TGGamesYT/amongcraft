package me.tg.amongcraft;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Random;

import static me.tg.amongcraft.Amongcraft.rotateShape;


public class SabotageTaskBlock extends Block {

    public static final Identifier OPEN_GUI_PACKET = new Identifier("amongcraft", "open_sabotage_task_gui");
    public static final EnumProperty<SabotageType> TASK = EnumProperty.of("task", SabotageType.class);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public SabotageTaskBlock(Settings settings) {
        super(settings);
    }

    public enum SabotageType implements StringIdentifiable {
        O2("o2"),
        REACTOR("reactor"),
        COMMS("comms"),
        LIGHTS("lights");

        private final String name;
        SabotageType(String name) { this.name = name; }

        @Override
        public String asString() { return name; }
    }

    VoxelShape shape0 = VoxelShapes.cuboid(0.1875f, 0.0f, 0.375f, 0.8125f, 0.0625f, 1.0f);
    VoxelShape shape1 = VoxelShapes.cuboid(0.1875f, 0.0625f, 0.375f, 0.8125f, 0.125f, 1.0f);
    VoxelShape shape2 = VoxelShapes.cuboid(0.1875f, 0.125f, 0.375f, 0.8125f, 0.1875f, 1.0f);
    VoxelShape shape3 = VoxelShapes.cuboid(0.1875f, 0.1875f, 0.375f, 0.8125f, 0.25f, 1.0f);
    VoxelShape shape4 = VoxelShapes.cuboid(0.1875f, 0.25f, 0.375f, 0.8125f, 0.3125f, 1.0f);
    VoxelShape shape5 = VoxelShapes.cuboid(0.1875f, 0.3125f, 0.375f, 0.8125f, 0.375f, 1.0f);
    VoxelShape shape6 = VoxelShapes.cuboid(0.1875f, 0.375f, 0.375f, 0.8125f, 0.4375f, 1.0f);
    VoxelShape shape7 = VoxelShapes.cuboid(0.1875f, 0.4375f, 0.375f, 0.8125f, 0.5f, 1.0f);
    VoxelShape shape8 = VoxelShapes.cuboid(0.4375f, 0.5f, 0.4375f, 0.5625f, 0.53125f, 0.6875f);
    VoxelShape shape9 = VoxelShapes.cuboid(0.4375f, 0.53125f, 0.4375f, 0.5625f, 0.5625f, 0.6875f);
    VoxelShape shape10 = VoxelShapes.cuboid(0.4375f, 0.5625f, 0.4375f, 0.5625f, 0.59375f, 0.6875f);
    VoxelShape shape11 = VoxelShapes.cuboid(0.4375f, 0.59375f, 0.4375f, 0.5625f, 0.625f, 0.6875f);
    VoxelShape shape12 = VoxelShapes.cuboid(0.4375f, 0.625f, 0.4375f, 0.5625f, 0.65625f, 0.6875f);
    VoxelShape shape13 = VoxelShapes.cuboid(0.4375f, 0.65625f, 0.4375f, 0.5625f, 0.6875f, 0.6875f);
    VoxelShape shape14 = VoxelShapes.cuboid(0.4375f, 0.6875f, 0.4375f, 0.5625f, 0.71875f, 0.6875f);
    VoxelShape shape15 = VoxelShapes.cuboid(0.4375f, 0.71875f, 0.4375f, 0.5625f, 0.75f, 0.6875f);
    VoxelShape shape16 = VoxelShapes.cuboid(0.5625f, 0.5625f, 0.5f, 0.625f, 0.578125f, 0.625f);
    VoxelShape shape17 = VoxelShapes.cuboid(0.5625f, 0.578125f, 0.5f, 0.625f, 0.59375f, 0.625f);
    VoxelShape shape18 = VoxelShapes.cuboid(0.5625f, 0.59375f, 0.5f, 0.625f, 0.609375f, 0.625f);
    VoxelShape shape19 = VoxelShapes.cuboid(0.5625f, 0.609375f, 0.5f, 0.625f, 0.625f, 0.625f);
    VoxelShape shape20 = VoxelShapes.cuboid(0.5625f, 0.625f, 0.5f, 0.625f, 0.640625f, 0.625f);
    VoxelShape shape21 = VoxelShapes.cuboid(0.5625f, 0.640625f, 0.5f, 0.625f, 0.65625f, 0.625f);
    VoxelShape shape22 = VoxelShapes.cuboid(0.5625f, 0.65625f, 0.5f, 0.625f, 0.671875f, 0.625f);
    VoxelShape shape23 = VoxelShapes.cuboid(0.5625f, 0.671875f, 0.5f, 0.625f, 0.6875f, 0.625f);
    VoxelShape shape24 = VoxelShapes.cuboid(0.375f, 0.5625f, 0.5f, 0.4375f, 0.578125f, 0.625f);
    VoxelShape shape25 = VoxelShapes.cuboid(0.375f, 0.578125f, 0.5f, 0.4375f, 0.59375f, 0.625f);
    VoxelShape shape26 = VoxelShapes.cuboid(0.375f, 0.59375f, 0.5f, 0.4375f, 0.609375f, 0.625f);
    VoxelShape shape27 = VoxelShapes.cuboid(0.375f, 0.609375f, 0.5f, 0.4375f, 0.625f, 0.625f);
    VoxelShape shape28 = VoxelShapes.cuboid(0.375f, 0.625f, 0.5f, 0.4375f, 0.640625f, 0.625f);
    VoxelShape shape29 = VoxelShapes.cuboid(0.375f, 0.640625f, 0.5f, 0.4375f, 0.65625f, 0.625f);
    VoxelShape shape30 = VoxelShapes.cuboid(0.375f, 0.65625f, 0.5f, 0.4375f, 0.671875f, 0.625f);
    VoxelShape shape31 = VoxelShapes.cuboid(0.375f, 0.671875f, 0.5f, 0.4375f, 0.6875f, 0.625f);
    VoxelShape shape32 = VoxelShapes.cuboid(0.625f, 0.460036f, 0.58166f, 0.6875f, 0.596325f, 0.931105f);
    VoxelShape shape33 = VoxelShapes.cuboid(0.625f, 0.467254f, 0.58465f, 0.6875f, 0.603543f, 0.934094f);
    VoxelShape shape34 = VoxelShapes.cuboid(0.625f, 0.474472f, 0.58764f, 0.6875f, 0.61076f, 0.937084f);
    VoxelShape shape35 = VoxelShapes.cuboid(0.625f, 0.48169f, 0.590629f, 0.6875f, 0.617978f, 0.940074f);
    VoxelShape shape36 = VoxelShapes.cuboid(0.625f, 0.488908f, 0.593619f, 0.6875f, 0.625196f, 0.943064f);
    VoxelShape shape37 = VoxelShapes.cuboid(0.625f, 0.496125f, 0.596609f, 0.6875f, 0.632414f, 0.946053f);
    VoxelShape shape38 = VoxelShapes.cuboid(0.625f, 0.503343f, 0.599598f, 0.6875f, 0.639632f, 0.949043f);
    VoxelShape shape39 = VoxelShapes.cuboid(0.625f, 0.510561f, 0.602588f, 0.6875f, 0.646849f, 0.952033f);
    VoxelShape shape40 = VoxelShapes.cuboid(0.3125f, 0.460036f, 0.58166f, 0.375f, 0.596325f, 0.931105f);
    VoxelShape shape41 = VoxelShapes.cuboid(0.3125f, 0.467254f, 0.58465f, 0.375f, 0.603543f, 0.934094f);
    VoxelShape shape42 = VoxelShapes.cuboid(0.3125f, 0.474472f, 0.58764f, 0.375f, 0.61076f, 0.937084f);
    VoxelShape shape43 = VoxelShapes.cuboid(0.3125f, 0.48169f, 0.590629f, 0.375f, 0.617978f, 0.940074f);
    VoxelShape shape44 = VoxelShapes.cuboid(0.3125f, 0.488908f, 0.593619f, 0.375f, 0.625196f, 0.943064f);
    VoxelShape shape45 = VoxelShapes.cuboid(0.3125f, 0.496125f, 0.596609f, 0.375f, 0.632414f, 0.946053f);
    VoxelShape shape46 = VoxelShapes.cuboid(0.3125f, 0.503343f, 0.599598f, 0.375f, 0.639632f, 0.949043f);
    VoxelShape shape47 = VoxelShapes.cuboid(0.3125f, 0.510561f, 0.602588f, 0.375f, 0.646849f, 0.952033f);
    VoxelShape shape48 = VoxelShapes.cuboid(0.3125f, 0.5f, 0.875f, 0.6875f, 0.507812f, 0.9375f);
    VoxelShape shape49 = VoxelShapes.cuboid(0.3125f, 0.507812f, 0.875f, 0.6875f, 0.515625f, 0.9375f);
    VoxelShape shape50 = VoxelShapes.cuboid(0.3125f, 0.515625f, 0.875f, 0.6875f, 0.523438f, 0.9375f);
    VoxelShape shape51 = VoxelShapes.cuboid(0.3125f, 0.523438f, 0.875f, 0.6875f, 0.53125f, 0.9375f);
    VoxelShape shape52 = VoxelShapes.cuboid(0.3125f, 0.53125f, 0.875f, 0.6875f, 0.539062f, 0.9375f);
    VoxelShape shape53 = VoxelShapes.cuboid(0.3125f, 0.539062f, 0.875f, 0.6875f, 0.546875f, 0.9375f);
    VoxelShape shape54 = VoxelShapes.cuboid(0.3125f, 0.546875f, 0.875f, 0.6875f, 0.554688f, 0.9375f);
    VoxelShape shape55 = VoxelShapes.cuboid(0.3125f, 0.554688f, 0.875f, 0.6875f, 0.5625f, 0.9375f);
    VoxelShape COMMS_SHAPE = VoxelShapes.union(shape0, shape1, shape2, shape3, shape4, shape5, shape6, shape7, shape8, shape9, shape10, shape11, shape12, shape13, shape14, shape15, shape16, shape17, shape18, shape19, shape20, shape21, shape22, shape23, shape24, shape25, shape26, shape27, shape28, shape29, shape30, shape31, shape32, shape33, shape34, shape35, shape36, shape37, shape38, shape39, shape40, shape41, shape42, shape43, shape44, shape45, shape46, shape47, shape48, shape49, shape50, shape51, shape52, shape53, shape54, shape55);

    VoxelShape shape56 = VoxelShapes.cuboid(0.1875f, 0.125f, 0.9375f, 0.8125f, 0.21875f, 1.0f);
    VoxelShape shape57 = VoxelShapes.cuboid(0.1875f, 0.21875f, 0.9375f, 0.8125f, 0.3125f, 1.0f);
    VoxelShape shape58 = VoxelShapes.cuboid(0.1875f, 0.3125f, 0.9375f, 0.8125f, 0.40625f, 1.0f);
    VoxelShape shape59 = VoxelShapes.cuboid(0.1875f, 0.40625f, 0.9375f, 0.8125f, 0.5f, 1.0f);
    VoxelShape shape60 = VoxelShapes.cuboid(0.1875f, 0.5f, 0.9375f, 0.8125f, 0.59375f, 1.0f);
    VoxelShape shape61 = VoxelShapes.cuboid(0.1875f, 0.59375f, 0.9375f, 0.8125f, 0.6875f, 1.0f);
    VoxelShape shape62 = VoxelShapes.cuboid(0.1875f, 0.6875f, 0.9375f, 0.8125f, 0.78125f, 1.0f);
    VoxelShape shape63 = VoxelShapes.cuboid(0.1875f, 0.78125f, 0.9375f, 0.8125f, 0.875f, 1.0f);
    VoxelShape O2_SHAPE = VoxelShapes.union(shape56, shape57, shape58, shape59, shape60, shape61, shape62, shape63);

    VoxelShape shape64 = VoxelShapes.cuboid(0.1875f, 0.125f, 0.9375f, 0.8125f, 0.21875f, 1.0f);
    VoxelShape shape65 = VoxelShapes.cuboid(0.1875f, 0.21875f, 0.9375f, 0.8125f, 0.3125f, 1.0f);
    VoxelShape shape66 = VoxelShapes.cuboid(0.1875f, 0.3125f, 0.9375f, 0.8125f, 0.40625f, 1.0f);
    VoxelShape shape67 = VoxelShapes.cuboid(0.1875f, 0.40625f, 0.9375f, 0.8125f, 0.5f, 1.0f);
    VoxelShape shape68 = VoxelShapes.cuboid(0.1875f, 0.5f, 0.9375f, 0.8125f, 0.59375f, 1.0f);
    VoxelShape shape69 = VoxelShapes.cuboid(0.1875f, 0.59375f, 0.9375f, 0.8125f, 0.6875f, 1.0f);
    VoxelShape shape70 = VoxelShapes.cuboid(0.1875f, 0.6875f, 0.9375f, 0.8125f, 0.78125f, 1.0f);
    VoxelShape shape71 = VoxelShapes.cuboid(0.1875f, 0.78125f, 0.9375f, 0.8125f, 0.875f, 1.0f);
    VoxelShape REACTOR_SHAPE = VoxelShapes.union(shape64, shape65, shape66, shape67, shape68, shape69, shape70, shape71);

    VoxelShape shape72 = VoxelShapes.cuboid(-0.125f, 0.0f, 0.9375f, 1.125f, 0.226562f, 1.0f);
    VoxelShape shape73 = VoxelShapes.cuboid(-0.125f, 0.226562f, 0.9375f, 1.125f, 0.453125f, 1.0f);
    VoxelShape shape74 = VoxelShapes.cuboid(-0.125f, 0.453125f, 0.9375f, 1.125f, 0.679688f, 1.0f);
    VoxelShape shape75 = VoxelShapes.cuboid(-0.125f, 0.679688f, 0.9375f, 1.125f, 0.90625f, 1.0f);
    VoxelShape shape76 = VoxelShapes.cuboid(-0.125f, 0.90625f, 0.9375f, 1.125f, 1.132812f, 1.0f);
    VoxelShape shape77 = VoxelShapes.cuboid(-0.125f, 1.132812f, 0.9375f, 1.125f, 1.359375f, 1.0f);
    VoxelShape shape78 = VoxelShapes.cuboid(-0.125f, 1.359375f, 0.9375f, 1.125f, 1.585938f, 1.0f);
    VoxelShape shape79 = VoxelShapes.cuboid(-0.125f, 1.585938f, 0.9375f, 1.125f, 1.8125f, 1.0f);
    VoxelShape LIGHTS_SHAPE = VoxelShapes.union(shape72, shape73, shape74, shape75, shape76, shape77, shape78, shape79);
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        SabotageType type = state.get(TASK);
        Direction facing = state.get(FACING);

        VoxelShape base;
        switch (type) {
            case O2: base = O2_SHAPE; break;
            case REACTOR: base = REACTOR_SHAPE; break;
            case COMMS: base = COMMS_SHAPE; break;
            case LIGHTS: base = LIGHTS_SHAPE; break;
            default: return VoxelShapes.fullCube();
        }

        return rotateShape(facing, base);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TASK, FACING);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient && itemStack.hasNbt()) {
            NbtCompound nbt = itemStack.getNbt();
            String map = nbt.getString("map");
            String task = nbt.getString("task");

            JsonObject data = new JsonObject();
            data.addProperty("map", map);
            data.addProperty("task", task);

            BlockNBTStore.save(world, pos, data);
            Amongcraft.LOGGER.info("Placed sabotage block at {} with task={} map={}", pos, task, map);
        }

        super.onPlaced(world, pos, state, placer, itemStack);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            JsonObject data = BlockNBTStore.load(world, pos);
            if (data == null) {
                Amongcraft.LOGGER.warn("No sabotage data found for block at {}", pos);
                return ActionResult.PASS;
            }

            String fullTask = data.get("task").getAsString(); // e.g., "o2-1" or "reactor-2"
            String task = fullTask.split("-")[0]; // just "o2" or "reactor"
            String map = data.get("map").getAsString();
            if (!SabotageManager.isSabotaged(task)) {
                Amongcraft.LOGGER.info("Tried to open sabotage GUI for '{}', but it's not active", task);
                return ActionResult.SUCCESS;
            }
            JsonObject lightState = data.has("light_state") ? data.getAsJsonObject("light_state") : new JsonObject();


            // Uncomment this if you want map checking
            // if (!TaskProgressTracker.currentMap.equals(map)) {
            //     return ActionResult.SUCCESS;
            // }

            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBlockPos(pos);
            buf.writeString(fullTask);
            buf.writeString(map);

            for (int i = 0; i < 5; i++) {
                boolean s = lightState.has("lever_" + i) && lightState.get("lever_" + i).getAsBoolean();
                buf.writeBoolean(s);
            }

            serverPlayer.networkHandler.sendPacket(new CustomPayloadS2CPacket(OPEN_GUI_PACKET, buf));
            Amongcraft.LOGGER.info("Clicked sabotage block at {} -> task={} map={}", pos, task, map);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing();
        ItemStack stack = ctx.getStack();

        SabotageType type = SabotageType.COMMS; // default fallback

        if (stack.hasNbt() && stack.getNbt().contains("task")) {
            String task = stack.getNbt().getString("task").split("-")[0].toLowerCase();
            for (SabotageType t : SabotageType.values()) {
                if (t.asString().equals(task)) {
                    type = t;
                    break;
                }
            }
        }

        return this.getDefaultState()
                .with(TASK, type)
                .with(FACING, facing);
    }

    public static JsonObject generateRandomLightState() {
        Random random = new Random();
        JsonObject obj = new JsonObject();

        // Repeat until at least one is OFF
        boolean[] switches;
        do {
            switches = new boolean[5];
            for (int i = 0; i < 5; i++) {
                switches[i] = random.nextBoolean();
            }
        } while (allOn(switches));

        for (int i = 0; i < 5; i++) {
            obj.addProperty("lever_" + i, switches[i]);
        }

        return obj;
    }

    private static boolean allOn(boolean[] switches) {
        for (boolean b : switches) {
            if (!b) return false;
        }
        return true;
    }
}

