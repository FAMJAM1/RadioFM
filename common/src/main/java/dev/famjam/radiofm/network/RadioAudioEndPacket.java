package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RadioAudioEndPacket(UUID source) implements CustomPacketPayload {

    public static final Type<RadioAudioEndPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_audio_end")
    );

    public static final StreamCodec<ByteBuf, RadioAudioEndPacket> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(RadioAudioEndPacket::new, RadioAudioEndPacket::source);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
