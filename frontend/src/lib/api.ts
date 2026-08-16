import { MOCK_BOOKS } from '@/data/mockBooks'
import type { Book, SortField, SortOrder } from '@/types/book'
import { getCrawlStatus } from '@/types/book'

export interface BookQuery {
  page?: number
  pageSize?: number
  keyword?: string
  region?: string
  status?: string
  tag?: string
  /** 入库状态筛选(0=未入库, 1=入库中, 2=已入库); 后端按主表 crawl_status 映射 */
  ingestStatus?: number | null
  /** 兼容章节列表的主表爬取状态筛选。 */
  crawlStatus?: number
  /** Chapter count range: lower bound inclusive, upper bound exclusive. */
  chapterMin?: number
  chapterMax?: number
  sortBy?: SortField
  sortOrder?: SortOrder
}

export interface BookListPageResult {
  data: Book[]
  total: number
  page: number
  pageSize: number
}

export interface BookOptions {
  regions: string[]
  statuses: string[]
  sortFields: string[]
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * 前端排序字段 -> 后端数据库列白名单
 * 注意：后端仅支持 score | updateTime | clicks | createdAt
 */
const SORT_FIELD_MAP: Record<SortField, string> = {
  id: 'id',
  clicks: 'clicks',
  score: 'score',
  updatedAt: 'updateTime',
  chapter: 'chapter',
  image: 'image',
  image_done: 'image_done',
  inventory: 'crawlTime',
  chapterCount: 'chapterCount',
}

/**
 * 获取漫画列表（真实后端 /api/book/list）。
 * 后端未就绪时自动降级到本地 mock 数据，保证页面可演示。
 */
export async function fetchBooks(query: BookQuery = {}): Promise<BookListPageResult> {
  const page = query.page ?? 1
  const pageSize = query.pageSize ?? 10
  const sortBy = query.sortBy ?? 'id'
  const sortOrder = query.sortOrder ?? 'desc'

  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('pageSize', String(pageSize))
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.region && query.region !== 'all') params.set('region', query.region)
  if (query.status && query.status !== 'all') params.set('status', query.status)
  if (query.tag?.trim()) params.set('tag', query.tag.trim())
  if (query.ingestStatus !== null && query.ingestStatus !== undefined) {
    params.set('ingestStatus', String(query.ingestStatus))
  }
  if (query.chapterMin !== undefined) params.set('chapterMin', String(query.chapterMin))
  if (query.chapterMax !== undefined) params.set('chapterMax', String(query.chapterMax))
  params.set('sortField', SORT_FIELD_MAP[sortBy] ?? 'createdAt')
  params.set('sortDir', sortOrder)

  try {
    const resp = await fetch(`/api/book/list?${params.toString()}`)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code !== 0) throw new Error(json.message || '业务错误')
    const pg = json.data as {
      list: Book[]
      total: number
      page: number
      pageSize: number
    }
    return { data: pg.list, total: pg.total, page: pg.page, pageSize: pg.pageSize }
  } catch (e) {
    console.warn('[fetchBooks] 后端不可用，降级使用 mock 数据:', e)
    return fallbackMock(query, page, pageSize)
  }
}

/** 后端不可用时的本地 mock 兜底 */
function fallbackMock(query: BookQuery, page: number, pageSize: number): BookListPageResult {
  const keyword = (query.keyword ?? '').trim().toLowerCase()
  const sortBy = query.sortBy ?? 'id'
  const sortOrder = query.sortOrder ?? 'desc'

  let list = [...MOCK_BOOKS]
  if (keyword) {
    list = list.filter(
      (b) =>
        b.name.toLowerCase().includes(keyword) ||
        (b.alias ?? '').toLowerCase().includes(keyword) ||
        (b.author ?? '').toLowerCase().includes(keyword) ||
        b.sourceBookId.toLowerCase().includes(keyword),
    )
  }
  if (query.region && query.region !== 'all') {
    list = list.filter((b) => b.region === query.region)
  }
  if (query.status && query.status !== 'all') {
    list = list.filter((b) => b.status === query.status)
  }
  if (query.ingestStatus !== null && query.ingestStatus !== undefined) {
    list = list.filter((b) => getCrawlStatus(b.crawlStatus).value === query.ingestStatus)
  }
  if (query.chapterMin !== undefined) {
    list = list.filter((b) => (b.chapterCount ?? 0) >= query.chapterMin!)
  }
  if (query.chapterMax !== undefined) {
    list = list.filter((b) => (b.chapterCount ?? 0) < query.chapterMax!)
  }
  list.sort((a, b) => {
    let cmp = 0
    switch (sortBy) {
      case 'clicks':
        cmp = (a.clicks ?? 0) - (b.clicks ?? 0)
        break
      case 'score':
        cmp = Number(a.score ?? 0) - Number(b.score ?? 0)
        break
      case 'updatedAt':
        cmp = new Date(a.updatedAt ?? 0).getTime() - new Date(b.updatedAt ?? 0).getTime()
        break
      default:
        cmp = a.id - b.id
    }
    return sortOrder === 'asc' ? cmp : -cmp
  })

  const total = list.length
  const start = (page - 1) * pageSize
  return { data: list.slice(start, start + pageSize), total, page, pageSize }
}

/** 获取筛选维度枚举（地区 / 状态 / 排序字段） */
export async function fetchBookOptions(): Promise<BookOptions> {
  try {
    const resp = await fetch('/api/book/options')
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code === 0 && json.data) return json.data as BookOptions
    throw new Error('options 格式异常')
  } catch {
    return {
      regions: ['日本', '韩国', '国产', '欧美', '其他'],
      statuses: ['连载中', '已完结'],
      sortFields: ['score', 'updateTime', 'clicks', 'createdAt', 'chapter', 'image'],
    }
  }
}

export interface CrawlStatus {
  bookCount: number
  chapterCount: number
  bookPageCount: number
  detail: {
    running: boolean
    total: number
    notCrawled: number
    chapterDone: number
    failed: number
    done: number
    progress: number
    lastCrawlTime: string | null
  }
  image: {
    running: boolean
    pending: number
    done: number
    failed: number
    totalCh: number
    processed: number
    progress: number       // 章节进度(%)
    successRate: number
    bookPageCount: number   // 已入库图片数(实时更新)
    imgProgress: number     // 图片进度(%)
  }
}

/** 实时爬取进度（仪表盘/进度页轮询）
 *  注意: /api/crawl/* 接口返回的是裸对象(无 {code,data} 包装), 直接取 body */
export async function fetchCrawlStatus(): Promise<CrawlStatus> {
  const resp = await fetch('/api/crawl/status')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  return (await resp.json()) as CrawlStatus
}

/** 停止全部爬虫（详情/列表/图片，当前批次结束后停止） */
export async function stopCrawl(): Promise<string> {
  const resp = await fetch('/api/crawl/stop')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  return json.message || '已发送停止指令'
}

/** 启动图片爬虫（全量断点续爬，后台异步执行，立即返回） */
export async function startImageCrawl(): Promise<string> {
  const resp = await fetch('/api/crawl/image/start')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  return json.message || '已提交'
}

/** 触发详情爬虫（后台异步执行，立即返回） */
export async function startDetailCrawl(limit?: number): Promise<string> {
  const params = limit && limit > 0 ? `?limit=${limit}` : ''
  const resp = await fetch(`/api/crawl/detail${params}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  return json.message || '已提交'
}

/** 单本漫画图片入库（仅爬该漫画未爬章节，后台异步执行，立即返回） */
export async function crawlImageBook(bookId: number): Promise<string> {
  const resp = await fetch(`/api/crawl/image/book?bookId=${bookId}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code && json.code !== 0) {
    throw new Error(json.message || '提交失败')
  }
  return json.message || '已提交'
}

/** 单本漫画图片入库实时进度 */
export interface ImageProgress {
  bookId: number
  running: boolean
  total: number
  processed: number
  success: number
  fail: number
  currentChapter: string
  currentImageCount: number
  progress: number
}
export async function fetchImageProgress(bookId: number): Promise<ImageProgress> {
  const resp = await fetch(`/api/crawl/image/progress?bookId=${bookId}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  return resp.json()
}

export interface CrawlLogEntry {
  ts: number
  level: string
  msg: string
}
/** 单本漫画图片入库的控制台风格日志 */
export async function fetchImageLog(bookId: number): Promise<{ running: boolean; logs: CrawlLogEntry[] }> {
  const resp = await fetch(`/api/crawl/image/log?bookId=${bookId}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  return { running: json.running, logs: json.logs || [] }
}

// ===================== 一键入库与入库日志 =====================

export interface IngestPreview {
  bookCount: number
  chapterCount: number
  running: boolean
}

export interface IngestBatchStatus {
  running: boolean
  batchNo: string | null
  totalBooks: number
  totalChapters: number
  completedBooks: number
  successBooks: number
  failedBooks: number
  currentBookId: number | null
  currentBookName: string | null
  lastCompletedLogId: number | null
  lastCompletedBookName: string | null
  lastDurationSeconds: number | null
  cancelRequested: boolean
  cancelled: boolean
  message: string
}

export interface IngestLog {
  id: number
  batchNo: string
  bookId: number
  bookName: string | null
  status: number
  crawlStatusBefore: number | null
  crawlStatusAfter: number | null
  totalChapterCount: number
  pendingChapterCount: number
  successChapterCount: number
  failedChapterCount: number
  imageCount: number
  startTime: string
  endTime: string | null
  durationSeconds: number | null
  errorMessage: string | null
  createdAt: string
}

export interface IngestLogPage {
  list: IngestLog[]
  total: number
  page: number
  pageSize: number
}

function batchIngestParams(sortBy: SortField, sortOrder: SortOrder) {
  const params = new URLSearchParams()
  params.set('sortField', SORT_FIELD_MAP[sortBy] ?? 'crawlTime')
  params.set('sortDir', sortOrder)
  return params
}

export async function fetchIngestPreview(sortBy: SortField, sortOrder: SortOrder): Promise<IngestPreview> {
  const resp = await fetch(`/api/ingest/preview?${batchIngestParams(sortBy, sortOrder).toString()}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || '获取入库预览失败')
  return json.data as IngestPreview
}

export async function startBatchIngest(sortBy: SortField, sortOrder: SortOrder): Promise<IngestBatchStatus> {
  const resp = await fetch(`/api/ingest/start?${batchIngestParams(sortBy, sortOrder).toString()}`, { method: 'POST' })
  const json = await resp.json()
  if (!resp.ok || json.code !== 0) throw new Error(json.message || '提交一键入库失败')
  return json.data as IngestBatchStatus
}

export async function cancelBatchIngest(): Promise<IngestBatchStatus> {
  const resp = await fetch('/api/ingest/cancel', { method: 'POST' })
  const json = await resp.json()
  if (!resp.ok || json.code !== 0) throw new Error(json.message || '取消一键入库失败')
  return json.data as IngestBatchStatus
}

export async function fetchBatchIngestStatus(): Promise<IngestBatchStatus> {
  const resp = await fetch('/api/ingest/status')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || '获取一键入库状态失败')
  return json.data as IngestBatchStatus
}

export async function fetchIngestLogs(page = 1, pageSize = 20, batchNo?: string): Promise<IngestLogPage> {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (batchNo) params.set('batchNo', batchNo)
  const resp = await fetch(`/api/ingest/logs?${params.toString()}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || '获取入库日志失败')
  return json.data as IngestLogPage
}

/** 统计概览（仪表盘用）
 *  真实数据来自 /api/book/stats:
 *  { bookCount, chapterCount, totalImageCount, downloadedImageCount, doneCount, failedCount }
 *  后端不可用时自动降级到本地 mock 数据，保证页面可演示。
 */
export interface Overview {
  bookCount: number
  chapterCount: number
  totalImageCount: number
  downloadedImageCount: number
  doneCount: number
  failedCount: number
}

export async function fetchOverview(): Promise<Overview> {
  try {
    const resp = await fetch('/api/book/stats')
    if (resp.ok) {
      const json = await resp.json()
      if (json.code === 0 && json.data) {
        const d = json.data
        return {
          bookCount: d.bookCount ?? 0,
          chapterCount: d.chapterCount ?? 0,
          totalImageCount: d.totalImageCount ?? 0,
          downloadedImageCount: d.downloadedImageCount ?? 0,
          doneCount: d.doneCount ?? 0,
          failedCount: d.failedCount ?? 0,
        }
      }
    }
  } catch {
    /* ignore，降级到 mock */
  }
  await sleep(200)
  const done = MOCK_BOOKS.filter((b) => b.crawlStatus === 3).length
  const failed = MOCK_BOOKS.filter((b) => b.crawlStatus === -1).length
  const chapterCount = MOCK_BOOKS.length * 42
  const totalImg = chapterCount * 11
  const downloaded = Math.floor(totalImg * 0.7)
  return {
    bookCount: MOCK_BOOKS.length,
    chapterCount,
    totalImageCount: totalImg,
    downloadedImageCount: downloaded,
    doneCount: done,
    failedCount: failed,
  }
}

export interface Chapter {
  id: number
  bookId: number
  chapterNo: number
  title: string | null
  sourceChapterId: string | null
  chapterUrl: string | null
  imageCount: number | null
  crawlStatus: number | null
  crawlTime: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ChapterPageResult {
  list: Chapter[]
  total: number
  page: number
  pageSize: number
}

/** 分页查询某漫画的章节（真实后端 /api/chapter/list） */
export async function fetchChapters(
  bookId: number,
  page = 1,
  pageSize = 20,
  orderDir: 'asc' | 'desc' = 'asc',
): Promise<ChapterPageResult> {
  const params = new URLSearchParams()
  params.set('bookId', String(bookId))
  params.set('page', String(page))
  params.set('pageSize', String(pageSize))
  params.set('orderDir', orderDir)
  const resp = await fetch(`/api/chapter/list?${params.toString()}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || '业务错误')
  const data = json.data as ChapterPageResult
  return { list: data.list ?? [], total: data.total ?? 0, page: data.page ?? 1, pageSize: data.pageSize ?? pageSize }
}

// ===================== 漫画阅读(章节图片) =====================

/** 章节内单张图片 */
export interface BookPage {
  id: number
  chapterId: number
  /** 图片序号(从1开始, 阅读顺序) */
  pageNo: number
  imgUrl: string
  fileSize?: number | null
  imgWidth?: number | null
  imgHeight?: number | null
  downloadStatus?: number | null
}

export interface BookPageResult {
  list: BookPage[]
  total: number
  page: number
  pageSize: number
}

/**
 * 分页查询某章节的图片(按 page_no 升序, 保证阅读顺序)
 * @param chapterId 章节ID
 * @param page 页码(从1开始)
 * @param pageSize 每页图片数(默认10)
 */
export async function fetchBookPages(
  chapterId: number,
  page = 1,
  pageSize = 10,
): Promise<BookPageResult> {
  const params = new URLSearchParams()
  params.set('chapterId', String(chapterId))
  params.set('page', String(page))
  params.set('pageSize', String(pageSize))
  const resp = await fetch(`/api/page/list?${params.toString()}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 0) throw new Error(json.message || '业务错误')
  const data = json.data as BookPageResult
  return { list: data.list ?? [], total: data.total ?? 0, page: data.page ?? 1, pageSize: data.pageSize ?? pageSize }
}

// ===================== 入库盘点 =====================

/** 盘点批次记录 */
export interface InventoryRecord {
  id: number
  batchNo: string
  startTime: string | null
  endTime: string | null
  status: number // 1-进行中 2-已完成 3-失败
  scannedBookCount: number
  doneBookCount: number
  updatedChapterCount: number
  updatedBookCount: number
  remark: string | null
  createdAt: string
}

/** 盘点批次内的漫画明细 */
export interface InventoryRecordBook {
  id: number
  recordId: number
  bookId: number
  sourceBookId: string | null
  bookName: string | null
  chapterCount: number
  imageCount: number
  createdAt: string
}

/** 触发一次入库盘点（同步执行，百万级数据为聚合SQL，通常数秒完成） */
export async function runInventory(): Promise<InventoryRecord> {
  const resp = await fetch('/api/inventory/run', { method: 'POST' })
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 200) throw new Error(json.message || '盘点失败')
  return json.data as InventoryRecord
}

/** 盘点记录列表（按时间倒序） */
export async function fetchInventoryRecords(): Promise<InventoryRecord[]> {
  const resp = await fetch('/api/inventory/records')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 200) throw new Error(json.message || '查询失败')
  return (json.data as InventoryRecord[]) ?? []
}

/** 某批次的盘点漫画明细 */
export async function fetchInventoryRecordBooks(
  recordId: number,
): Promise<InventoryRecordBook[]> {
  const resp = await fetch(`/api/inventory/records/${recordId}/books`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  if (json.code !== 200) throw new Error(json.message || '查询失败')
  return (json.data as InventoryRecordBook[]) ?? []
}
