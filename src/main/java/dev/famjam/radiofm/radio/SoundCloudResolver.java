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

public class SoundCloudResolver {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static volatile String cachedClientId = null;

    public static boolean isSoundCloud(String url) {
        return url.contains("soundcloud.com/");
    }

    public static String resolve(String soundcloudUrl) throws Exception {
        RadioFM.LOGGER.info("SoundCloud: resolving {}", soundcloudUrl);

        String html = get(soundcloudUrl);

        String clientId = getClientId(html);
        RadioFM.LOGGER.info("SoundCloud: client_id={}", clientId);

        // ru: разбираем блок hydration | en: parse the hydration blob
        Matcher m = Pattern.compile("window\\.__sc_hydration = (\\[.*?\\]);").matcher(html);
        if (!m.find()) throw new Exception("SoundCloud: hydration not found");

        JsonArray hydration = JsonParser.parseString(m.group(1)).getAsJsonArray();
        String progressiveUrl = null;

        for (int i = 0; i < hydration.size(); i++) {
            JsonObject item = hydration.get(i).getAsJsonObject();
            if (!"sound".equals(item.get("hydratable").getAsString())) continue;

            JsonArray transcodings = item.getAsJsonObject("data")
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
        return mp3Url;
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
