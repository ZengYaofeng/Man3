import type { SiteSource, SiteSourcePayload, DedupResult } from '@/types/siteSource'

export interface SitePageResult {
  data: SiteSource[]
  total: number
  page: number
  pageSize: number
  /** true 表示后端不可达, 当前展示的是前端 mock 兜底数据 */
  mock?: boolean
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * 站点来源 CRUD，对接后端 /api/site/* 接口。
 * 后端不可用时自动降级到本地 mock，保证页面可演示。
 */

export async function fetchSites(
  keyword = '',
  page = 1,
  pageSize = 10,
): Promise<SitePageResult> {
  try {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('pageSize', String(pageSize))
    if (keyword.trim()) params.set('keyword', keyword.trim())
    const resp = await fetch(`/api/site/list?${params.toString()}`)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code !== 0) throw new Error(json.message || '业务错误')
    const pg = json.data as {
      list: SiteSource[]
      total: number
      page: number
      pageSize: number
    }
    const data = pg.list.map((s) => ({ ...s, isEnabled: s.enabled === 1 }))
    return { data, total: pg.total, page: pg.page, pageSize: pg.pageSize, mock: false }
  } catch (e) {
    console.warn('[fetchSites] 后端不可用，降级 mock:', e)
    const r = await fallbackList(keyword, page, pageSize)
    return { ...r, mock: true }
  }
}

export async function fetchAllSites(): Promise<SiteSource[]> {
  try {
    const resp = await fetch('/api/site/all')
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code === 0 && Array.isArray(json.data))
      return (json.data as SiteSource[]).map((s) => ({ ...s, isEnabled: s.enabled === 1 }))
    throw new Error('格式异常')
  } catch (e) {
    console.warn('[fetchAllSites] 后端不可用，降级 mock:', e)
    const { MOCK_SITES } = await import('@/data/mockSites')
    return MOCK_SITES
  }
}

export async function saveSite(payload: SiteSourcePayload & { id?: number }): Promise<SiteSource> {
  try {
    const resp = await fetch('/api/site/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    const json = await resp.json()
    if (json.code !== 0) throw new Error(json.message || '保存失败')
    return json.data as SiteSource
  } catch (e) {
    console.warn('[saveSite] 后端不可用，降级 mock:', e)
    await sleep(300)
    if (payload.id) {
      return { ...(payload as SiteSource), id: payload.id, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    }
    const id = Math.floor(Math.random() * 100000)
    return { ...(payload as SiteSource), id, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  }
}

export async function deleteSite(id: number): Promise<void> {
  try {
    const resp = await fetch(`/api/site/${id}`, { method: 'DELETE' })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code !== 0) throw new Error(json.message || '删除失败')
  } catch (e) {
    console.warn('[deleteSite] 后端不可用，降级 mock:', e)
    await sleep(250)
  }
}

export async function setSiteEnabled(id: number, enabled: boolean): Promise<void> {
  try {
    const resp = await fetch(`/api/site/${id}/enabled?enabled=${enabled}`, { method: 'POST' })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const json = await resp.json()
    if (json.code !== 0) throw new Error(json.message || '操作失败')
  } catch (e) {
    console.warn('[setSiteEnabled] 后端不可用，降级 mock:', e)
    await sleep(200)
  }
}

/** 全站查重（占位实现，功能暂不开放） */
export async function runDedup(_siteId: number): Promise<DedupResult[]> {
  await sleep(400)
  return []
}

/* ---------------- mock 兜底 ---------------- */
async function fallbackList(
  keyword: string,
  page: number,
  pageSize: number,
): Promise<SitePageResult> {
  const { MOCK_SITES } = await import('@/data/mockSites')
  const kw = keyword.trim().toLowerCase()
  let list = MOCK_SITES
  if (kw) {
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(kw) ||
        s.url.toLowerCase().includes(kw) ||
        (s.remark ?? '').toLowerCase().includes(kw),
    )
  }
  const total = list.length
  const start = (page - 1) * pageSize
  return { data: list.slice(start, start + pageSize), total, page, pageSize }
}
