package dev.famjam.radiofm.radio;

public class DropboxUrls {

    /**
     * RU: www.dropbox.com даже с dl=1 отдаёт application/binary, и LavaPlayer
     *     такой поток не опознаёт; с dl.dropboxusercontent.com приходит audio/mpeg
     * US: www.dropbox.com serves application/binary even with dl=1 and LavaPlayer
     *     cannot tell what it is; dl.dropboxusercontent.com sends audio/mpeg
     */
    public static String toDirect(String url) {
        if (url == null || !url.contains("dropbox.com/")) return url;

        String fixed = url.replace("://www.dropbox.com/", "://dl.dropboxusercontent.com/");

        fixed = fixed.replace("?dl=0", "?dl=1").replace("&dl=0", "&dl=1");
        if (!fixed.contains("dl=1")) {
            fixed = fixed + (fixed.contains("?") ? "&" : "?") + "dl=1";
        }
        return fixed;
    }
}
