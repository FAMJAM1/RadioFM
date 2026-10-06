package dev.famjam.radiofm.radio;

public class AudioDecoderFactory {
    public static AudioDecoder create(String url) throws Exception {
        // RU: ссылку задаёт игрок, ходит по ней сервер | US: the player sets the link, the server fetches it
        SafeUrls.ensureAllowed(url);

        if (SoundCloudResolver.isSoundCloud(url)) {
            url = SoundCloudResolver.resolve(url);
            SafeUrls.ensureAllowed(url); // RU: адрес новый, тоже чужой | US: a new address, also untrusted
        }
        return new LavaPlayerAudioDecoder(url);
    }
}
