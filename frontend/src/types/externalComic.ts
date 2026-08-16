export type ExternalComicSource = 'niaoniaomh' | 'yuyumh'

export interface ExternalComic {
  id: number
  sourceBookId: string
  name: string
  author?: string | null
  tags?: string | null
  description?: string | null
  coverUrl?: string | null
  sourceUrl: string
  chapterCount?: number | null
  status?: string | null
  sourceUpdateText?: string | null
  isSame: number
  matchedBookId?: number | null
  crawlTime?: string | null
  ingestedChapterCount?: number
  imageDoneChapterCount?: number
  pendingChapterCount?: number
  ingestedImageCount?: number
  ingestStatus?: 0 | 1 | 2 | 3
}

export interface ExternalComicPage {
  list: ExternalComic[]
  total: number
  page: number
  pageSize: number
}

export interface ExternalChapter {
  id: number
  externalComicId: number
  chapterNo: number
  title: string | null
  sourceChapterId: string | null
  chapterUrl: string | null
  imageCount: number | null
  crawlStatus: number | null
  crawlTime: string | null
}

export interface ExternalImagePage {
  id: number
  chapterId: number
  pageNo: number
  imgUrl: string
  fileSize?: number | null
  imgWidth?: number | null
  imgHeight?: number | null
  downloadStatus?: number | null
}

export interface ExternalChapterPageResult {
  list: ExternalChapter[]
  total: number
  page: number
  pageSize: number
}

export interface ExternalImagePageResult {
  list: ExternalImagePage[]
  total: number
  page: number
  pageSize: number
}

export interface ExternalComicStats {
  total: number
  same: number
  different: number
}

export interface ExternalComicCrawlStatus {
  running: boolean
  processed: number
  pages: number
  currentName: string
  message: string
  startedAt?: string | null
  finishedAt?: string | null
}

export interface ExternalIngestStatus {
  running: boolean
  stage: 'chapters' | 'images' | ''
  total: number
  processed: number
  success: number
  failed: number
  currentName: string
  message: string
  startedAt?: string | null
  finishedAt?: string | null
}

export interface ExternalIngestConfig {
  chapterWorkers: number
  pageWorkers: number
  ikanmhChapterWorkers: number
}

export interface IkanmhCrawlerStatus {
  running: boolean
  chapterWorkers: number
  message: string
}
