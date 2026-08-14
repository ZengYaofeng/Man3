package com.man3.utils;

import com.man3.config.IkanmhProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
}
