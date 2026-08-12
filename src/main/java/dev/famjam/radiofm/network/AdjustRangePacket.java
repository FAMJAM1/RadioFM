package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * ru: шифт с колесом мыши меняет слышимость
 * en: shift and the wheel change the range
 *
 * @param pos   ru: радио под прицелом, пусто — в руке | en: the radio in view, empty means held
 * @param steps ru: щелчков колеса, вниз отрицательное | en: wheel notches, negative downwards
 * @param fine  ru: зажат Ctrl — шаг в один блок | en: ctrl held, a one block step
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
