import type {
  ExternalComicCrawlStatus,
  ExternalComicPage,
  ExternalComicSource,
  ExternalComicStats,
  ExternalChapterPageResult,
  ExternalImagePageResult,
  ExternalIngestConfig,
  ExternalIngestStatus,
  IkanmhCrawlerStatus,
} from '@/types/externalComic'

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  const json = await response.json()
  if (!response.ok || json.code !== 0) throw new Error(json.message || '请求失败')
  return json.data as T
}

export function fetchExternalComics(
  source: ExternalComicSource,
  page: number,
  pageSize: number,
  keyword: string,
  same: number | null,
  chapterMin?: number,
  chapterMax?: number,
  ingestStatus?: number | null,
) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (keyword.trim()) params.set('keyword', keyword.trim())
  if (same !== null) params.set('same', String(same))
  if (chapterMin !== undefined) params.set('chapterMin', String(chapterMin))
  if (chapterMax !== undefined) params.set('chapterMax', String(chapterMax))
  if (ingestStatus !== null && ingestStatus !== undefined) params.set('ingestStatus', String(ingestStatus))
  return request<ExternalComicPage>(`/api/external-comics/${source}/list?${params.toString()}`)
}

export function fetchExternalComicStats(source: ExternalComicSource) {
  return request<ExternalComicStats>(`/api/external-comics/${source}/stats`)
}

export function fetchExternalChapters(source: ExternalComicSource, externalComicId: number, page = 1, pageSize = 20, orderDir: 'asc' | 'desc' = 'asc') {
  const params = new URLSearchParams({ externalComicId: String(externalComicId), page: String(page), pageSize: String(pageSize), orderDir })
  return request<ExternalChapterPageResult>(`/api/external-comics/${source}/chapters?${params.toString()}`)
}

export function fetchExternalImagePages(source: ExternalComicSource, chapterId: number, page = 1, pageSize = 20) {
  const params = new URLSearchParams({ chapterId: String(chapterId), page: String(page), pageSize: String(pageSize) })
  return request<ExternalImagePageResult>(`/api/external-comics/${source}/pages?${params.toString()}`)
}

export function fetchExternalComicCrawlStatus(source: ExternalComicSource) {
  return request<ExternalComicCrawlStatus>(`/api/external-comics/${source}/crawl-status`)
}

export function startExternalComicCrawl(source: ExternalComicSource) {
  return request<ExternalComicCrawlStatus>(`/api/external-comics/${source}/crawl`, { method: 'POST' })
}

export function fetchExternalIngestStatus(source: ExternalComicSource) {
  return request<ExternalIngestStatus>(`/api/external-comics/${source}/ingest/status`)
}

export function startExternalIngest(source: ExternalComicSource, stage: 'chapters' | 'images', externalComicId?: number) {
  const params = externalComicId ? `?externalComicId=${externalComicId}` : ''
  return request<ExternalIngestStatus>(`/api/external-comics/${source}/ingest/${stage}${params}`, { method: 'POST' })
}

export function stopExternalIngest(source: ExternalComicSource) {
  return request<ExternalIngestStatus>(`/api/external-comics/${source}/ingest/stop`, { method: 'POST' })
}

export function fetchExternalIngestConfig() {
  return request<ExternalIngestConfig>('/api/external-comics/ingest/config')
}

export function updateExternalIngestConfig(chapterWorkers: number, pageWorkers: number, ikanmhChapterWorkers: number) {
  const params = new URLSearchParams({
    chapterWorkers: String(chapterWorkers),
    pageWorkers: String(pageWorkers),
    ikanmhChapterWorkers: String(ikanmhChapterWorkers),
  })
  return request<ExternalIngestConfig>(`/api/external-comics/ingest/config?${params}`, { method: 'POST' })
}

export function fetchIkanmhCrawlerStatus() {
  return request<IkanmhCrawlerStatus>('/api/external-comics/ikanmh/status')
}
