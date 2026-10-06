package dev.famjam.radiofm.radio;

/**
 * RU: приводит поток декодера к моно 48кГц, которое ждёт голосовой чат;
 *     копим внутри, потому что кадр декодера 21.8мс, а чату нужно ровно 20мс
 * US: brings the decoder's output to the mono 48kHz the voice chat expects;
 *     buffered here because a decoder frame is 21.8ms while the chat wants exactly 20ms
 */
public class StreamConverter {

    public static final int TARGET_SAMPLE_RATE = 48000;
    private static final int FRAME_SIZE = 960;

    private final int channels;
    /** RU: исходных сэмплов на один целевой | US: source samples per output sample */
    private final double step;

    private short[] buffer = new short[FRAME_SIZE * 8];
    private int size;

    /** RU: интерполяция идёт от него к текущему | US: interpolation runs from here to the current one */
    private short previous;
    private boolean hasPrevious;
    /** RU: позиция между ними, 0..1 | US: position between them, 0..1 */
    private double phase;

    public StreamConverter(int sourceSampleRate, int channels) {
        if (sourceSampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive, got " + sourceSampleRate);
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be positive, got " + channels);
        }
        this.channels = channels;
        this.step = (double) sourceSampleRate / TARGET_SAMPLE_RATE;
    }

    /** RU: сэмплы чередуются по каналам | US: samples are interleaved by channel */
    public void add(short[] samples, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i + channels <= end; i += channels) {
            feed(downmix(samples, i));
        }
    }

    private void feed(short current) {
        if (!hasPrevious) {
            previous = current;
            hasPrevious = true;
            return;
        }
        // RU: на один исходный может выпасть и ноль, и несколько | US: one source sample may yield none or several
        while (phase < 1d) {
            append((short) Math.round(previous + (current - previous) * phase));
            phase += step;
        }
        phase -= 1d;
        previous = current;
    }

    private short downmix(short[] samples, int index) {
        if (channels == 1) {
            return samples[index];
        }
        int sum = 0;
        for (int c = 0; c < channels; c++) {
            sum += samples[index + c];
        }
        return (short) (sum / channels);
    }

    private void append(short sample) {
        if (size == buffer.length) {
            short[] grown = new short[buffer.length * 2];
            System.arraycopy(buffer, 0, grown, 0, size);
            buffer = grown;
        }
        buffer[size++] = sample;
    }

    public int getBufferSize() {
        return size;
    }

    /** RU: кадр 20мс, нехватка добивается тишиной | US: a 20ms frame, padded with silence */
    public short[] getFrame() {
        short[] frame = new short[FRAME_SIZE];
        int taken = Math.min(FRAME_SIZE, size);
        System.arraycopy(buffer, 0, frame, 0, taken);
        System.arraycopy(buffer, taken, buffer, 0, size - taken);
        size -= taken;
        return frame;
    }

    public short[] drainFrame() {
        return getFrame();
    }
}
