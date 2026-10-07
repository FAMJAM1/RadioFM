package dev.famjam.radiofm.radio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DropboxUrls {

    private static final String DIRECT_HOST = "dl.dropboxusercontent.com";

    /**
     * RU: www.dropbox.com даже с dl=1 отдаёт application/binary, и LavaPlayer
     *     такой поток не опознаёт; с dl.dropboxusercontent.com приходит audio/mpeg
     * US: www.dropbox.com serves application/binary even with dl=1 and LavaPlayer
     *     cannot tell what it is; dl.dropboxusercontent.com sends audio/mpeg
     */
    public static String toDirect(String url) {
        if (url == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return url;
        }
        String host = uri.getHost();
        if (host == null) {
            return url;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("dropbox.com") && !host.equals("www.dropbox.com")) {
            return url;
        }

        // RU: rlkey и прочие параметры нужны ссылке, меняем только dl
        // US: rlkey and the other parameters are needed by the link, only dl changes
        List<String> params = new ArrayList<>();
        String query = uri.getRawQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (!param.isEmpty() && !param.startsWith("dl=")) {
                    params.add(param);
                }
            }
        }
        params.add("dl=1");
        return "https://" + DIRECT_HOST + uri.getRawPath() + "?" + String.join("&", params);
    }
}
