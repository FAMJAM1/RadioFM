package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.voice.VoiceBackend;
import dev.famjam.radiofm.voice.VoiceBackends;
import dev.famjam.radiofm.voice.VoiceOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * RU: {@link #get()} зовёт аудиопоток голосового мода каждые 20мс - блокировать нельзя,
 *     сеть уходит в фоновые потоки, до готовности отдаём тишину
 * US: {@link #get()} runs on the voice mod's audio thread every 20ms - never block it,
 *     network work goes to background threads and silence fills the gap
 */
public abstract class RadioStream implements Supplier<short[]> {

    /** RU: 20мс при 48кГц | US: 20ms at 48kHz */
    protected static final int FRAME_SIZE = 960;

    protected final RadioStation station;
    protected final UUID id;

    /** RU: ссылка из окна и треки, в которые она раскрылась (плейлист - во много) | US: a link from the screen and the tracks it expanded to (a playlist - to many) */
    private record Segment(String raw, List<String> tracks) {
    }

    private volatile List<Segment> segments;
    private volatile List<String> playlist;
    /** RU: растёт при перестановке, чтобы поздняя предзагрузка по старой очереди не встала | US: grows on reorder so a late preload from the old queue is not used */
    private volatile int queueGeneration;

    private volatile boolean active;
    private volatile boolean paused;
    private volatile boolean shuffle;
    /** RU: -1 - повтор выключен | US: -1 means repeat off */
    private volatile int repeatTrackIndex = -1;

    private volatile int currentTrackIndex;
    private volatile boolean skipRequested;
    private volatile int requestedIndex = -1;

    private volatile VoiceOutput output;
    /** RU: дальность, изменённая уже во время игры | US: a range changed while playing */
    private volatile float rangeOverride = -1;
    private volatile AudioDecoder decoder;
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
        this.segments = resolveSegments(station);
        this.playlist = flatten(segments);
    }

    /** RU: ссылка может быть плейлистом | US: a link may be a playlist */
    private static List<Segment> resolveSegments(RadioStation station) {
        List<Segment> resolved = new ArrayList<>();
        for (String raw : station.tracks()) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                resolved.add(new Segment(trimmed, List.copyOf(PlaylistLoader.load(trimmed))));
            }
        }
        return List.copyOf(resolved);
    }

    private static List<String> flatten(List<Segment> segments) {
        List<String> all = new ArrayList<>();
        segments.forEach(segment -> all.addAll(segment.tracks()));
        return List.copyOf(all);
    }

    /**
     * RU: те же ссылки в другом порядке: текущий трек доигрывает, дальше идём по новой очереди,
     *     плейлисты заново не скачиваются
     * US: the same links in another order: the current track plays on, then the new queue is
     *     followed, playlists are not downloaded again
     *
     * @return RU: false - список изменился не только порядком, нужен перезапуск | US: false when the list changed beyond its order, a restart is needed
     */
    public synchronized boolean reorder(List<String> rawTracks) {
        List<Segment> old = segments;
        List<Segment> pool = new ArrayList<>(old);
        List<Segment> next = new ArrayList<>();
        for (String raw : rawTracks) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int found = -1;
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i) != null && pool.get(i).raw().equals(trimmed)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            next.add(pool.get(found));
            pool.set(found, null);
        }
        if (next.size() != old.size()) {
            return false;
        }

        int current = currentTrackIndex;
        Segment playing = null;
        int offset = 0;
        int start = 0;
        for (Segment segment : old) {
            if (current < start + segment.tracks().size()) {
                playing = segment;
                offset = current - start;
                break;
            }
            start += segment.tracks().size();
        }
        int moved = 0;
        start = 0;
        for (Segment segment : next) {
            if (segment == playing) {
                moved = start + offset;
                break;
            }
            start += segment.tracks().size();
        }

        queueGeneration++;
        segments = List.copyOf(next);
        playlist = flatten(next);
        currentTrackIndex = moved;
        discardPending();
        return true;
    }

    protected abstract VoiceOutput openOutput(VoiceBackend backend);

    protected abstract String threadPrefix();

    /** RU: трек грузится в фоне, поэтому о сбоях сообщаем отсюда | US: tracks load in the background, so failures are reported from here */
    protected void tell(String translationKey) {
    }

    protected void onFrame() {
    }

    protected float configuredRange() {
        float override = rangeOverride;
        if (override > 0) {
            return override;
        }
        return station.range() > 0
                ? station.range()
                : RadioFM.SERVER_CONFIG.radioRange.get().floatValue();
    }

    public void setRange(float range) {
        rangeOverride = range;
        VoiceOutput out = output;
        if (out != null) {
            out.setRange(configuredRange());
        }
    }

    public void start() {
        if (playlist.isEmpty()) {
            RadioFM.LOGGER.warn("Radio {} has no tracks to play", id);
            return;
        }
        VoiceBackend backend = VoiceBackends.current();
        if (backend == null) {
            RadioFM.LOGGER.warn("Radio {} has no voice output chosen yet", id);
            return;
        }
        backend.runWhenReady(() -> openAndPlay(backend));
    }

    private void openAndPlay(VoiceBackend backend) {
        try {
            VoiceOutput opened = openOutput(backend);
            if (opened == null) {
                RadioFM.LOGGER.error("Could not open an audio channel for radio {}", id);
                return;
            }
            output = opened;

            loadFirstTrackAsync();
        } catch (Exception e) {
            RadioFM.LOGGER.error("Failed to set up radio {}", id, e);
        }
    }

    private void loadFirstTrackAsync() {
        Thread thread = new Thread(() -> {
            try {
                // RU: повтор мог быть задан на выключенном радио | US: repeat may be set while off
                currentTrackIndex = repeatTrackIndex >= 0 ? repeatTrackIndex : 0;
                skipRequested = false;
                loadCurrentTrack();

                VoiceOutput out = output;
                if (out == null) {
                    return; // RU: успели выключить | US: switched off meanwhile
                }
                // RU: строго до start(): null из get() голосовой мод считает концом потока
                // US: before start(): null from get() means end of stream to the voice mod
                active = true;
                out.start();
            } catch (Exception e) {
                active = false;
                RadioFM.LOGGER.error("Failed to start radio {}", id, e);
                tell("message.radiofm.start_error");
            }
        }, threadPrefix() + "-start-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        active = false;
        paused = false;

        VoiceOutput out = output;
        output = null;
        if (out != null) {
            out.stop();
        }

        closeQuietly(decoder);
        decoder = null;
        discardPending();
        converter = null;
    }

    private void loadCurrentTrack() throws Exception {
        converter = null;
        resetClock();

        String url = playlist.get(currentTrackIndex);
        RadioFM.LOGGER.info("Radio {} plays track {}/{}: {}", id, currentTrackIndex + 1, playlist.size(), url);

        AudioDecoder previous = decoder;
        try {
            decoder = openDecoder(url);
            announce(decoder);
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
            // RU: декодер предупреждает заранее | US: the decoder warns ahead of time
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
     * RU: готовит следующий трек, не переключаясь; подмена - в {@link #get()}
     * US: preloads the next track without switching; {@link #get()} swaps it in
     */
    private void prepareNextTrack() {
        if (trackLoading || pendingDecoder != null) {
            return;
        }
        int next = computeNextIndex();
        int generation = queueGeneration;
        String url = playlist.get(next);
        trackLoading = true;

        Thread thread = new Thread(() -> {
            try {
                AudioDecoder prepared = openDecoder(url);
                if (generation != queueGeneration) {
                    closeQuietly(prepared);
                    return;
                }
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
     * RU: перемотка в фоне - открытие трека это HTTP-запросы
     * US: skipping in the background - opening a track means HTTP requests
     */
    private void startSkipLoad(int index) {
        discardPending();

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
                announce(loaded);
            } catch (Exception e) {
                RadioFM.LOGGER.warn("Failed to load track {} on skip", index, e);
                tell("message.radiofm.start_error");
            } finally {
                trackLoading = false;
                closeQuietly(old);
            }
        }, threadPrefix() + "-skip-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    /** RU: заготовку выбирали по прежним режимам | US: the preload was picked under old modes */
    private void discardPending() {
        AudioDecoder stale = pendingDecoder;
        pendingDecoder = null;
        pendingIndex = -1;
        closeQuietly(stale);
    }

    private boolean advanceToPending() {
        AudioDecoder next = pendingDecoder;
        if (next == null) {
            return false;
        }

        AudioDecoder old = decoder;
        decoder = next;
        announce(next);
        currentTrackIndex = pendingIndex;
        pendingDecoder = null;
        pendingIndex = -1;
        converter = null;
        resetClock();

        closeQuietly(old);
        return true;
    }

    private void announce(AudioDecoder current) {
        VoiceOutput out = output;
        if (out != null && current != null) {
            out.setTrack(current.getTitle(), current.getAuthor());
        }
    }

    public String getTrackTitle() {
        AudioDecoder current = decoder;
        return current == null ? null : current.getTitle();
    }

    public String getTrackAuthor() {
        AudioDecoder current = decoder;
        return current == null ? null : current.getAuthor();
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

            // RU: кадр декодера 21.8мс против 20мс у чата - тянем только до полного
            //     кадра, иначе трек расходуется на 8.8% быстрее, чем звучит
            // US: decoder frames are 21.8ms against the chat's 20ms - pull only until a
            //     frame is full, or the track is consumed 8.8% faster than it plays
            boolean exhausted = false;
            while (converter == null || converter.getBufferSize() < FRAME_SIZE) {
                short[] samples = current.nextSamples();
                if (samples == null) {
                    exhausted = true;
                    break;
                }
                if (samples.length == 0) {
                    break; // RU: кадр не готов | US: no frame ready
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
            return silence();
        } catch (Exception e) {
            RadioFM.LOGGER.warn("Radio {} stream error", id, e);
            stop();
            return null;
        }
    }

    private short[] onTrackExhausted() {
        // RU: сначала хвост из конвертера | US: drain the converter first
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
        prepareNextTrack(); // RU: предзагрузка не сработала | US: preload did not happen
        return silence();
    }

    private static short[] silence() {
        return new short[FRAME_SIZE];
    }

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
        discardPending();
    }

    /** RU: номер вне списка выключает повтор | US: an index outside the list turns repeat off */
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
