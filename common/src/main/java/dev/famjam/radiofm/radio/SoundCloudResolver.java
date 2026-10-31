package dev.famjam.radiofm.radio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.famjam.radiofm.RadioFM;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RU: штатный источник SoundCloud в LavaPlayer без client_id получает отказ и сразу ложно
 *     завершает трек (FINISHED на 0 мс), поэтому client_id достаём из JS-бандла страницы
 * US: LavaPlayer's own SoundCloud source is refused without a client_id and ends the track
 *     at once with a fake FINISHED at 0ms, so the client_id is dug out of the page's JS bundle
 */
public class SoundCloudResolver {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile String cachedClientId = null;

    public static boolean isSoundCloud(String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.equals("soundcloud.com") || host.endsWith(".soundcloud.com");
    }

    public record Resolved(String url, String title, String author) {
    }

    public static Resolved resolve(String soundcloudUrl) throws Exception {
        RadioFM.LOGGER.info("SoundCloud: resolving {}", soundcloudUrl);

        String html = get(soundcloudUrl);

        String clientId = getClientId(html);
        RadioFM.LOGGER.info("SoundCloud: client_id={}", clientId);

        // RU: блок hydration на странице с описанием треков и их transcodings | US: the page's hydration blob describing tracks and their transcodings
        Matcher m = Pattern.compile("window\\.__sc_hydration = (\\[.*?\\]);").matcher(html);
        if (!m.find()) throw new Exception("SoundCloud: hydration not found");

        JsonArray hydration = JsonParser.parseString(m.group(1)).getAsJsonArray();
        String progressiveUrl = null;
        String title = null;
        String author = null;

        for (int i = 0; i < hydration.size(); i++) {
            JsonObject item = hydration.get(i).getAsJsonObject();
            if (!"sound".equals(item.get("hydratable").getAsString())) continue;

            JsonObject data = item.getAsJsonObject("data");
            title = text(data, "title");
            if (data.has("user") && data.get("user").isJsonObject()) {
                author = text(data.getAsJsonObject("user"), "username");
            }
            JsonArray transcodings = data
                    .getAsJsonObject("media")
                    .getAsJsonArray("transcodings");

            for (int j = 0; j < transcodings.size(); j++) {
                JsonObject t = transcodings.get(j).getAsJsonObject();
                JsonObject format = t.getAsJsonObject("format");
                if ("progressive".equals(format.get("protocol").getAsString())
                        && format.get("mime_type").getAsString().contains("mpeg")) {
                    progressiveUrl = t.get("url").getAsString();
                    break;
                }
            }
            break;
        }

        if (progressiveUrl == null) throw new Exception("SoundCloud: no progressive MP3 transcoding found");

        String streamJson = get(progressiveUrl + "?client_id=" + clientId);
        JsonObject streamObj = JsonParser.parseString(streamJson).getAsJsonObject();
        String mp3Url = streamObj.get("url").getAsString();

        RadioFM.LOGGER.info("SoundCloud: resolved to {}", mp3Url.substring(0, Math.min(80, mp3Url.length())));
        return new Resolved(mp3Url, title, author);
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String getClientId(String html) throws Exception {
        if (cachedClientId != null) return cachedClientId;

        Matcher scripts = Pattern.compile("src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]*\\.js)\"").matcher(html);
        while (scripts.find()) {
            String jsUrl = scripts.group(1);
            String js = get(jsUrl);
            Matcher cid = Pattern.compile("client_id:\"([a-zA-Z0-9]+)\"").matcher(js);
            if (cid.find()) {
                cachedClientId = cid.group(1);
                return cachedClientId;
            }
        }
        throw new Exception("SoundCloud: client_id not found");
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new Exception("SoundCloud HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }
}
