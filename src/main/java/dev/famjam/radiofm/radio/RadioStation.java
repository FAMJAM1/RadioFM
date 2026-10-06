package dev.famjam.radiofm.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * RU: настройки радио; лежат и на предмете, и на блоке, поэтому неизменяемы
 * US: the radio's settings; stored on the item and on the block, hence immutable
 *
 * @param range RU: слышимость в блоках, {@link #RANGE_FROM_CONFIG} - из конфига | US: range in blocks, {@link #RANGE_FROM_CONFIG} takes it from the config
 */
public record RadioStation(UUID id, String name, List<String> tracks, boolean on, float range) {

    public static final float RANGE_FROM_CONFIG = -1F;

    public static final Codec<RadioStation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RadioStation::id),
            Codec.STRING.fieldOf("name").forGetter(RadioStation::name),
            Codec.STRING.listOf().fieldOf("tracks").forGetter(RadioStation::tracks),
            Codec.BOOL.fieldOf("on").forGetter(RadioStation::on),
            Codec.FLOAT.fieldOf("range").forGetter(RadioStation::range)
    ).apply(instance, RadioStation::new));

    public static final StreamCodec<ByteBuf, RadioStation> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RadioStation::id,
            ByteBufCodecs.STRING_UTF8, RadioStation::name,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RadioStation::tracks,
            ByteBufCodecs.BOOL, RadioStation::on,
            ByteBufCodecs.FLOAT, RadioStation::range,
            RadioStation::new
    );

    public RadioStation {
        tracks = List.copyOf(tracks);
    }

    public static RadioStation create(String name, List<String> tracks) {
        return new RadioStation(UUID.randomUUID(), name, tracks, false, RANGE_FROM_CONFIG);
    }

    public RadioStation withTracks(List<String> newTracks) {
        return new RadioStation(id, name, newTracks, on, range);
    }

    public RadioStation withName(String newName) {
        return new RadioStation(id, newName, tracks, on, range);
    }

    public RadioStation withOn(boolean nowOn) {
        return new RadioStation(id, name, tracks, nowOn, range);
    }

    public RadioStation toggled() {
        return withOn(!on);
    }

    public RadioStation withRange(float newRange) {
        return new RadioStation(id, name, tracks, on, newRange);
    }

    /** RU: при копировании нужен новый id, это ключ стрима | US: a copy needs a new id, it keys the stream */
    public RadioStation withNewId() {
        return new RadioStation(UUID.randomUUID(), name, tracks, on, range);
    }

    public boolean hasTracks() {
        return !tracks.isEmpty();
    }
}
