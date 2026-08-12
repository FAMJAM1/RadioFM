package dev.famjam.radiofm.radio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.RadioVoicechatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ru: движок воспроизведения; наследники задают только канал звука
 * en: playback engine; subclasses only choose the audio channel
 * <p>
 * ru: {@link #get()} зовёт аудиопоток чата каждые 20мс — блокировать нельзя,
 *     сеть уходит в фоновые потоки, до готовности отдаём тишину
 * en: {@link #get()} runs on the chat audio thread every 20ms — never block it,
 *     network work goes to background threads and silence fills the gap
 */
public abstract class RadioStream implements Supplier<short[]> {

    /** ru: 20мс при 48кГц | en: 20ms at 48kHz */
    protected static final int FRAME_SIZE = 960;

    protected final RadioStation station;
    protected final UUID id;

    private final List<String> playlist;

    private volatile boolean active;
    private volatile boolean paused;
    private volatile boolean shuffle;
    /** ru: -1 — повтор выключен | en: -1 means repeat off */
    private volatile int repeatTrackIndex = -1;

    private volatile int currentTrackIndex;
    private volatile boolean skipRequested;
    private volatile int requestedIndex = -1;

    private volatile AudioChannel channel;
    private volatile AudioPlayer audioPlayer;
    private volatile AudioDecoder decoder;
    /** ru: подставляется, когда текущий доиграл | en: swapped in once the current one ends */
    private volatile AudioDecoder pendingDecoder;
    private volatile int pendingIndex = -1;
    private volatile StreamConverter converter;
    private volatile boolean trackLoading;

    private volatile long startedAt = -1;
    private volatile long pausedTotal;
    private volatile long pausedAt = -1;

    protected RadioStream(RadioStation station, UUID id) {
        this.station = station;
        this.id = id;
        this.playlist = resolvePlaylist(station);
    }

    /** ru: ссылка может быть плейлистом | en: a link may be a playlist */
    private static List<String> resolvePlaylist(RadioStation station) {
        List<String> resolved = new ArrayList<>();
        for (String raw : station.tracks()) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                resolved.addAll(PlaylistLoader.load(trimmed));
            }
        }
        return List.copyOf(resolved);
    }

    // ru: различия наследников | en: what subclasses differ in

    /** ru: канал вместе с дальностью и категорией | en: channel, its range and category */
    protected abstract AudioChannel openChannel(VoicechatServerApi api);

    protected abstract String threadPrefix();

    /** ru: старт не удался | en: could not start */
    protected void onStartFailed() {
    }

    /** ru: на каждом кадре | en: on every frame */
    protected void onFrame() {
    }

    protected float configuredRange() {
        return station.range() > 0
                ? station.range()
                : RadioFM.SERVER_CONFIG.radioRange.get().floatValue();
    }

    // ru: запуск и остановка | en: start and stop

    public void start() {
        if (playlist.isEmpty()) {
            RadioFM.LOGGER.warn("Radio {} has no tracks to play", id);
            return;
        }
        RadioVoicechatPlugin.runWhenReady(this::openAndPlay);
    }

    private void openAndPlay() {
        try {
            VoicechatServerApi api = RadioVoicechatPlugin.voicechatServerApi;
            if (api == null) {
                return;
            }

            AudioChannel opened = openChannel(api);
            if (opened == null) {
                RadioFM.LOGGER.error("Could not open an audio channel for radio {}", id);
                return;
            }
            channel = opened;
            audioPlayer = api.createAudioPlayer(opened, api.createEncoder(OpusEncoderMode.AUDIO), this);

            loadFirstTrackAsync();
        } catch (Exception e) {
            RadioFM.LOGGER.error("Failed to set up radio {}", id, e);
        }
    }

    private void loadFirstTrackAsync() {
        Thread thread = new Thread(() -> {
            try {
                // ru: повтор мог быть задан на выключенном радио | en: repeat may be set while off
                currentTrackIndex = repeatTrackIndex >= 0 ? repeatTrackIndex : 0;
                skipRequested = false;
                loadCurrentTrack();

                AudioPlayer player = audioPlayer;
                if (player == null) {
                    return; // ru: успели выключить | en: switched off meanwhile
                }
                // ru: строго до startPlaying(): null из get() чат считает концом потока
                // en: before startPlaying(): null from get() means end of stream to the chat
                active = true;
                player.startPlaying();
            } catch (Exception e) {
                active = false;
                RadioFM.LOGGER.error("Failed to start radio {}", id, e);
                onStartFailed();
            }
        }, threadPrefix() + "-start-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        active = false;
        paused = false;

        AudioPlayer player = audioPlayer;
        audioPlayer = null;
        if (player != null) {
            player.stopPlaying();
        }

        closeQuietly(decoder);
        decoder = null;
        discardPending();
        converter = null;
        channel = null;
    }

    // ru: загрузка треков | en: loading tracks

    private void loadCurrentTrack() throws Exception {
        converter = null;
        resetClock();

        String url = playlist.get(currentTrackIndex);
        RadioFM.LOGGER.info("Radio {} plays track {}/{}: {}", id, currentTrackIndex + 1, playlist.size(), url);

        AudioDecoder previous = decoder;
        try {
            decoder = openDecoder(url);
        } catch (Exception e) {
            active = false;
            throw e;
        } finally {
            closeQuietly(previous);
        }
    }

    private AudioDecoder openDecoder(String url) throws Exception {
        AudioDecoder opened = AudioDecoderFactory.create(url);
        if (opened instanceof LavaPlayerAudioDecoder lavaPlayer) {
            // ru: декодер предупреждает заранее | en: the decoder warns ahead of time
            lavaPlayer.setOnTrackEndCallback(() -> {
                if (repeatTrackIndex >= 0 || playlist.size() > 1) {
                    prepareNextTrack();
                }
            });
        }
        return opened;
    }

    private int computeNextIndex() {
        if (repeatTrackIndex >= 0) {
            return repeatTrackIndex;
        }
        if (shuffle) {
            int next;
            do {
                next = (int) (Math.random() * playlist.size());
            } while (playlist.size() > 1 && next == currentTrackIndex);
            return next;
        }
        return (currentTrackIndex + 1) % playlist.size();
    }

    /**
     * ru: готовит следующий трек, не переключаясь; подмена — в {@link #get()}
     * en: preloads the next track without switching; {@link #get()} swaps it in
     */
    private void prepareNextTrack() {
        if (trackLoading || pendingDecoder != null) {
            return;
        }
        int next = computeNextIndex();
        trackLoading = true;

        Thread thread = new Thread(() -> {
            try {
                AudioDecoder prepared = openDecoder(playlist.get(next));
                pendingIndex = next;
                pendingDecoder = prepared;
            } catch (Exception e) {
                RadioFM.LOGGER.error("Failed to preload the next track", e);
            } finally {
                trackLoading = false;
            }
        }, threadPrefix() + "-preload-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * ru: перемотка в фоне — открытие трека это HTTP-запросы
     * en: skipping in the background — opening a track means HTTP requests
     */
    private void startSkipLoad(int index) {
        discardPending(); // ru: заготовка устарела | en: the preloaded one is stale

        AudioDecoder old = decoder;
        decoder = null;
        converter = null;
        currentTrackIndex = index;
        trackLoading = true;

        Thread thread = new Thread(() -> {
            try {
                AudioDecoder loaded = openDecoder(playlist.get(index));
                resetClock();
                decoder = loaded;
            } catch (Exception e) {
                RadioFM.LOGGER.warn("Failed to load track {} on skip", index, e);
                onStartFailed();
            } finally {
                trackLoading = false;
                closeQuietly(old);
            }
        }, threadPrefix() + "-skip-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    /** ru: заготовку выбирали по прежним режимам | en: the preload was picked under old modes */
    private void discardPending() {
        AudioDecoder stale = pendingDecoder;
        pendingDecoder = null;
        pendingIndex = -1;
        closeQuietly(stale);
    }

    /** ru: встаём на подготовленный | en: switch to the preloaded one */
    private boolean advanceToPending() {
        AudioDecoder next = pendingDecoder;
        if (next == null) {
            return false;
        }

        AudioDecoder old = decoder;
        decoder = next;
        currentTrackIndex = pendingIndex;
        pendingDecoder = null;
        pendingIndex = -1;
        converter = null;
        resetClock();

        closeQuietly(old);
        return true;
    }

    private static void closeQuietly(AudioDecoder decoder) {
        if (decoder == null) {
            return;
        }
        try {
            decoder.close();
        } catch (Exception e) {
            RadioFM.LOGGER.warn("Failed to close an audio decoder", e);
        }
    }

    // ru: выдача звука | en: producing audio

    @Override
    public short[] get() {
        if (!active) {
            return null;
        }
        if (paused) {
            return silence();
        }

        if (skipRequested) {
            skipRequested = false;
            startSkipLoad(requestedIndex);
        }

        onFrame();

        try {
            AudioDecoder current = decoder;
            if (current == null) {
                return trackLoading ? silence() : null;
            }

            // ru: кадр декодера 21.8мс против 20мс у чата — тянем только до полного
            //     кадра, иначе трек расходуется на 8.8% быстрее, чем звучит
            // en: decoder frames are 21.8ms against the chat's 20ms — pull only until a
            //     frame is full, or the track is consumed 8.8% faster than it plays
            boolean exhausted = false;
            while (converter == null || converter.getBufferSize() < FRAME_SIZE) {
                short[] samples = current.nextSamples();
                if (samples == null) {
                    exhausted = true;
                    break;
                }
                if (samples.length == 0) {
                    break; // ru: кадр не готов | en: no frame ready
                }
                if (converter == null) {
                    converter = new StreamConverter(current.getSampleRate(), current.getChannels());
                }
                converter.add(samples, 0, samples.length);
            }

            if (converter != null && converter.getBufferSize() >= FRAME_SIZE) {
                return converter.getFrame();
            }
            if (exhausted) {
                return onTrackExhausted();
            }
            return silence(); // ru: копим дальше | en: keep buffering
        } catch (Exception e) {
            RadioFM.LOGGER.warn("Radio {} stream error", id, e);
            stop();
            return null;
        }
    }

    private short[] onTrackExhausted() {
        // ru: сначала хвост из конвертера | en: drain the converter first
        if (converter != null && converter.getBufferSize() > 0) {
            return converter.drainFrame();
        }
        if (advanceToPending()) {
            return silence();
        }
        converter = null;
        if (trackLoading) {
            return silence();
        }
        if (playlist.size() <= 1 && repeatTrackIndex < 0) {
            stop();
            return null;
        }
        prepareNextTrack(); // ru: предзагрузка не сработала | en: preload did not happen
        return silence();
    }

    private static short[] silence() {
        return new short[FRAME_SIZE];
    }

    // ru: управление | en: controls

    public void skipNext() {
        skipTo((currentTrackIndex + 1) % playlist.size());
    }

    public void skipPrev() {
        skipTo((currentTrackIndex - 1 + playlist.size()) % playlist.size());
    }

    public void skipToIndex(int index) {
        if (index >= 0 && index < playlist.size()) {
            skipTo(index);
        }
    }

    private void skipTo(int index) {
        requestedIndex = index;
        skipRequested = true;
    }

    public void setShuffle(boolean shuffle) {
        if (this.shuffle == shuffle) {
            return;
        }
        this.shuffle = shuffle;
        discardPending(); // ru: выбор устарел | en: the choice is stale
    }

    /** ru: номер вне списка выключает повтор | en: an index outside the list turns repeat off */
    public void setRepeatTrack(int index) {
        int normalized = (index >= 0 && index < playlist.size()) ? index : -1;
        if (normalized == repeatTrackIndex) {
            return;
        }
        repeatTrackIndex = normalized;
        discardPending();
    }

    public void pause() {
        if (!active || paused) {
            return;
        }
        paused = true;
        pausedAt = System.currentTimeMillis();
    }

    public void resume() {
        if (!active || !paused) {
            return;
        }
        if (pausedAt >= 0) {
            pausedTotal += System.currentTimeMillis() - pausedAt;
        }
        pausedAt = -1;
        paused = false;
    }

    public void togglePause() {
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    // ru: состояние | en: state

    private void resetClock() {
        startedAt = System.currentTimeMillis();
        pausedTotal = 0;
        pausedAt = -1;
    }

    public long getElapsedSeconds() {
        if (startedAt < 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - startedAt - pausedTotal;
        if (pausedAt >= 0) {
            elapsed -= System.currentTimeMillis() - pausedAt;
        }
        return Math.max(0, elapsed / 1000);
    }

    public long getDurationSeconds() {
        AudioDecoder current = decoder;
        return current == null ? -1 : current.getDurationSeconds();
    }

    /** ru: строка для окна | en: string for the screen */
    public String describeState() {
        if (!active) {
            return "STOPPED";
        }
        return paused ? "PAUSED" : "ACTIVE";
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public boolean isRepeat() {
        return repeatTrackIndex >= 0;
    }

    public int getRepeatTrackIndex() {
        return repeatTrackIndex;
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public int getTrackCount() {
        return playlist.size();
    }

    public RadioStation getStation() {
        return station;
    }

    public UUID getId() {
        return id;
    }
}
