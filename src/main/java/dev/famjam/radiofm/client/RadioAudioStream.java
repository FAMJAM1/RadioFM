package dev.famjam.radiofm.client;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class RadioAudioStream implements AudioStream {

    private static final AudioFormat FORMAT = new AudioFormat(48000F, 16, 1, true, false);

    private final ClientRadioSource source;

    RadioAudioStream(ClientRadioSource source) {
        this.source = source;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    /** RU: размер, который просит игра (секунда), не соблюдаем - иначе задержка в секунды | US: the size the game asks for (a second) is ignored, or the delay grows to seconds */
    @Override
    public ByteBuffer read(int size) {
        short[] chunk = source.takeChunk();
        if (chunk == null) {
            return null;
        }
        // RU: OpenAL читает только прямой буфер | US: OpenAL only reads a direct buffer
        ByteBuffer buffer = BufferUtils.createByteBuffer(chunk.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : chunk) {
            buffer.putShort(sample);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void close() {
    }
}
