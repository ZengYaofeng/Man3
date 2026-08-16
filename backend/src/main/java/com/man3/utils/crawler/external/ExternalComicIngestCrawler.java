package com.man3.utils.crawler.external;

import com.man3.service.ExternalComicIngestService;

/** Source-specific parsing contract. Common scheduling is owned by ExternalComicIngestService. */
public interface ExternalComicIngestCrawler {
    String source();
    void crawlChapters(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception;
    void crawlPages(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception;
}
