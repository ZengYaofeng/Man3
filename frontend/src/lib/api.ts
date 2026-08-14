import { MOCK_BOOKS } from '@/data/mockBooks'
import type { Book, SortField, SortOrder } from '@/types/book'

export interface BookQuery {
  page?: number
  pageSize?: number
  keyword?: string
  region?: string
  status?: string
  tag?: string
  /** 前端本地过滤（后端 BookQueryDTO 暂未支持该参数） */
  crawlStatus?: number | null
  sortBy?: SortField
  sortOrder?: SortOrder
}

export interface BookPageResult {
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
  id: 'createdAt',
  clicks: 'clicks',
  score: 'score',
  updatedAt: 'updateTime',
}

/**
 * 获取漫画列表（真实后端 /api/book/list）。
 * 后端未就绪时自动降级到本地 mock 数据，保证页面可演示。
 */
export async function fetchBooks(query: BookQuery = {}): Promise<BookPageResult> {
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
    // 后端 BookQueryDTO 暂不支持 crawlStatus 过滤，这里在前端对当前页结果做二次过滤
    const list =
      query.crawlStatus !== null && query.crawlStatus !== undefined
        ? pg.list.filter((b) => b.crawlStatus === query.crawlStatus)
        : pg.list
    return { data: list, total: pg.total, page: pg.page, pageSize: pg.pageSize }
  } catch (e) {
    console.warn('[fetchBooks] 后端不可用，降级使用 mock 数据:', e)
    return fallbackMock(query, page, pageSize)
  }
}

/** 后端不可用时的本地 mock 兜底 */
function fallbackMock(query: BookQuery, page: number, pageSize: number): BookPageResult {
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
  if (query.crawlStatus !== null && query.crawlStatus !== undefined) {
    list = list.filter((b) => b.crawlStatus === query.crawlStatus)
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
      sortFields: ['score', 'updateTime', 'clicks', 'createdAt'],
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
}

/** 实时爬取进度（仪表盘/进度页轮询）
 *  注意: /api/crawl/* 接口返回的是裸对象(无 {code,data} 包装), 直接取 body */
export async function fetchCrawlStatus(): Promise<CrawlStatus> {
  const resp = await fetch('/api/crawl/status')
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  return (await resp.json()) as CrawlStatus
}

/** 触发详情爬虫（后台异步执行，立即返回） */
export async function startDetailCrawl(limit?: number): Promise<string> {
  const params = limit && limit > 0 ? `?limit=${limit}` : ''
  const resp = await fetch(`/api/crawl/detail${params}`)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const json = await resp.json()
  return json.message || '已提交'
}

/** 统计概览（仪表盘用） */
export async function fetchOverview() {
  try {
    const resp = await fetch('/api/book/list?page=1&pageSize=1')
    if (resp.ok) {
      const json = await resp.json()
      return {
        bookCount: json.data?.total ?? 0,
        chapterCount: 0,
        bookPageCount: 0,
        doneCount: 0,
        failedCount: 0,
      }
    }
  } catch {
    /* ignore，降级到 mock */
  }
  await sleep(200)
  const done = MOCK_BOOKS.filter((b) => b.crawlStatus === 3).length
  const failed = MOCK_BOOKS.filter((b) => b.crawlStatus === -1).length
  return {
    bookCount: MOCK_BOOKS.length,
    chapterCount: MOCK_BOOKS.length * 42,
    bookPageCount: MOCK_BOOKS.length * 42 * 11,
    doneCount: done,
    failedCount: failed,
  }
}
