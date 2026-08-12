package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** ru: пустой pos — радио в руке | en: an empty pos means a held radio */
public record RadioControlPacket(java.util.Optional<BlockPos> pos, int action, int data) implements CustomPacketPayload {

    public static final int PREV = 0;
    public static final int PAUSE = 1;
    public static final int NEXT = 2;
    public static final int STOP = 3;
    public static final int PLAY = 4;
    public static final int SHUFFLE = 5;
    public static final int REPEAT = 6;
    public static final int REPEAT_TRACK = 7;

    public static final Type<RadioControlPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_control")
    );

    public static final StreamCodec<FriendlyByteBuf, RadioControlPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), RadioControlPacket::pos,
            ByteBufCodecs.INT, RadioControlPacket::action,
            ByteBufCodecs.INT, RadioControlPacket::data,
            RadioControlPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}