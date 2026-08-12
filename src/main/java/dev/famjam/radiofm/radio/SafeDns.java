package dev.famjam.radiofm.radio;

import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ru: резолвер имён для LavaPlayer, режущий внутренние адреса
 * en: a name resolver for LavaPlayer that refuses internal addresses
 * <p>
 * ru: проверки одной ссылки мало — LavaPlayer сам идёт по редиректам; здесь
 *     проверка на уровне соединения, поэтому и подмена DNS закрыта
 * en: checking the link alone is not enough — LavaPlayer follows redirects; this
 *     sits at the connection, which also closes DNS rebinding
 */
public class SafeDns implements DnsResolver {

    private final DnsResolver delegate = SystemDefaultDnsResolver.INSTANCE;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);

        for (InetAddress address : addresses) {
            if (SafeUrls.isInternal(address)) {
                throw new UnknownHostException("Refusing to reach an internal address: " + host);
            }
        }
        return addresses;
    }
}
