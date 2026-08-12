package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.config.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** ru: разворачивает плейлист в треки; обычная ссылка возвращается как есть | en: expands a playlist into tracks; a plain link is returned as is */
public class PlaylistLoader {

    private static final int TIMEOUT_MS = 5000;
    /** ru: сколько редиректов пройдём, проверяя каждый | en: redirects we follow, checking each */
    private static final int MAX_REDIRECTS = 5;

    public static List<String> load(String url) {
        String trimmed = url.trim();
        String withoutQuery = trimmed.split("\\?")[0].toLowerCase();

        if (withoutQuery.endsWith(".txt") || withoutQuery.endsWith(".m3u") || withoutQuery.endsWith(".pls")) {
            try {
                return loadFromRemote(trimmed);
            } catch (Exception e) {
                RadioFM.LOGGER.warn("Failed to load playlist from {}: {}", url, e.getMessage());
            }
        }

        List<String> single = new ArrayList<>();
        single.add(trimmed);
        return single;
    }

    private static List<String> loadFromRemote(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            List<String> urls = parse(reader);
            RadioFM.LOGGER.info("Loaded {} tracks from playlist {}", urls.size(), url);
            return urls;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * ru: редиректы вручную — публичная ссылка может увести внутрь сети
     * en: redirects by hand — a public link may lead inward
     */
    private static HttpURLConnection open(String url) throws IOException {
        String current = url;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SafeUrls.ensureAllowed(current);

            HttpURLConnection connection = (HttpURLConnection) new java.net.URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.connect();

            int status = connection.getResponseCode();
            if (status < 300 || status >= 400) {
                return connection;
            }

            String next = connection.getHeaderField("Location");
            connection.disconnect();
            if (next == null) {
                throw new IOException("Redirect without a target from " + current);
            }
            current = URI.create(current).resolve(next).toString();
        }
        throw new IOException("Too many redirects for " + url);
    }

    private static List<String> parse(BufferedReader reader) throws IOException {
        int maxTracks = limit(RadioFM.SERVER_CONFIG.maxTracksPerRadio.get(), 128);
        int maxBytes = limit(RadioFM.SERVER_CONFIG.maxPlaylistBytes.get(), 262_144);

        List<String> urls = new ArrayList<>();
        int read = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            // ru: без потолка огромный файл съест память | en: without a cap a huge file eats memory
            read += line.length() + 1;
            if (read > maxBytes) {
                RadioFM.LOGGER.warn("Playlist is over {} bytes, reading no further", maxBytes);
                break;
            }

            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue; // ru: пустое и комментарии | en: blanks and comments
            }
            if (line.toLowerCase().startsWith("file")) {
                int equals = line.indexOf('='); // ru: формат .pls | en: .pls format
                if (equals >= 0) {
                    line = line.substring(equals + 1).trim();
                }
            }
            if (!line.startsWith("http://") && !line.startsWith("https://")) {
                continue;
            }

            urls.add(line);
            if (urls.size() >= maxTracks) {
                RadioFM.LOGGER.warn("Playlist has more than {} tracks, taking no more", maxTracks);
                break;
            }
        }
        return urls;
    }

    /** ru: конфиг мог не успеть прочитаться | en: the config may not be read yet */
    private static int limit(int configured, int fallback) {
        return ServerConfig.SPEC.isLoaded() ? configured : fallback;
    }
}
