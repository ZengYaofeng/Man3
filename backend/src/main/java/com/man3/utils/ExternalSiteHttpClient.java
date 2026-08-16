package com.man3.utils;

import com.man3.config.IkanmhProperties;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 面向外部漫画目录站的轻量 HTML 客户端，仅请求公开 HTML 页面。 */
@Component
public class ExternalSiteHttpClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private final IkanmhProperties properties;

    public ExternalSiteHttpClient(IkanmhProperties properties) {
        this.properties = properties;
    }

    public Document get(String url) throws IOException {
        IOException lastError = null;
        int retries = Math.max(1, properties.getRetryTimes());

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                Connection connection = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(url)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .timeout(Math.max(10000, properties.getTimeoutMs()));
                if (properties.isProxyEnabled()) {
                    connection.proxy(properties.getProxyHost(), properties.getProxyPort());
                }
                return connection.get();
            } catch (IOException e) {
                lastError = e;
                if (attempt < retries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw new IOException("External site request failed after " + retries + " attempts: " + url, lastError);
    }

    /** Fetches a page together with any session cookies required by its image CDN. */
    public PageResponse getWithCookies(String url) throws IOException {
        IOException lastError = null;
        int retries = Math.max(1, properties.getRetryTimes());
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                Connection connection = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .referrer(url)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .timeout(Math.max(10000, properties.getTimeoutMs()));
                if (properties.isProxyEnabled()) connection.proxy(properties.getProxyHost(), properties.getProxyPort());
                Connection.Response response = connection.execute();
                return new PageResponse(response.parse(), response.cookies());
            } catch (IOException e) {
                lastError = e;
                if (attempt < retries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw new IOException("External site request failed after " + retries + " attempts: " + url, lastError);
    }

    public static class PageResponse {
        private final Document document;
        private final Map<String, String> cookies;

        private PageResponse(Document document, Map<String, String> cookies) {
            this.document = document;
            this.cookies = cookies == null ? Collections.emptyMap() : new HashMap<>(cookies);
        }

        public Document getDocument() { return document; }
        public Map<String, String> getCookies() { return Collections.unmodifiableMap(cookies); }
    }
}
