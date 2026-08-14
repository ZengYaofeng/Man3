// 漫画主表 (book) 字段定义，与后端 Book.java / init_db.sql 保持一致

export interface Book {
  /** 主键ID */
  id: number
  /** 来源站点漫画ID(如1224), 用于去重 */
  sourceBookId: string
  /** 漫画名称 */
  name: string
  /** 漫画别名 */
  alias?: string
  /** 作者(多个用&或,分隔) */
  author?: string
  /** 连载状态(连载中/已完结) */
  status?: string
  /** 地区/国家 */
  region?: string
  /** 标签(多个用,分隔) */
  tags?: string
  /** 漫画简介 */
  description?: string
  /** 封面图片链接 */
  coverUrl?: string
  /** 站点显示的更新时间 */
  updateTime?: string
  /** 点击量 */
  clicks?: number
  /** 评分 */
  score?: number
  /** 漫画主页URL */
  sourceUrl?: string
  /** 爬取状态: 0-未爬取 1-章节已爬取 2-图片已爬取 3-全部完成 -1-失败 */
  crawlStatus: number
  /** 最近爬取时间 */
  crawlTime?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

// 爬取状态枚举（与后端 crawlStatus 字段对应）
export const CRAWL_STATUS_OPTIONS = [
  { value: -1, label: '失败', className: 'bg-red-100 text-red-700' },
  { value: 0, label: '未爬取', className: 'bg-slate-100 text-slate-600' },
  { value: 1, label: '章节已爬取', className: 'bg-blue-100 text-blue-700' },
  { value: 2, label: '图片已爬取', className: 'bg-indigo-100 text-indigo-700' },
  { value: 3, label: '全部完成', className: 'bg-emerald-100 text-emerald-700' },
] as const

export function getCrawlStatus(value: number) {
  return (
    CRAWL_STATUS_OPTIONS.find((o) => o.value === value) ?? {
      value,
      label: '未知',
      className: 'bg-slate-100 text-slate-600',
    }
  )
}

// 连载状态选项
export const SERIAL_STATUS_OPTIONS = ['连载中', '已完结']

export type SortField = 'id' | 'clicks' | 'score' | 'updatedAt'
export type SortOrder = 'asc' | 'desc'

export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`
}
