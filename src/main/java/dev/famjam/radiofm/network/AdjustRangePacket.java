package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * RU: pos пусто - радио в руке; steps - щелчки колеса, вниз отрицательное; fine - шаг в один блок
 * US: an empty pos means a held radio; steps are wheel notches, negative downwards; fine is a one block step
 */
public record AdjustRangePacket(java.util.Optional<BlockPos> pos, int steps, boolean fine) implements CustomPacketPayload {

    public static final Type<AdjustRangePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "adjust_range")
    );

    public static final StreamCodec<FriendlyByteBuf, AdjustRangePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), AdjustRangePacket::pos,
            ByteBufCodecs.INT, AdjustRangePacket::steps,
            ByteBufCodecs.BOOL, AdjustRangePacket::fine,
            AdjustRangePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
