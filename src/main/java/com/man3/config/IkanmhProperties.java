package com.man3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ikanmh 站点爬虫配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "crawler.ikanmh")
public class IkanmhProperties {

    /** 站点根地址 */
    private String baseUrl;

    /** 浏览器 User-Agent */
    private String userAgent;

    /** 连接超时(毫秒) */
    private int timeoutMs;

    /** 失败重试次数 */
    private int retryTimes;

    /** 是否走代理 */
    private boolean proxyEnabled;

    /** 代理主机 */
    private String proxyHost;

    /** 代理端口 */
    private int proxyPort;

    /** 列表页请求间隔(毫秒) */
    private long listRequestIntervalMs;

    /** 详情页请求间隔(毫秒) */
    private long detailRequestIntervalMs;
}
