package dev.famjam.radiofm.radio;

public interface AudioDecoder {
    /**
     * ru: следующий кусок сэмплов, null — поток кончился
     * en: the next chunk of samples, null when the stream ended
     */
    short[] nextSamples() throws Exception;

    int getSampleRate();
    int getChannels();
    void close();

    /**
     * ru: длительность в секундах, -1 у стрима
     * en: length in seconds, -1 for a live stream
     */
    default long getDurationSeconds() { return -1; }
}
