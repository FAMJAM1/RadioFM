package dev.famjam.radiofm.radio;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.famjam.radiofm.RadioFM;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.io.File;

public class LavaPlayerManager {

    private static final AudioPlayerManager INSTANCE;

    static {
        String tmpDir = System.getProperty("java.io.tmpdir") + File.separator + "lavaplayer-natives";
        new File(tmpDir).mkdirs();
        System.setProperty("nativelibs.tmpdir", tmpDir);

        INSTANCE = new DefaultAudioPlayerManager();
        INSTANCE.setFrameBufferDuration(10000); // ru: 10 секунд буфера | en: a ten second buffer
        // ru: стерео 44.1кГц, пересчитает StreamConverter | en: stereo 44.1kHz, StreamConverter resamples it
        INSTANCE.getConfiguration().setOutputFormat(
            com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats.COMMON_PCM_S16_BE
        );

        // ru: отдельный менеджер YouTube — у встроенного устарел cipher | en: a separate YouTube manager, the built-in cipher is stale
        register(new YoutubeAudioSourceManager());
        register(SoundCloudAudioSourceManager.createDefault());
        register(new BandcampAudioSourceManager());
        register(new VimeoAudioSourceManager());
        register(new TwitchStreamAudioSourceManager());
        register(new HttpAudioSourceManager());
    }

    /**
     * ru: свой резолвер имён — режет внутренние адреса на каждом соединении,
     *     значит и на редиректах, по которым LavaPlayer ходит сам
     * en: our own name resolver — refuses internal addresses per connection, so
     *     redirects LavaPlayer follows on its own are covered too
     */
    private static void register(AudioSourceManager manager) {
        if (manager instanceof HttpConfigurable configurable) {
            configurable.configureBuilder(builder -> builder.setDnsResolver(new SafeDns()));
        } else {
            RadioFM.LOGGER.warn("{} does not expose its HTTP client, its requests stay unchecked",
                    manager.getClass().getSimpleName());
        }
        INSTANCE.registerSourceManager(manager);
    }

    public static AudioPlayerManager get() {
        return INSTANCE;
    }
}
