package dev.famjam.radiofm.radio;

public interface AudioDecoder {
    /**
     * RU: следующий кусок сэмплов, null - поток кончился
     * US: the next chunk of samples, null when the stream ended
     */
    short[] nextSamples() throws Exception;

    int getSampleRate();
    int getChannels();
    void close();

    /**
     * RU: длительность в секундах, -1 у стрима
     * US: length in seconds, -1 for a live stream
     */
    default long getDurationSeconds() { return -1; }
}
