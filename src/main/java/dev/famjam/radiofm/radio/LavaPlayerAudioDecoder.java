package dev.famjam.radiofm.radio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LavaPlayerAudioDecoder implements AudioDecoder {

    private AudioPlayer player;
    private AudioTrack track;
    private final String url;
    private final AtomicBoolean trackEnded = new AtomicBoolean(false);
    private volatile long trackEndedAt = -1;
    private volatile boolean closed = false;
    private AudioEventAdapter listener;
    private int emptyFrameCount = 0;
    private volatile boolean nextTrackLoading = false;
    private volatile boolean preloadTriggered = false;

    // RU: за сколько до конца грузить следующий; на 10с не успевало и была тишина
    // US: how early to load the next one; ten seconds was short and left silence
    private static final long PRELOAD_LEAD_MS = 25_000;
    public void setNextTrackLoading(boolean loading) { this.nextTrackLoading = loading; }
    private Runnable onTrackEndCallback = null;

    public void setOnTrackEndCallback(Runnable callback) {
        this.onTrackEndCallback = callback;
    }

    // RU: LavaPlayer так подписывает прямые файлы без тегов | US: what LavaPlayer puts on plain files without tags
    private static final String UNKNOWN_TITLE = "Unknown title";
    private static final String UNKNOWN_AUTHOR = "Unknown artist";

    private final String originalUrl;
    private volatile String fallbackTitle;
    private volatile String fallbackAuthor;

    /** RU: для источников, где LavaPlayer не видит тегов (SoundCloud через прямой mp3) | US: for sources where LavaPlayer sees no tags (SoundCloud via a direct mp3) */
    public void setFallbackInfo(String title, String author) {
        this.fallbackTitle = title;
        this.fallbackAuthor = author;
    }

    @Override
    public String getTitle() {
        String title = track == null ? null : track.getInfo().title;
        if (title != null && !title.isBlank() && !UNKNOWN_TITLE.equals(title)) {
            return title;
        }
        if (fallbackTitle != null) {
            return fallbackTitle;
        }
        return fileName(originalUrl);
    }

    @Override
    public String getAuthor() {
        String author = track == null ? null : track.getInfo().author;
        if (author != null && !author.isBlank() && !UNKNOWN_AUTHOR.equals(author)) {
            return author;
        }
        return fallbackAuthor;
    }

    /** RU: последний кусок пути без расширения | US: the last path segment without its extension */
    private static String fileName(String url) {
        try {
            String path = java.net.URI.create(url).getPath();
            if (path == null || path.isEmpty() || path.endsWith("/")) {
                return null;
            }
            String name = java.net.URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), java.nio.charset.StandardCharsets.UTF_8);
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        } catch (Exception e) {
            return null;
        }
    }

    public LavaPlayerAudioDecoder(String originalUrl) throws Exception {
        this.originalUrl = originalUrl;
        this.url = DropboxUrls.toDirect(originalUrl);

        AudioPlayerManager manager = LavaPlayerManager.get();
        this.player = manager.createPlayer();

        this.listener = new AudioEventAdapter() {
            @Override
            public void onTrackStart(AudioPlayer p, AudioTrack t) {
                trackEnded.set(false);
                emptyFrameCount = 0;
                dev.famjam.radiofm.RadioFM.LOGGER.info("LavaPlayer track started: {}", t.getInfo().title);
            }
            @Override
            public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason) {
                long pos = t.getPosition();
                long dur = t.getDuration();
                dev.famjam.radiofm.RadioFM.LOGGER.info("LavaPlayer track ended: {} pos={}ms dur={}ms diff={}ms", reason, pos, dur, dur - pos);
                // RU: сорвавшийся поток LavaPlayer сразу отчитывается FINISHED, так падал SoundCloud без client_id
                // US: a failed LavaPlayer stream reports FINISHED right away, as SoundCloud did without a client_id
                if (!closed && pos > 1000 && (reason.mayStartNext || reason == AudioTrackEndReason.FINISHED)) {
                    trackEnded.set(true);
                    trackEndedAt = System.currentTimeMillis();
                    if (onTrackEndCallback != null) {
                        final Runnable cb = onTrackEndCallback;
                        Thread t2 = new Thread(cb::run, "RadioTrackEndCallback");
                        t2.setDaemon(true);
                        t2.start();
                    }
                } else if (pos <= 1000) {
                    dev.famjam.radiofm.RadioFM.LOGGER.info("Ignoring fake FINISHED event pos={}ms", pos);
                }
            }
        };
        this.player.addListener(this.listener);

        CountDownLatch latch = new CountDownLatch(1);
        final AudioTrack[] loadedTrack = new AudioTrack[1];
        final Exception[] loadError = new Exception[1];

        manager.loadItem(this.url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                loadedTrack[0] = audioTrack;
                latch.countDown();
            }
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack t = playlist.getSelectedTrack() != null
                        ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
                loadedTrack[0] = t;
                latch.countDown();
            }
            @Override
            public void noMatches() {
                loadError[0] = new Exception("No matches for: " + url);
                latch.countDown();
            }
            @Override
            public void loadFailed(FriendlyException exception) {
                loadError[0] = exception;
                latch.countDown();
            }
        });

        if (!latch.await(30, TimeUnit.SECONDS))
            throw new Exception("Timeout loading: " + url);
        if (loadError[0] != null)
            throw new Exception("Failed to load: " + url, loadError[0]);
        if (loadedTrack[0] == null)
            throw new Exception("Track is null for: " + url);

        this.track = loadedTrack[0];
        this.player.playTrack(this.track);
        dev.famjam.radiofm.RadioFM.LOGGER.info("LavaPlayer loaded: {} ({}s)", track.getInfo().title, getDurationSeconds());
    }

    @Override
    public short[] nextSamples() {
        if (player == null || track == null) return new short[0];
        AudioFrame frame;
        try { frame = player.provide(20, TimeUnit.MILLISECONDS); }
        catch (Exception e) { frame = null; }
        if (frame == null) {
            if (trackEnded.get() && !nextTrackLoading) {
                emptyFrameCount++;
                // RU: 500мс пустых кадров - буфер точно пуст | US: 500ms of empty frames, the buffer is drained
                if (emptyFrameCount > 25) {
                    dev.famjam.radiofm.RadioFM.LOGGER.info("nextSamples null: buffer empty");
                    return null;
                }
            }
            return new short[0];
        }
        if (frame.isTerminator()) {
            dev.famjam.radiofm.RadioFM.LOGGER.info("Terminator frame!");
            return null;
        }
        emptyFrameCount = 0;
        if (!preloadTriggered && onTrackEndCallback != null && track != null) {
            long pos = track.getPosition();
            long dur = track.getDuration();
            if (dur > 0 && dur != Long.MAX_VALUE && (dur - pos) < PRELOAD_LEAD_MS) {
                preloadTriggered = true;
                dev.famjam.radiofm.RadioFM.LOGGER.info("Preloading next track, {}ms left", dur - pos);
                final Runnable cb = onTrackEndCallback;
                Thread t = new Thread(cb::run, "RadioPreloader");
                t.setDaemon(true);
                t.start();
            }
        }
        byte[] data = frame.getData();
        short[] samples = new short[data.length / 2];
        for (int i = 0; i < samples.length; i++) {
            int b1 = data[i * 2] & 0xFF;
            int b2 = data[i * 2 + 1] & 0xFF;
            samples[i] = (short) ((b1 << 8) | b2);
        }
        return samples;
    }

    @Override public int getSampleRate() { return 44100; }
    @Override public int getChannels()   { return 2; }

    @Override
    public long getDurationSeconds() {
        if (track == null) return -1;
        long dur = track.getDuration();
        if (dur == Long.MAX_VALUE || dur <= 0) return -1;
        return dur / 1000;
    }

    public void setPaused(boolean paused) {
        if (player != null) player.setPaused(paused);
    }

    @Override
    public void close() {
        closed = true;
        if (player != null) {
            if (listener != null) player.removeListener(listener);
            player.stopTrack();
            player.destroy();
        }
    }
}
