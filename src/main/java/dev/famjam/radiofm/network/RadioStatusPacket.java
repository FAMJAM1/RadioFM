package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadioStatusPacket(long elapsedSeconds, long durationSeconds) implements CustomPacketPayload {

    public static final Type<RadioStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_status")
    );

    public static final StreamCodec<FriendlyByteBuf, RadioStatusPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RadioStatusPacket::elapsedSeconds,
            ByteBufCodecs.VAR_LONG, RadioStatusPacket::durationSeconds,
            RadioStatusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
