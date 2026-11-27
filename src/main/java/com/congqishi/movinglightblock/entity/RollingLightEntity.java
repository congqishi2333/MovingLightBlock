package com.congqishi.movinglightblock.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Invisible-to-logic, client-rendered cube that visually follows a player.
 *
 * <p>The real block light is still placed by {@code LightFollower}; this entity only
 * draws a rolling cube. It waits until the player has walked a few blocks, then flies
 * toward the player at a bounded speed so it never sits at the player's feet and
 * blocks interactions. When it stops chasing, gravity pulls it back to the ground.
 * Chasing is triggered by the player's own travel, not by the distance to the entity,
 * so falling cannot re-trigger it. Visual rolling is accumulated client-side according to
 * the resource pack's {@code assets/movinglightblock/rolling.json}.</p>
 */
public class RollingLightEntity extends Entity {
    private static final double GRAVITY = 0.08D;
    private static final double TERMINAL_VELOCITY = 3.9D;

    /** Server-side "animation" config, synced so the client renderer can honour it. */
    public static final EntityDataAccessor<Boolean> ANIMATION =
            SynchedEntityData.defineId(RollingLightEntity.class, EntityDataSerializers.BOOLEAN);

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
        builder.define(ANIMATION, true);
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

        // Rolling itself is computed client-side so resource packs can define axis/speed.
        this.entityData.set(ANIMATION, animate);
    }

    public boolean isAnimationEnabled() {
        return this.entityData.get(ANIMATION);
    }

    public void stopFollowing() {
        this.following = false;
        this.travelSinceIdle = 0.0D;
        this.lastPlayerTarget = null;
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** Called when the entity is too far away or has ended up inside a solid block. */
    public void teleportTo(Vec3 position) {
        this.setPos(position);
        this.setDeltaMovement(Vec3.ZERO);
        this.following = false;
        this.travelSinceIdle = 0.0D;
        this.lastPlayerTarget = null;
    }

    /**
     * True only when the cube's centre is buried in a full solid block. Damage/utility
     * blocks such as cactus, magma blocks or modded spikes are not counted here, so standing
     * on them must not trigger a teleport.
     */
    public boolean isInsideSolid() {
        BlockPos pos = BlockPos.containing(this.getX(), this.getY() + 0.5D, this.getZ());
        BlockState state = this.level().getBlockState(pos);
        return state.isSuffocating(this.level(), pos)
                && !state.is(com.congqishi.movinglightblock.MovingLightBlockMod.MOVING_LIGHT.get());
    }

    // The cube must never be hurt, pushed, ignited or triggered by any block/entity path.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Skips magma/cactus/fire/entityInside effects entirely. */
    @Override
    protected void checkInsideBlocks() {
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

        // Start below the cube. Scanning its own cell would lift it through walls
        // whenever it spawned inside a solid block.
        for (int y = Mth.floor(this.getY()) - 1; y >= level.getMinBuildHeight(); y--) {
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
