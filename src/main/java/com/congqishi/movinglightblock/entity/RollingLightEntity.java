package com.congqishi.movinglightblock.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
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
 * draws a rolling cube. It waits until the player has walked a few blocks, then flies
 * toward the player at a bounded speed so it never sits at the player's feet and
 * blocks interactions. When it stops chasing, gravity pulls it back to the ground.
 * Chasing is triggered by the player's own travel, not by the distance to the entity,
 * so falling cannot re-trigger it. Rotation is accumulated from its own movement.</p>
 */
public class RollingLightEntity extends Entity {
    private static final double GRAVITY = 0.08D;
    private static final double TERMINAL_VELOCITY = 3.9D;

    public static final EntityDataAccessor<Quaternionf> ROTATION =
            SynchedEntityData.defineId(RollingLightEntity.class, EntityDataSerializers.QUATERNION);

    private final Quaternionf rotation = new Quaternionf();
    private UUID owner;
    private boolean following;
    private Vec3 lastPlayerTarget;
    private double travelSinceIdle;

    public RollingLightEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
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
     * <p>The entity stays still until the player has travelled {@code startDistance} blocks,
     * then flies toward the player at {@code speed} blocks per second. It stops once it is
     * within {@code stopDistance}, so it does not sit at the player's feet and block
     * interactions. Only the player's movement can start a chase: gravity/falling never
     * re-locks the entity.</p>
     */
    public void follow(ServerPlayer player, int yOffset, boolean animate,
                       double startDistance, double stopDistance, double speed) {
        Vec3 target = new Vec3(player.getX(), player.getY() + yOffset, player.getZ());
        Vec3 current = this.position();
        double distance = current.distanceTo(target);
        boolean wasFollowing = this.following;

        // The target always has the same yOffset, so this is the player's actual movement.
        if (this.lastPlayerTarget != null && !this.following) {
            this.travelSinceIdle += this.lastPlayerTarget.distanceTo(target);
        }
        this.lastPlayerTarget = target;

        if (!this.following && this.travelSinceIdle >= startDistance && distance > stopDistance) {
            this.following = true;
        }
        if (this.following && distance <= stopDistance) {
            this.following = false;
            this.travelSinceIdle = 0.0D;
        }

        double stepLength = 0.0D;
        Vec3 delta = target.subtract(current);

        if (this.following && distance > 1.0E-4D) {
            stepLength = Math.min(Math.max(speed, 0.0D) / 20.0D, distance);
            this.setDeltaMovement(delta.scale(stepLength / distance));
        } else {
            // Keep vertical velocity so gravity can continue, but stop drifting sideways.
            double vertical = wasFollowing && !this.following ? 0.0D : this.getDeltaMovement().y;
            this.setDeltaMovement(0.0D, vertical, 0.0D);
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

    public void stopFollowing() {
        this.following = false;
        this.travelSinceIdle = 0.0D;
        this.lastPlayerTarget = null;
        this.setDeltaMovement(Vec3.ZERO);
    }

    public Quaternionf getRenderRotation() {
        return this.entityData.get(ROTATION);
    }

    @Override
    public void tick() {
        Vec3 motion = this.getDeltaMovement();
        if (this.following) {
            // Flying: follow() supplies the velocity, including vertical movement.
            this.setPos(this.position().add(motion));
        } else {
            double vertical = Math.max(motion.y - GRAVITY, -TERMINAL_VELOCITY);
            double nextY = this.getY() + vertical;
            double ground = groundY();
            if (nextY <= ground) {
                nextY = ground;
                vertical = 0.0D;
            }
            this.setPos(this.getX(), nextY, this.getZ());
            this.setDeltaMovement(0.0D, vertical, 0.0D);
        }
        super.tick();
    }

    private double groundY() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return this.getY();
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        for (int y = Mth.floor(this.getY()); y >= level.getMinBuildHeight(); y--) {
            probe.set(x, y, z);
            BlockState state = level.getBlockState(probe);
            if (!state.canBeReplaced()) {
                return y + 1.0D;
            }
        }
        return level.getMinBuildHeight();
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
