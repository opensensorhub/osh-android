package org.sensorhub.android;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public final class UnsafeTls
{
    private static final X509TrustManager TRUST_ALL_CERTIFICATES = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    };

    private static final HostnameVerifier TRUST_ALL_HOSTNAMES = (hostname, session) -> true;
    private static final SSLSocketFactory SOCKET_FACTORY = createSocketFactory();

    private UnsafeTls() {}

    public static OkHttpClient insecureClient(OkHttpClient baseClient) {
        return configure(baseClient.newBuilder()).build();
    }

    public static OkHttpClient.Builder configure(OkHttpClient.Builder builder) {
        return builder
            .sslSocketFactory(SOCKET_FACTORY, TRUST_ALL_CERTIFICATES)
            .hostnameVerifier(TRUST_ALL_HOSTNAMES);
    }

    public static void configure(HttpsURLConnection connection) {
        connection.setSSLSocketFactory(SOCKET_FACTORY);
        connection.setHostnameVerifier(TRUST_ALL_HOSTNAMES);
    }

    private static SSLSocketFactory createSocketFactory() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[] { TRUST_ALL_CERTIFICATES }, new SecureRandom());
            return context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to configure insecure TLS client", e);
        }
    }
}
