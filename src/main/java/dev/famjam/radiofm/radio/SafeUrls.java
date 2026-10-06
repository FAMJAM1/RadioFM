package dev.famjam.radiofm.radio;

import dev.famjam.radiofm.RadioFM;
import dev.famjam.radiofm.config.ServerConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * RU: ссылку вписывает игрок, а ходит по ней сервер - без проверки это SSRF
 * US: the player writes the link, the server fetches it - unchecked that is SSRF
 */
public final class SafeUrls {

    private SafeUrls() {
    }

    public static void ensureAllowed(String url) throws IOException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IOException("Malformed URL: " + url);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("Only http and https are allowed, got: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("URL has no host: " + url);
        }

        if (ServerConfig.SPEC.isLoaded() && RadioFM.SERVER_CONFIG.allowPrivateNetworks.get()) {
            return;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Unknown host: " + host);
        }

        // RU: хватает одного внутреннего адреса | US: one internal address is enough to refuse
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new IOException("Refusing to reach an internal address: " + host);
            }
        }
    }

    public static boolean isAllowed(String url) {
        try {
            ensureAllowed(url);
            return true;
        } catch (IOException e) {
            RadioFM.LOGGER.warn("Rejected URL: {}", e.getMessage());
            return false;
        }
    }

    static boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress()       // 127.0.0.0/8, ::1
                || address.isAnyLocalAddress()   // 0.0.0.0
                || address.isLinkLocalAddress()  // 169.254.0.0/16
                || address.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address);
    }

    /** RU: fc00::/7, метода для них нет | US: fc00::/7, no method covers these */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
