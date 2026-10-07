package dev.famjam.radiofm.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import dev.famjam.radiofm.config.ClientConfig;
import dev.famjam.radiofm.network.RadioAudioEndPacket;
import dev.famjam.radiofm.network.RadioAudioPacket;
import dev.famjam.radiofm.network.RadioTrackPacket;
import dev.famjam.radiofm.screen.RadioScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RU: радио своего канала на клиенте; пакеты разбирает сетевой поток, чтобы подвисания
 *     отрисовки не задерживали кадры, а звук заводит основной
 * US: our channel's radios on the client; the network thread takes the packets, so render
 *     hitches do not delay frames, and the main thread starts the sound
 */
public final class OwnAudioClient {

    private static final Map<UUID, ClientRadioSource> SOURCES = new ConcurrentHashMap<>();
    /** RU: если игра не запускает звук, не долбим её каждый тик | US: if the game will not start the sound, do not retry every tick */
    private static final long RESTART_INTERVAL_MS = 1000L;
    /** RU: субтитр живёт 3с, обновляем чаще, пока трек играет | US: a subtitle lives 3s, refreshed more often while the track plays */
    private static final int SUBTITLE_REFRESH_TICKS = 20;
    private static int subtitleTicks;

    private OwnAudioClient() {
    }

    /** RU: сетевой поток | US: network thread */
    public static void onAudio(RadioAudioPacket packet) {
        ClientRadioSource source = SOURCES.compute(packet.source(), (id, existing) ->
                existing == null || existing.isFinished() ? new ClientRadioSource() : existing);
        source.accept(packet);
    }

    /** RU: сетевой поток | US: network thread */
    public static void onTrack(RadioTrackPacket packet) {
        ClientRadioSource source = SOURCES.compute(packet.source(), (id, existing) ->
                existing == null || existing.isFinished() ? new ClientRadioSource() : existing);
        source.setTrack(packet.title(), packet.author());
    }

    /** RU: сетевой поток | US: network thread */
    public static void onEnd(RadioAudioEndPacket packet) {
        ClientRadioSource source = SOURCES.get(packet.source());
        if (source != null) {
            source.end();
        }
    }

    /** RU: основной поток, каждый тик, и на паузе тоже | US: main thread, every tick, paused or not */
    public static void tick() {
        if (SOURCES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager sounds = minecraft.getSoundManager();
        Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
        float volume = ClientConfig.RADIO_VOLUME.get() / 100F;
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        long now = System.currentTimeMillis();
        boolean paused = minecraft.isPaused();
        boolean refreshSubtitles = ++subtitleTicks >= SUBTITLE_REFRESH_TICKS;
        if (refreshSubtitles) {
            subtitleTicks = 0;
        }

        Iterator<ClientRadioSource> it = SOURCES.values().iterator();
        while (it.hasNext()) {
            ClientRadioSource source = it.next();
            source.checkTimeout(now);
            if (source.isFinished()) {
                if (source.sound != null && sounds.isActive(source.sound) && !source.stopRequested) {
                    source.stopRequested = true;
                    sounds.stop(source.sound);
                }
                if (source.sound == null || !sounds.isActive(source.sound)) {
                    source.close();
                    it.remove();
                }
                continue;
            }
            ensurePlaying(sounds, source, now);
            drive(sounds, source, listener, volume, master, paused);
            if (refreshSubtitles) {
                showSubtitle(minecraft, source);
            }
        }
    }

    /**
     * RU: громкость и позицию ставим прямо источнику OpenAL. Общая громкость игры множит всё,
     *     поэтому делим на неё: SVC и PV её не слушают, и иначе при Master 26% радио было
     *     вчетверо тише. Пауза меню ставит на паузу все звуки игры, а радио, как и в SVC, играет
     *     дальше. Поля движка и канала открыты через accesstransformer.cfg
     * US: volume and position go straight to the OpenAL source. The game's master volume scales
     *     everything, so we divide by it: SVC and PV ignore it, and otherwise at 26% master the radio
     *     was four times quieter. The pause menu pauses every game sound, while the radio goes on
     *     as in SVC. Engine and channel fields are opened in accesstransformer.cfg
     */
    private static void drive(SoundManager sounds, ClientRadioSource source, Vec3 listener,
                              float volume, float master, boolean paused) {
        if (source.sound == null) {
            return;
        }
        ChannelAccess.ChannelHandle handle = sounds.soundEngine.instanceToChannel.get(source.sound);
        if (handle == null) {
            return;
        }
        Vec3 at = RadioSoundInstance.position(source);
        source.sound.moveTo(at);
        float gain = master <= 0F ? 0F : volume * falloff(listener, at, source.range()) / master;
        handle.execute(channel -> {
            channel.setSelfPosition(at);
            AL10.alSourcef(channel.source, AL10.AL_MAX_GAIN, Math.max(1F, gain));
            AL10.alSourcef(channel.source, AL10.AL_GAIN, gain);
            if (paused) {
                channel.unpause();
            }
        });
    }

    /** RU: в субтитрах игры - что сейчас играет | US: the game's subtitles show what is playing */
    private static void showSubtitle(Minecraft minecraft, ClientRadioSource source) {
        if (source.sound == null || !minecraft.options.showSubtitles().get()) {
            return;
        }
        Component line = RadioScreen.trackLine(source.title(), source.author());
        if (line != null) {
            minecraft.gui.subtitleOverlay.onPlaySound(source.sound,
                    new TrackSubtitle(RadioSoundInstance.location(), line), source.range());
        }
    }

    /** RU: линейно до края слышимости, как у игры | US: linear up to the edge of the range, like the game's */
    private static float falloff(Vec3 listener, Vec3 at, float range) {
        return (float) Math.max(0D, 1D - listener.distanceTo(at) / Math.max(1F, range));
    }

    /**
     * RU: игра может снять звук сама (лимит каналов, смена устройства), тогда заводим заново
     * US: the game may drop the sound itself (channel limit, device change), then it is started again
     */
    private static void ensurePlaying(SoundManager sounds, ClientRadioSource source, long now) {
        if (source.sound != null && sounds.isActive(source.sound)) {
            return;
        }
        if (now - source.lastStartAttempt < RESTART_INTERVAL_MS) {
            return;
        }
        source.lastStartAttempt = now;
        source.sound = new RadioSoundInstance(source);
        sounds.play(source.sound);
    }


    public static void clear() {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        for (ClientRadioSource source : SOURCES.values()) {
            source.close();
            if (source.sound != null) {
                sounds.stop(source.sound);
            }
        }
        SOURCES.clear();
    }
}
