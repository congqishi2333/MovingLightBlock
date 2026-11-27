package com.congqishi.movinglightblock.network;

import com.congqishi.movinglightblock.MovingLightBlockMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent by the client when the configured retract combination is pressed while aiming at the cube. */
public record RetractPayload(int entityId) implements CustomPacketPayload {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "retract");
    public static final Type<RetractPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<ByteBuf, RetractPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RetractPayload::entityId, RetractPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
