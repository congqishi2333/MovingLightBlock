package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Draws the animated cube using the ordinary block model. Rotation is accumulated on the
 * client so a resource pack can choose the axis, whether to roll at all, and the speed.
 */
public class RollingLightRenderer extends EntityRenderer<RollingLightEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "textures/block/moving_light.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final BlockRenderDispatcher blockRenderer;
    private final BlockState renderState;
    private final Map<UUID, RollState> states = new HashMap<>();

    private ResourceManager lastManager;
    private RollingConfig rollingConfig;
    private long lastConfigLoadNanos = System.nanoTime();
    private int lastModelRevision = ModelManager.revision();

    public RollingLightRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.renderState = MovingLightBlockMod.VISUAL_LIGHT.get().defaultBlockState();
        this.shadowRadius = 0.0F;
        this.lastManager = context.getResourceManager();
        this.rollingConfig = ModelManager.load(this.lastManager);
    }

    @Override
    public void render(RollingLightEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        boolean managerChanged = manager != this.lastManager;
        int modelRevision = ModelManager.revision();
        boolean modelChanged = modelRevision != this.lastModelRevision;
        long now = System.nanoTime();
        // ResourceManager.reload() can keep the same instance, so also poll periodically.
        if (managerChanged || modelChanged || now - this.lastConfigLoadNanos > 500_000_000L) {
            this.lastManager = manager;
            this.lastModelRevision = modelRevision;
            this.rollingConfig = ModelManager.load(manager);
            this.lastConfigLoadNanos = now;
            if (managerChanged || modelChanged) {
                this.states.clear();
            }
        }

        Quaternionf rotation = updateRotation(entity, partialTick);

        poseStack.pushPose();
        // The block model is defined from the lower corner; the entity origin is at the feet.
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(rotation);
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        this.blockRenderer.renderSingleBlock(
                this.renderState,
                poseStack,
                bufferSource,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                RenderType.solid());

        poseStack.popPose();
    }

    private Quaternionf updateRotation(RollingLightEntity entity, float partialTick) {
        if (entity.isRemoved()) {
            this.states.remove(entity.getUUID());
            return new Quaternionf();
        }

        RollState state = this.states.computeIfAbsent(entity.getUUID(), id -> new RollState());
        boolean animate = entity.isAnimationEnabled() && this.rollingConfig.enabled;

        if (!animate) {
            state.rotation.identity();
            state.initialized = false;
            return state.rotation;
        }

        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        if (state.initialized) {
            double dx = x - state.lastX;
            double dy = y - state.lastY;
            double dz = z - state.lastZ;
            double horizontal = Math.hypot(dx, dz);
            double factor = this.rollingConfig.degreesPerBlock * this.rollingConfig.speed;

            Vector3f axis = null;
            double degrees = 0.0D;
            switch (this.rollingConfig.axis) {
                case AUTO -> {
                    if (horizontal > 1.0E-5D) {
                        axis = new Vector3f((float) dz, 0.0F, (float) -dx).normalize();
                        degrees = horizontal * factor;
                    }
                }
                case X -> {
                    if (Math.abs(dz) > 1.0E-5D) {
                        axis = new Vector3f(1.0F, 0.0F, 0.0F);
                        degrees = dz * factor;
                    }
                }
                case Y -> {
                    if (horizontal > 1.0E-5D) {
                        axis = new Vector3f(0.0F, 1.0F, 0.0F);
                        degrees = horizontal * factor;
                    }
                }
                case Z -> {
                    if (Math.abs(dx) > 1.0E-5D) {
                        axis = new Vector3f(0.0F, 0.0F, 1.0F);
                        degrees = -dx * factor;
                    }
                }
                case FREE -> {
                    // A custom axis rolls by the actual distance travelled, not just one component.
                    double travel = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (travel > 1.0E-5D) {
                        axis = this.rollingConfig.freeAxis;
                        degrees = travel * factor;
                    }
                }
                case EULER -> {
                    // Three-axis rotation in JOML's XYZ order (X, then Y, then Z).
                    Vector3f euler = this.rollingConfig.eulerDegrees;
                    if (euler.x * euler.x + euler.y * euler.y + euler.z * euler.z > 1.0E-6F) {
                        state.rotation.premul(new Quaternionf().rotationXYZ(
                                (float) Math.toRadians(euler.x * factor),
                                (float) Math.toRadians(euler.y * factor),
                                (float) Math.toRadians(euler.z * factor)));
                    }
                }
                case NONE -> {
                    // Handled by "enabled" above; kept explicit for exhaustiveness.
                }
            }

            if (axis != null && Math.abs(degrees) > 1.0E-4D) {
                state.rotation.premul(new Quaternionf(
                        new AxisAngle4f((float) Math.toRadians(degrees), axis)));
            }
        }

        state.lastX = x;
        state.lastY = y;
        state.lastZ = z;
        state.initialized = true;
        return state.rotation;
    }

    @Override
    public ResourceLocation getTextureLocation(RollingLightEntity entity) {
        return TEXTURE;
    }

    private static final class RollState {
        private final Quaternionf rotation = new Quaternionf();
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean initialized;
    }
}
