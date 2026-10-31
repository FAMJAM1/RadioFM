package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * RU: кадр Opus 20мс; entityId -1 - звук стоит в pos, иначе идёт за сущностью
 * US: a 20ms Opus frame; entityId -1 means the sound stays at pos, otherwise it follows the entity
 */
public record RadioAudioPacket(UUID source, BlockPos pos, int entityId, float range, byte[] frame)
        implements CustomPacketPayload {

    public static final Type<RadioAudioPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_audio")
    );

    public static final StreamCodec<ByteBuf, RadioAudioPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RadioAudioPacket::source,
            BlockPos.STREAM_CODEC, RadioAudioPacket::pos,
            ByteBufCodecs.VAR_INT, RadioAudioPacket::entityId,
            ByteBufCodecs.FLOAT, RadioAudioPacket::range,
            ByteBufCodecs.BYTE_ARRAY, RadioAudioPacket::frame,
            RadioAudioPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
