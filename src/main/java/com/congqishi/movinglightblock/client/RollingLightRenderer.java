package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the animated cube using the ordinary block model, rotated around its centre.
 */
public class RollingLightRenderer extends EntityRenderer<RollingLightEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "textures/block/moving_light.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final BlockRenderDispatcher blockRenderer;
    private final BlockState renderState;

    public RollingLightRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.renderState = MovingLightBlockMod.VISUAL_LIGHT.get().defaultBlockState();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RollingLightEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // The block model is defined from the lower corner; the entity origin is at the feet.
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(entity.getRenderRotation());
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

    @Override
    public ResourceLocation getTextureLocation(RollingLightEntity entity) {
        return TEXTURE;
    }
}
