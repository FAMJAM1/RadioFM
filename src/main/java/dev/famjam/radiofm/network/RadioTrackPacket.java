package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** RU: что играет на источнике своего канала, для субтитров; пустой author - неизвестен | US: what a source on our channel plays, for subtitles; an empty author means unknown */
public record RadioTrackPacket(UUID source, String title, String author) implements CustomPacketPayload {

    public static final Type<RadioTrackPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_track")
    );

    public static final StreamCodec<ByteBuf, RadioTrackPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RadioTrackPacket::source,
            ByteBufCodecs.stringUtf8(256), RadioTrackPacket::title,
            ByteBufCodecs.stringUtf8(256), RadioTrackPacket::author,
            RadioTrackPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
