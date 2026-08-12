package dev.famjam.radiofm.radio;

/**
 * ru: приводит поток декодера к моно 48кГц, которое ждёт голосовой чат
 * en: brings the decoder's output to the mono 48kHz the voice chat expects
 * <p>
 * ru: каналы усредняются, частота — линейной интерполяцией; копим внутри,
 *     потому что кадр декодера 21.8мс, а чату нужно ровно 20мс
 * en: channels are averaged, the rate is interpolated; buffered here because a
 *     decoder frame is 21.8ms while the chat wants exactly 20ms
 */
public class StreamConverter {

    public static final int TARGET_SAMPLE_RATE = 48000;
    private static final int FRAME_SIZE = 960;

    private final int channels;
    /** ru: исходных сэмплов на один целевой | en: source samples per output sample */
    private final double step;

    private short[] buffer = new short[FRAME_SIZE * 8];
    private int size;

    /** ru: интерполяция идёт от него к текущему | en: interpolation runs from here to the current one */
    private short previous;
    private boolean hasPrevious;
    /** ru: позиция между ними, 0..1 | en: position between them, 0..1 */
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

    /** ru: сэмплы чередуются по каналам | en: samples are interleaved by channel */
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
        // ru: на один исходный может выпасть и ноль, и несколько | en: one source sample may yield none or several
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

    /** ru: кадр 20мс, нехватка добивается тишиной | en: a 20ms frame, padded with silence */
    public short[] getFrame() {
        short[] frame = new short[FRAME_SIZE];
        int taken = Math.min(FRAME_SIZE, size);
        System.arraycopy(buffer, 0, frame, 0, taken);
        System.arraycopy(buffer, taken, buffer, 0, size - taken);
        size -= taken;
        return frame;
    }

    /** ru: то же, но по смыслу «доиграть остаток» | en: the same, meaning "drain the tail" */
    public short[] drainFrame() {
        return getFrame();
    }
}
