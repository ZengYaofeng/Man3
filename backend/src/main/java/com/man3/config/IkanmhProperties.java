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

    /** 图片下载根目录(本地存储路径, 将按 漫画标题/章节标题/页码 建子目录) */
    private String downloadDir;

    /** 下载线程池大小(同时工作的下载线程数) */
    private int downloadThreadPoolSize;

    /** 每个连接的最大并发下载任务数(全局信号量, 防止打爆源站) */
    private int maxConcurrentDownloads;

    /** 单张图片下载失败重试次数 */
    private int downloadRetryTimes;

    /** 下载请求间隔(毫秒, 用于限流, 避免被封) */
    private long downloadRequestIntervalMs;
}
