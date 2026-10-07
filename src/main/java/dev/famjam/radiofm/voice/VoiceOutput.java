package dev.famjam.radiofm.voice;

public interface VoiceOutput {

    void start();

    void stop();

    /** RU: дальность меняется на лету, без перезапуска трека | US: the range changes live, without restarting the track */
    default void setRange(float range) {
    }

    /** RU: название и автор текущего трека, null - неизвестно | US: title and author of the current track, null when unknown */
    default void setTrack(String title, String author) {
    }
}
