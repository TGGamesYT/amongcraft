package me.tg.amongcraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class DeadBodyEntity extends Entity {

    public static final TrackedData<Optional<UUID>> PLAYER_UUID = DataTracker.registerData(DeadBodyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    public DeadBodyEntity(EntityType<? extends DeadBodyEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = false;
        this.setPose(EntityPose.SLEEPING);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean canHit() {
        return true;
    }

    public static DeadBodyEntity create(PlayerEntity player) {
        DeadBodyEntity deadBody = new DeadBodyEntity(Amongcraft.CORPSE_ENTITY_TYPE, player.getWorld());
        deadBody.setPos(player.getX(), player.getY(), player.getZ());
        deadBody.dataTracker.set(PLAYER_UUID, Optional.of(player.getUuid()));
        deadBody.resetPosition();
        deadBody.refreshPosition();
        return deadBody;
    }

    public UUID getPlayerUUID() {
        return dataTracker.get(PLAYER_UUID).get();
    }

    @Override
    protected Box calculateBoundingBox() {
        Box box = super.calculateBoundingBox();
        return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ).shrink(0.0, 1.2, 0.0).expand(0.6, 0.0, 0.1);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        return ActionResult.SUCCESS;
    }

    public Optional<UUID> getPlayerUuid() {
        return dataTracker.get(PLAYER_UUID);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(PLAYER_UUID, Optional.empty());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("PlayerUUID")) {
            this.dataTracker.set(PLAYER_UUID, Optional.of(nbt.getUuid("PlayerUUID")));
        } else if (nbt.contains("PlayerUUID", 8)) {  // 8 = STRING
            // fallback for old string format, convert to UUID
            this.dataTracker.set(PLAYER_UUID, Optional.of(UUID.fromString(nbt.getString("PlayerUUID"))));
        } else {
            this.dataTracker.set(PLAYER_UUID, Optional.empty());
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        Optional<UUID> optionalUuid = this.dataTracker.get(PLAYER_UUID);
        optionalUuid.ifPresent(value -> nbt.putUuid("PlayerUUID", value));
    }

}
