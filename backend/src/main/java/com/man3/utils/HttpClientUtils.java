package com.man3.utils;

import com.man3.config.IkanmhProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 基于 Jsoup 的 HTTP 请求工具(带重试)
 */
@Slf4j
@Component
public class HttpClientUtils {

    private final IkanmhProperties props;

    public HttpClientUtils(IkanmhProperties props) {
        this.props = props;
    }

    /**
     * GET 请求并解析 HTML, 失败自动重试
     */
    public Document get(String url) throws IOException {
        IOException lastError = null;
        for (int i = 1; i <= props.getRetryTimes(); i++) {
            try {
                org.jsoup.Connection connection = Jsoup.connect(url)
                        .userAgent(props.getUserAgent())
                        .referrer(props.getBaseUrl() + "/")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Cache-Control", "no-cache")
                        .timeout(props.getTimeoutMs())
                        .ignoreContentType(false);
                if (props.isProxyEnabled()) {
                    connection.proxy(props.getProxyHost(), props.getProxyPort());
                }
                return connection.get();
            } catch (IOException e) {
                lastError = e;
                log.warn("请求失败 {}(第{}/{}次): {}", url, i, props.getRetryTimes(), e.getMessage());
                if (i < props.getRetryTimes()) {
                    CrawlerUtils.sleep(1000L * i);
                }
            }
        }
        throw lastError;
    }

    /**
     * GET 请求下载二进制数据(用于获取图片字节大小/尺寸), 失败自动重试
     * 使用 HttpURLConnection 以精确控制连接/读取超时, 避免源站慢响应导致无限挂起
     */
    public byte[] downloadBytes(String url) throws IOException {
        IOException lastError = null;
        int timeout = Math.max(props.getTimeoutMs(), 10000);
        for (int i = 1; i <= props.getRetryTimes(); i++) {
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL u = new java.net.URL(url);
                conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setRequestProperty("User-Agent", props.getUserAgent());
                conn.setRequestProperty("Referer", props.getBaseUrl() + "/");
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8");
                if (props.isProxyEnabled()) {
                    java.net.Proxy proxy = new java.net.Proxy(
                            java.net.Proxy.Type.HTTP,
                            new java.net.InetSocketAddress(props.getProxyHost(), props.getProxyPort()));
                    conn = (java.net.HttpURLConnection) u.openConnection(proxy);
                    conn.setConnectTimeout(timeout);
                    conn.setReadTimeout(timeout);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", props.getUserAgent());
                    conn.setRequestProperty("Referer", props.getBaseUrl() + "/");
                }
                try (InputStream in = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    int total = 0;
                    // 单张图最大 20MB 保护, 防止异常大文件耗尽内存
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        total += n;
                        if (total > 20 * 1024 * 1024) {
                            throw new IOException("图片超过20MB, 终止下载: " + url);
                        }
                    }
                    return out.toByteArray();
                }
            } catch (IOException e) {
                lastError = e;
                log.warn("下载失败 {}(第{}/{}次): {}", url, i, props.getRetryTimes(), e.getMessage());
                if (i < props.getRetryTimes()) {
                    CrawlerUtils.sleep(1000L * i);
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw lastError;
    }
}
