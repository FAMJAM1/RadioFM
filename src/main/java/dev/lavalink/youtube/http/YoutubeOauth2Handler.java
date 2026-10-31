package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;

/**
 * RU: заглушка вместо класса youtube-source с тем же именем: оригинал умеет входить в аккаунт Google
 *     и хранить токен, и CurseForge отклонял мод из-за этого, хотя вход мы не включали. Менеджер YouTube
 *     создаёт этот класс сам, поэтому убрать его нельзя, только заменить; оригинал выкинут в shadowJar
 * US: a stand-in for the youtube-source class of the same name: the original can log in to a Google
 *     account and store the token, and CurseForge rejected the mod for it although we never turned login on.
 *     The YouTube manager creates this class itself, so it can only be replaced, not removed; the original
 *     is dropped in shadowJar
 */
public class YoutubeOauth2Handler {

    public static final String OAUTH_INJECT_CONTEXT_ATTRIBUTE = "yt-oauth-token";

    public YoutubeOauth2Handler(HttpInterfaceManager httpInterfaceManager) {
    }

    public void setRefreshToken(String refreshToken, boolean skipInitialization) {
    }

    public boolean hasAccessToken() {
        return false;
    }

    public boolean shouldRefreshAccessToken() {
        return false;
    }

    public String getRefreshToken() {
        return null;
    }

    public boolean isOauthFetchContext(HttpClientContext context) {
        return false;
    }

    public void refreshAccessToken(boolean force) {
    }

    public void applyToken(HttpUriRequest request) {
    }

    public void applyToken(HttpUriRequest request, String token) {
    }
}
