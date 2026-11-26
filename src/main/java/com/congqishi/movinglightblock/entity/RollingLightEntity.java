package com.congqishi.movinglightblock.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Invisible-to-logic, client-rendered cube that visually follows a player.
 *
 * <p>The real block light is still placed by {@code LightFollower}; this entity only
 * draws a rolling cube at the player's feet. Rotation is accumulated from the
 * player's movement so the cube looks like it is tumbling forward like a companion
 * cube, without any collision or physics.</p>
 */
public class RollingLightEntity extends Entity {
    public static final EntityDataAccessor<Quaternionf> ROTATION =
            SynchedEntityData.defineId(RollingLightEntity.class, EntityDataSerializers.QUATERNION);

    private final Quaternionf rotation = new Quaternionf();
    private UUID owner;

    public RollingLightEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setPose(Pose.STANDING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROTATION, new Quaternionf());
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return this.owner;
    }

    /**
     * Called every server tick by {@code LightFollower}.
     */
    public void follow(ServerPlayer player, int yOffset, boolean animate) {
        Vec3 feet = player.position();
        double targetX = feet.x;
        double targetY = feet.y + yOffset;
        double targetZ = feet.z;

        double dx = targetX - this.getX();
        double dz = targetZ - this.getZ();

        this.setPos(targetX, targetY, targetZ);

        if (animate) {
            double distance = Math.hypot(dx, dz);
            if (distance > 1.0E-4D) {
                // A one-block move tips a one-block cube by 90 degrees.
                float angle = (float) Math.min(distance, 2.0D) * ((float) Math.PI / 2.0F);
                Vector3f axis = new Vector3f((float) dz, 0.0F, (float) -dx).normalize();
                Quaternionf delta = new Quaternionf(new AxisAngle4f(angle, axis));
                this.rotation.premul(delta);
            }
        } else {
            this.rotation.identity();
        }

        // Send a copy: SynchedEntityData skips updates when the reference value compares equal.
        this.entityData.set(ROTATION, new Quaternionf(this.rotation));
    }

    public Quaternionf getRenderRotation() {
        return this.entityData.get(ROTATION);
    }

    @Override
    public void tick() {
        // Position and rotation are driven entirely by LightFollower.
        super.tick();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
    }
}
