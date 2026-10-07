package dev.famjam.radiofm.client;

import com.sedmelluq.discord.lavaplayer.natives.opus.OpusDecoder;
import dev.famjam.radiofm.network.RadioAudioPacket;
import net.minecraft.core.BlockPos;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;

/**
 * RU: одно играющее радио на клиенте; кадры кладёт сетевой поток, забирает поток звука,
 *     громкость считает основной
 * US: one playing radio on the client; the network thread pushes frames, the sound thread
 *     takes them, the main thread works out the volume
 */
final class ClientRadioSource {

    static final int FRAME_SIZE = 960;
    /**
     * RU: Minecraft держит в очереди 4 куска и подкачивает по одному; 60мс на кусок дают
     *     запас в 240мс, меньше - и при подтормаживании тика очередь пустеет и звук обрывается
     * US: Minecraft keeps 4 chunks queued and refills one at a time; 60ms a chunk gives
     *     240ms of slack, less and a slow tick drains the queue and the sound stops
     */
    static final int CHUNK_FRAMES = 3;
    /**
     * RU: 200мс запаса перед стартом и после провала: на 60мс пакеты, пришедшие пачкой
     *     после подвисания, давали дыры
     * US: 200ms of slack before starting and after running dry: with 60ms, packets arriving
     *     in a burst after a hitch left gaps
     */
    private static final int PREBUFFER_FRAMES = 10;
    /** RU: больше секунды - отстаём, лишнее выкидываем до запаса | US: past a second we lag behind, the excess is dropped back to the slack */
    private static final int MAX_FRAMES = 50;
    /** RU: столько без пакетов - радио ушло из зоны или сервер пропал | US: this long without packets - out of range or the server is gone */
    private static final long TIMEOUT_MS = 1500L;

    private final ArrayDeque<short[]> frames = new ArrayDeque<>();
    private boolean primed;
    private volatile boolean finished;

    private volatile BlockPos pos = BlockPos.ZERO;
    private volatile int entityId = -1;
    private volatile float range = 16F;
    private volatile long lastPacketAt = System.currentTimeMillis();
    private volatile String title;
    private volatile String author;

    // RU: только сетевой поток, close - под тем же замком | US: network thread only, close takes the same lock
    private OpusDecoder decoder;
    private ByteBuffer opusIn;
    private ShortBuffer pcmOut;

    // RU: только основной поток | US: main thread only
    RadioSoundInstance sound;
    long lastStartAttempt;
    boolean stopRequested;

    void accept(RadioAudioPacket packet) {
        pos = packet.pos();
        entityId = packet.entityId();
        range = packet.range();
        lastPacketAt = System.currentTimeMillis();
        short[] pcm = decode(packet.frame());
        if (pcm != null) {
            push(pcm);
        }
    }

    private synchronized short[] decode(byte[] frame) {
        if (finished && decoder == null && opusIn != null) {
            return null;
        }
        if (decoder == null) {
            decoder = new OpusDecoder(48000, 1);
            opusIn = ByteBuffer.allocateDirect(4000).order(ByteOrder.nativeOrder());
            // RU: кадр Opus бывает до 120мс | US: an Opus frame can be up to 120ms
            pcmOut = ByteBuffer.allocateDirect(FRAME_SIZE * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        }
        opusIn.clear();
        opusIn.put(frame, 0, Math.min(frame.length, opusIn.capacity()));
        opusIn.flip();
        pcmOut.clear();
        int samples = decoder.decode(opusIn, pcmOut);
        short[] pcm = new short[FRAME_SIZE];
        pcmOut.position(0);
        pcmOut.get(pcm, 0, Math.min(samples, FRAME_SIZE));
        return pcm;
    }

    private void push(short[] pcm) {
        synchronized (frames) {
            frames.add(pcm);
            if (frames.size() > MAX_FRAMES) {
                while (frames.size() > PREBUFFER_FRAMES) {
                    frames.poll();
                }
            }
        }
    }

    /**
     * RU: вызывает поток звука; тишина вместо пустоты держит канал живым, null - конец
     * US: called by the sound thread; silence instead of nothing keeps the channel alive, null ends it
     */
    short[] takeChunk() {
        synchronized (frames) {
            if (finished && frames.isEmpty()) {
                return null;
            }
            short[] chunk = new short[FRAME_SIZE * CHUNK_FRAMES];
            if (!primed && !finished && frames.size() < PREBUFFER_FRAMES) {
                return chunk;
            }
            primed = true;
            int offset = 0;
            while (offset < chunk.length && !frames.isEmpty()) {
                System.arraycopy(frames.poll(), 0, chunk, offset, FRAME_SIZE);
                offset += FRAME_SIZE;
            }
            if (offset < chunk.length) {
                primed = false;
            }
            return chunk;
        }
    }

    void setTrack(String title, String author) {
        this.title = title;
        this.author = author;
    }

    String title() {
        return title;
    }

    String author() {
        return author;
    }

    void end() {
        finished = true;
    }

    void checkTimeout(long now) {
        if (now - lastPacketAt > TIMEOUT_MS) {
            finished = true;
        }
    }

    boolean isFinished() {
        return finished;
    }

    BlockPos pos() {
        return pos;
    }

    int entityId() {
        return entityId;
    }

    float range() {
        return range;
    }

    synchronized void close() {
        finished = true;
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }
}
