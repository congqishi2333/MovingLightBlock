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
 * draws a rolling cube. It waits until the player is a few blocks away, then flies
 * toward the player at a bounded speed so it never sits at the player's feet and
 * blocks interactions. Rotation is accumulated from its own movement.</p>
 */
public class RollingLightEntity extends Entity {
    public static final EntityDataAccessor<Quaternionf> ROTATION =
            SynchedEntityData.defineId(RollingLightEntity.class, EntityDataSerializers.QUATERNION);

    private final Quaternionf rotation = new Quaternionf();
    private UUID owner;
    private boolean following;

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
     *
     * <p>The entity stays still until the player is {@code startDistance} blocks away, then
     * flies toward the player at {@code speed} blocks per second. It stops once it is within
     * {@code stopDistance}, so it does not sit at the player's feet and block interactions.</p>
     */
    public void follow(ServerPlayer player, int yOffset, boolean animate,
                       double startDistance, double stopDistance, double speed) {
        Vec3 target = new Vec3(player.getX(), player.getY() + yOffset, player.getZ());
        Vec3 current = this.position();
        double distance = current.distanceTo(target);

        if (!this.following && distance >= startDistance) {
            this.following = true;
        }
        if (this.following && distance <= stopDistance) {
            this.following = false;
        }

        double stepLength = 0.0D;
        Vec3 delta = target.subtract(current);

        if (this.following && distance > 1.0E-4D) {
            stepLength = Math.min(Math.max(speed, 0.0D) / 20.0D, distance);
            this.setPos(current.add(delta.scale(stepLength / distance)));
        }

        if (animate) {
            double horizontal = Math.hypot(delta.x, delta.z);
            if (stepLength > 1.0E-4D && horizontal > 1.0E-4D) {
                double horizontalStep = stepLength * horizontal / distance;
                // A one-block move tips a one-block cube by 90 degrees.
                float angle = (float) Math.min(horizontalStep, 2.0D) * ((float) Math.PI / 2.0F);
                Vector3f axis = new Vector3f((float) delta.z, 0.0F, (float) -delta.x).normalize();
                Quaternionf deltaRotation = new Quaternionf(new AxisAngle4f(angle, axis));
                this.rotation.premul(deltaRotation);
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
