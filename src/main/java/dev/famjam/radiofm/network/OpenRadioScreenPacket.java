package dev.famjam.radiofm.network;
import dev.famjam.radiofm.RadioFM;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
/**
 * RU: пустой pos - радио в руке; repeatTrackIndex -1 - повтор выключен
 * US: an empty pos means a held radio; repeatTrackIndex -1 means repeat off
 */
public record OpenRadioScreenPacket(java.util.Optional<BlockPos> pos, String stationName, List<String> urls, String state, boolean shuffle, int repeatTrackIndex) implements CustomPacketPayload {
    public static final Type<OpenRadioScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "open_radio_screen")
    );
    public static final StreamCodec<FriendlyByteBuf, OpenRadioScreenPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), OpenRadioScreenPacket::pos,
            ByteBufCodecs.STRING_UTF8, OpenRadioScreenPacket::stationName,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), OpenRadioScreenPacket::urls,
            ByteBufCodecs.STRING_UTF8, OpenRadioScreenPacket::state,
            ByteBufCodecs.BOOL, OpenRadioScreenPacket::shuffle,
            ByteBufCodecs.INT, OpenRadioScreenPacket::repeatTrackIndex,
            OpenRadioScreenPacket::new
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}