package dev.famjam.radiofm.network;
import dev.famjam.radiofm.RadioFM;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * RU: пустой pos - радио в руке; repeatTrackIndex -1 - повтор выключен; ownChannel - звук идёт
 *     своим каналом, тогда в окне есть ползунок громкости
 * US: an empty pos means a held radio; repeatTrackIndex -1 means repeat off; ownChannel - the sound
 *     goes through our own channel, then the screen has a volume slider
 */
public record OpenRadioScreenPacket(Optional<BlockPos> pos, String stationName, List<String> urls, String state,
                                    boolean shuffle, int repeatTrackIndex, boolean ownChannel) implements CustomPacketPayload {
    public static final Type<OpenRadioScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "open_radio_screen")
    );
    private static final StreamCodec<ByteBuf, Optional<BlockPos>> POS = ByteBufCodecs.optional(BlockPos.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, List<String>> URLS =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);
    // RU: composite в 1.21.1 берёт не больше шести полей | US: composite in 1.21.1 takes six fields at most
    public static final StreamCodec<FriendlyByteBuf, OpenRadioScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                POS.encode(buf, packet.pos());
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.stationName());
                URLS.encode(buf, packet.urls());
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.state());
                buf.writeBoolean(packet.shuffle());
                buf.writeInt(packet.repeatTrackIndex());
                buf.writeBoolean(packet.ownChannel());
            },
            buf -> new OpenRadioScreenPacket(
                    POS.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    URLS.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readBoolean())
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
