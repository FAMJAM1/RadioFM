package dev.famjam.radiofm.network;

import dev.famjam.radiofm.RadioFM;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** RU: пустые title и author - неизвестно | US: an empty title or author means unknown */
public record RadioStatusPacket(long elapsedSeconds, long durationSeconds, String title, String author) implements CustomPacketPayload {

    private static final int MAX_TEXT = 256;

    public static RadioStatusPacket of(long elapsed, long duration, String title, String author) {
        return new RadioStatusPacket(elapsed, duration, clip(title), clip(author));
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    public static final Type<RadioStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RadioFM.MODID, "radio_status")
    );

    public static final StreamCodec<FriendlyByteBuf, RadioStatusPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RadioStatusPacket::elapsedSeconds,
            ByteBufCodecs.VAR_LONG, RadioStatusPacket::durationSeconds,
            ByteBufCodecs.STRING_UTF8, RadioStatusPacket::title,
            ByteBufCodecs.STRING_UTF8, RadioStatusPacket::author,
            RadioStatusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
