package dev.tggamesyt.amongcraft;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Legacy block kept ONLY so worlds that contain the old
 * {@code amongcraft:task_button[task=...]} blockstates still deserialize.
 *
 * It declares the same {@code task} and {@code facing} properties the old
 * single block had. {@link TaskBlockMigrator} replaces these blocks with the
 * new per-task blocks on chunk load. This block has no special behavior.
 */
public class LegacyTaskBlock extends Block {

    public static final EnumProperty<Amongcraft.TaskBlock.TaskType> TASK =
            EnumProperty.of("task", Amongcraft.TaskBlock.TaskType.class);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public LegacyTaskBlock() {
        super(FabricBlockSettings.create()
                .strength(1.0f)
                .sounds(BlockSoundGroup.WOOD));
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(TASK, Amongcraft.TaskBlock.TaskType.DEFAULT)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TASK, FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing());
    }
}
