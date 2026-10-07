package dev.famjam.radiofm.voice.svc;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import dev.famjam.radiofm.voice.ReadyQueue;
import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

public final class SvcBackend extends ReadyQueue {

    /**
     * RU: своя категория громкости; каналу передаётся id, а не объект
     * US: our own volume category; the channel takes the id, not the object
     */
    static final String RADIOS_CATEGORY = "radiofm";

    private static SvcBackend instance;

    private volatile VoicechatServerApi api;

    private SvcBackend() {
    }

    public static VoiceBackend create() {
        instance = new SvcBackend();
        return instance;
    }

    static void onVoiceChatStarted(VoicechatServerApi api) {
        SvcBackend backend = instance;
        if (backend != null) {
            backend.api = api;
            backend.markReady();
        }
    }

    @Override
    public VoiceOutput openAt(ServerLevel level, BlockPos pos, float range, Supplier<short[]> frames) {
        VoicechatServerApi current = api;
        if (current == null) {
            return null;
        }
        Position position = current.createPosition(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        LocationalAudioChannel channel = current.createLocationalAudioChannel(
                UUID.randomUUID(), current.fromServerLevel(level), position);
        return play(current, channel, range, frames);
    }

    @Override
    public VoiceOutput openOn(ServerPlayer player, float range, Supplier<short[]> frames) {
        VoicechatServerApi current = api;
        if (current == null) {
            return null;
        }
        EntityAudioChannel channel = current.createEntityAudioChannel(
                UUID.randomUUID(), current.fromServerPlayer(player));
        return play(current, channel, range, frames);
    }

    private static VoiceOutput play(VoicechatServerApi api, AudioChannel channel, float range, Supplier<short[]> frames) {
        if (channel == null) {
            return null;
        }
        if (channel instanceof LocationalAudioChannel locational) {
            locational.setDistance(range);
        } else if (channel instanceof EntityAudioChannel entity) {
            entity.setDistance(range);
        }
        channel.setCategory(RADIOS_CATEGORY);
        AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(OpusEncoderMode.AUDIO), frames);
        return new VoiceOutput() {
            @Override
            public void start() {
                player.startPlaying();
            }

            @Override
            public void stop() {
                player.stopPlaying();
            }

            @Override
            public void setRange(float range) {
                if (channel instanceof LocationalAudioChannel locational) {
                    locational.setDistance(range);
                } else if (channel instanceof EntityAudioChannel entity) {
                    entity.setDistance(range);
                }
            }
        };
    }

    @Override
    public void reset() {
        api = null;
        super.reset();
    }
}
