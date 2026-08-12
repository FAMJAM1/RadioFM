package dev.famjam.radiofm.radio;

public class AudioDecoderFactory {
    public static AudioDecoder create(String url) throws Exception {
        // ru: ссылку задаёт игрок, ходит по ней сервер | en: the player sets the link, the server fetches it
        SafeUrls.ensureAllowed(url);

        if (SoundCloudResolver.isSoundCloud(url)) {
            url = SoundCloudResolver.resolve(url);
            SafeUrls.ensureAllowed(url); // ru: адрес новый, тоже чужой | en: a new address, also untrusted
        }
        return new LavaPlayerAudioDecoder(url);
    }
}
