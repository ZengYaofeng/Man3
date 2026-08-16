import { useEffect, useState, useCallback, useRef, Fragment } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Search,
  RefreshCw,
  ArrowUp,
  ArrowDown,
  ChevronsUpDown,
  Eye,
  ChevronDown,
  ChevronRight,
  BookOpen,
  Image as ImageIcon,
  Terminal,
  Loader2,
  ArrowDownWideNarrow,
  ArrowUpWideNarrow,
  DatabaseZap,
} from 'lucide-react'
import CoverImage from '@/components/comic/CoverImage'
import ComicDetailDrawer from '@/components/comic/ComicDetailDrawer'
import {
  fetchBooks,
  fetchBookOptions,
  fetchChapters,
  crawlImageBook,
  fetchImageProgress,
  fetchIngestPreview,
  startBatchIngest,
  cancelBatchIngest,
  fetchBatchIngestStatus,
  fetchIngestLogs,
  type BookOptions,
  type Chapter,
  type IngestPreview,
  type IngestBatchStatus,
  type IngestLog,
} from '@/lib/api'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  type Book,
  type SortField,
  type SortOrder,
  CRAWL_STATUS_OPTIONS,
  SERIAL_STATUS_OPTIONS,
  getCrawlStatus,
  formatDateTime,
} from '@/types/book'

const PAGE_SIZES = [10, 20, 50]
const CHAPTER_PAGE_SIZE = 20

interface ExpandState {
  page: number
  total: number
  list: Chapter[]
  loading: boolean
  loaded: boolean
  orderDir: 'asc' | 'desc'
}

function SortHeader({
  field,
  label,
  active,
  order,
  onSort,
  className,
}: {
  field: SortField
  label: string
  active: boolean
  order: SortOrder
  onSort: (f: SortField) => void
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={() => onSort(field)}
      className={`inline-flex items-center gap-1 hover:text-slate-900 ${className ?? ''}`}
    >
      {label}
      {active ? (
        order === 'asc' ? (
          <ArrowUp className="size-3.5" />
        ) : (
          <ArrowDown className="size-3.5" />
        )
      ) : (
        <ChevronsUpDown className="size-3.5 opacity-40" />
      )}
    </button>
  )
}

export default function ComicList() {
  const [keyword, setKeyword] = useState('')
  const [region, setRegion] = useState('all')
  const [status, setStatus] = useState('all')
  const [ingestStatus, setIngestStatus] = useState('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [jumpValue, setJumpValue] = useState('')
  const [sortBy, setSortBy] = useState<SortField>('inventory')
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc')
  const [loading, setLoading] = useState(false)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [batchSubmitting, setBatchSubmitting] = useState(false)
  const [cancellingBatch, setCancellingBatch] = useState(false)
  const [ingestPreview, setIngestPreview] = useState<IngestPreview | null>(null)
  const [batchStatus, setBatchStatus] = useState<IngestBatchStatus | null>(null)
  const [completionNotice, setCompletionNotice] = useState<IngestLog | null>(null)
  const [actionMsg, setActionMsg] = useState<string | null>(null)
  const seenBatchLogIds = useRef<Set<number>>(new Set())
  const [data, setData] = useState<Book[]>([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState<Book | null>(null)
  // 展开行的 bookId -> 章节分页状态
  const [expanded, setExpanded] = useState<Record<number, ExpandState>>({})
  // 正在单本入库的漫画: bookId -> 进度快照(实时来自后端 /api/crawl/image/progress)
  const [ingesting, setIngesting] = useState<Record<
    number,
    {
      startedAt: number
      total: number
      processed: number
      success: number
      fail: number
      currentChapter: string
      progress: number
    }
  >>({})
  const [options, setOptions] = useState<BookOptions>({
    regions: [],
    statuses: ['连载中', '已完结'],
    sortFields: ['score', 'updateTime', 'clicks', 'createdAt'],
  })

  useEffect(() => {
    fetchBookOptions().then(setOptions)
  }, [])

  useEffect(() => {
    fetchBatchIngestStatus().then(setBatchStatus).catch(() => undefined)
  }, [])

  const refreshBooks = useCallback(async () => {
    const res = await fetchBooks({
      page,
      pageSize,
      keyword,
      region: region === 'all' ? undefined : region,
      status: status === 'all' ? undefined : status,
      ingestStatus: ingestStatus === 'all' ? null : Number(ingestStatus),
      sortBy,
      sortOrder,
    })
    setData(res.data)
    setTotal(res.total)
  }, [page, pageSize, keyword, region, status, ingestStatus, sortBy, sortOrder])

  useEffect(() => {
    let cancelled = false
    const t = setTimeout(async () => {
      setLoading(true)
      const res = await fetchBooks({
        page,
        pageSize,
        keyword,
        region: region === 'all' ? undefined : region,
        status: status === 'all' ? undefined : status,
        ingestStatus: ingestStatus === 'all' ? null : Number(ingestStatus),
        sortBy,
        sortOrder,
      })
      if (!cancelled) {
        setData(res.data)
        setTotal(res.total)
        setLoading(false)
      }
    }, 250)
    return () => {
      clearTimeout(t)
      cancelled = true
    }
  }, [keyword, region, status, ingestStatus, page, pageSize, sortBy, sortOrder])

  // 单本入库轮询: 有漫画正在入库时, 每 1.5s 拉取实时进度(/api/crawl/image/progress)
  useEffect(() => {
    const ids = Object.keys(ingesting)
    if (ids.length === 0) return
    const timer = setInterval(async () => {
      const bookIds = Object.keys(ingesting).map(Number)
      let allDone = true
      for (const id of bookIds) {
        try {
          const p = await fetchImageProgress(id)
          setIngesting((prev) => {
            const cur = prev[id]
            if (!cur) return prev
            return {
              ...prev,
              [id]: {
                ...cur,
                total: p.total,
                processed: p.processed,
                success: p.success,
                fail: p.fail,
                currentChapter: p.currentChapter,
                progress: p.progress,
              },
            }
          })
          // 仍在进行中(或尚未开始但 running)则记为未完成
          if (p.running || p.processed < p.total) {
            allDone = false
          }
        } catch {
          /* 忽略单本查询错误 */
        }
      }
      // 若全部完成, 刷新一次列表并清空轮询集合
      if (allDone) {
        setIngesting({})
        refreshBooks()
      }
    }, 1500)
    return () => clearInterval(timer)
  }, [ingesting, refreshBooks])

  const openBatchPreview = async () => {
    setPreviewLoading(true)
    try {
      const preview = await fetchIngestPreview(sortBy, sortOrder)
      setIngestPreview(preview)
      setPreviewOpen(true)
    } catch (e) {
      setActionMsg(`获取一键入库预览失败：${(e as Error).message}`)
    } finally {
      setPreviewLoading(false)
    }
  }

  const confirmBatchIngest = async () => {
    setBatchSubmitting(true)
    try {
      const status = await startBatchIngest(sortBy, sortOrder)
      setBatchStatus(status)
      seenBatchLogIds.current = new Set()
      setPreviewOpen(false)
      if (!status.running) {
        setActionMsg(status.message)
      }
    } catch (e) {
      setActionMsg(`一键入库提交失败：${(e as Error).message}`)
    } finally {
      setBatchSubmitting(false)
    }
  }

  const cancelCurrentBatch = async () => {
    setCancellingBatch(true)
    try {
      const status = await cancelBatchIngest()
      setBatchStatus(status)
      setActionMsg('已取消一键入库，正在停止当前漫画的处理。')
    } catch (e) {
      setActionMsg(`取消一键入库失败：${(e as Error).message}`)
    } finally {
      setCancellingBatch(false)
    }
  }

  useEffect(() => {
    if (!batchStatus?.batchNo) return
    let cancelled = false
    const poll = async () => {
      try {
        const status = await fetchBatchIngestStatus()
        if (cancelled) return
        setBatchStatus(status)
        const page = await fetchIngestLogs(1, 100, status.batchNo ?? undefined)
        if (cancelled) return
        const completed = page.list
          .filter((log) => log.status === 2 && !seenBatchLogIds.current.has(log.id))
          .sort((a, b) => a.id - b.id)
        if (completed.length > 0) {
          completed.forEach((log) => seenBatchLogIds.current.add(log.id))
          // Completed books can arrive faster than a toast can be read. Keep only the
          // most recent event so the final notification can disappear promptly.
          setCompletionNotice(completed[completed.length - 1])
        }
        if (!status.running) {
          refreshBooks()
          setActionMsg(status.message)
        }
      } catch {
        // 保留当前界面状态，下一轮继续重试。
      }
    }
    poll()
    const timer = window.setInterval(poll, 1200)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [batchStatus?.batchNo, refreshBooks])

  useEffect(() => {
    if (!completionNotice) return
    const timer = window.setTimeout(() => setCompletionNotice(null), 4200)
    return () => window.clearTimeout(timer)
  }, [completionNotice])

  // ===== 行展开 / 章节加载 =====
  const loadChapters = useCallback(async (bookId: number, chapterPage: number, orderDir: 'asc' | 'desc') => {
    setExpanded((prev) => ({
      ...prev,
      [bookId]: { ...prev[bookId], loading: true, page: chapterPage, orderDir },
    }))
    try {
      const res = await fetchChapters(bookId, chapterPage, CHAPTER_PAGE_SIZE, orderDir)
      setExpanded((prev) => ({
        ...prev,
        [bookId]: {
          page: res.page,
          total: res.total,
          list: res.list,
          loading: false,
          loaded: true,
          orderDir,
        },
      }))
    } catch {
      setExpanded((prev) => ({ ...prev, [bookId]: { ...prev[bookId], loading: false } }))
    }
  }, [])

  const toggleRow = (bookId: number) => {
    setExpanded((prev) => {
      if (prev[bookId]) {
        const n = { ...prev }
        delete n[bookId]
        return n
      }
      return prev
    })
    if (!expanded[bookId]?.loaded) {
      loadChapters(bookId, 1, 'asc')
    }
  }

  const toggleChapterOrder = (bookId: number) => {
    const cur = expanded[bookId]
    if (!cur) return
    const nextDir: 'asc' | 'desc' = cur.orderDir === 'asc' ? 'desc' : 'asc'
    loadChapters(bookId, 1, nextDir)
  }

  // 单本入库: 触发图片爬虫仅爬该漫画未爬章节, 并加入轮询集合实时刷新进度
  const handleIngest = async (b: Book) => {
    if (ingesting[b.id]) return
    try {
      await crawlImageBook(b.id)
      setIngesting((prev) => ({
        ...prev,
        [b.id]: {
          startedAt: Date.now(),
          total: b.chapterCount ?? 0,
          processed: 0,
          success: 0,
          fail: 0,
          currentChapter: '',
          progress: 0,
        },
      }))
      // 立即拉一次进度(确认爬虫已启动)
      try {
        const p = await fetchImageProgress(b.id)
        setIngesting((prev) => ({
          ...prev,
          [b.id]: {
            startedAt: Date.now(),
            total: p.total,
            processed: p.processed,
            success: p.success,
            fail: p.fail,
            currentChapter: p.currentChapter,
            progress: p.progress,
          },
        }))
      } catch {
        /* 忽略 */
      }
    } catch (e) {
      setActionMsg(`入库提交失败：${(e as Error).message}`)
    }
  }

  const handleSort = (f: SortField) => {
    if (sortBy === f) {
      setSortOrder((o) => (o === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortBy(f)
      setSortOrder('desc')
    }
  }

  // 跳页：将输入框的数值限制在合法范围内
  const handleJump = () => {
    const n = parseInt(jumpValue, 10)
    if (!Number.isNaN(n)) {
      const target = Math.min(Math.max(1, n), totalPages)
      setPage(target)
    }
    setJumpValue('')
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const pageNums: number[] = []
  const start = Math.max(1, page - 2)
  const end = Math.min(totalPages, start + 4)
  for (let i = start; i <= end; i++) pageNums.push(i)

  return (
    <div className="space-y-4">
      {/* 工具栏 */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-sm">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="搜索名称 / 别名 / 作者 / 来源ID"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(1)
            }}
            className="pl-9"
          />
        </div>
        <Select
          value={region}
          onValueChange={(v) => {
            setRegion(v)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[130px]">
            <SelectValue placeholder="地区" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部地区</SelectItem>
            {options.regions.map((r) => (
              <SelectItem key={r} value={r}>
                {r}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={status}
          onValueChange={(v) => {
            setStatus(v)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[140px]">
            <SelectValue placeholder="连载状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部状态</SelectItem>
            {SERIAL_STATUS_OPTIONS.map((s) => (
              <SelectItem key={s} value={s}>
                {s}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={ingestStatus}
          onValueChange={(v) => {
            setIngestStatus(v)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="入库状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部入库状态</SelectItem>
            {CRAWL_STATUS_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={String(o.value)}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          onClick={() => {
            setKeyword('')
            setRegion('all')
            setStatus('all')
            setIngestStatus('all')
            setPage(1)
          }}
        >
          重置
        </Button>

        <Button
          variant="default"
          disabled={previewLoading || batchStatus?.running}
          onClick={openBatchPreview}
          className="bg-emerald-600 hover:bg-emerald-700"
        >
          {previewLoading || batchStatus?.running ? (
            <Loader2 className="mr-1 size-4 animate-spin" />
          ) : (
            <DatabaseZap className="mr-1 size-4" />
          )}
          {batchStatus?.running
            ? `一键入库 ${batchStatus.completedBooks}/${batchStatus.totalBooks}`
            : '一键入库'}
        </Button>
        {batchStatus?.running && batchStatus.currentBookName && (
          <span className="max-w-[300px] truncate text-sm text-slate-500" title={batchStatus.currentBookName}>
            正在入库：{batchStatus.currentBookName}
          </span>
        )}
        {actionMsg && (
          <span className="max-w-[360px] truncate text-sm text-rose-600" title={actionMsg}>
            {actionMsg}
          </span>
        )}
        <Button
          variant="outline"
          size="icon"
          onClick={() => setPage((p) => p)}
          title="刷新"
        >
          <RefreshCw className="size-4" />
        </Button>
      </div>

      {/* 表格 */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead className="w-8"></TableHead>
                <TableHead className="w-[64px]">封面</TableHead>
                <TableHead>
                  <SortHeader field="id" label="ID" active={sortBy === 'id'} order={sortOrder} onSort={handleSort} />
                </TableHead>
                <TableHead>来源ID</TableHead>
                <TableHead className="min-w-[160px]">名称</TableHead>
                <TableHead>别名</TableHead>
                <TableHead>作者</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>地区</TableHead>
                <TableHead>标签</TableHead>
                <TableHead className="text-right">
                  <SortHeader
                    field="clicks"
                    label="点击量"
                    active={sortBy === 'clicks'}
                    order={sortOrder}
                    onSort={handleSort}
                    className="ml-auto"
                  />
                </TableHead>
                <TableHead className="text-right">
                  <SortHeader
                    field="score"
                    label="评分"
                    active={sortBy === 'score'}
                    order={sortOrder}
                    onSort={handleSort}
                    className="ml-auto"
                  />
                </TableHead>
                <TableHead>
                  <SortHeader
                    field="updatedAt"
                    label="更新时间"
                    active={sortBy === 'updatedAt'}
                    order={sortOrder}
                    onSort={handleSort}
                  />
                </TableHead>
                <TableHead>
                  <SortHeader
                    field="inventory"
                    label="已入库时间"
                    active={sortBy === 'inventory'}
                    order={sortOrder}
                    onSort={handleSort}
                  />
                </TableHead>
                <TableHead className="text-center">
                  <SortHeader
                    field="chapterCount"
                    label="入库进度"
                    active={sortBy === 'chapterCount'}
                    order={sortOrder}
                    onSort={handleSort}
                  />
                </TableHead>
                <TableHead className="text-center">
                  <SortHeader
                    field="image"
                    label="图片进度"
                    active={sortBy === 'image'}
                    order={sortOrder}
                    onSort={handleSort}
                  />
                </TableHead>
                <TableHead className="text-center">
                  <SortHeader
                    field="image_done"
                    label="入库完成"
                    active={sortBy === 'image_done'}
                    order={sortOrder}
                    onSort={handleSort}
                  />
                </TableHead>
                <TableHead className="text-center">预计时间</TableHead>
                <TableHead>入库状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={20} className="h-32 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              ) : data.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={20} className="h-32 text-center text-slate-400">
                    暂无数据
                  </TableCell>
                </TableRow>
              ) : (
                data.map((b) => {
                  const cs = getCrawlStatus(b.crawlStatus)
                  const es = expanded[b.id]
                  const isOpen = !!es
                  return (
                    <Fragment key={b.id}>
                      <TableRow className="hover:bg-slate-50">
                        <TableCell className="pr-0">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => toggleRow(b.id)}
                            title={isOpen ? '收起章节' : '展开章节'}
                          >
                            {isOpen ? (
                              <ChevronDown className="size-4" />
                            ) : (
                              <ChevronRight className="size-4" />
                            )}
                          </Button>
                        </TableCell>
                        <TableCell>
                          <CoverImage
                            src={b.coverUrl}
                            name={b.name}
                            className="size-12 rounded-md"
                          />
                        </TableCell>
                        <TableCell className="font-medium text-slate-700">{b.id}</TableCell>
                        <TableCell className="text-slate-500">{b.sourceBookId}</TableCell>
                        <TableCell className="max-w-[200px]">
                          <button
                            type="button"
                            className="truncate text-left font-medium text-indigo-600 hover:underline"
                            onClick={() => setSelected(b)}
                            title={b.name}
                          >
                            {b.name}
                          </button>
                        </TableCell>
                        <TableCell className="max-w-[120px] truncate text-slate-500" title={b.alias}>
                          {b.alias ?? '-'}
                        </TableCell>
                        <TableCell className="text-slate-600">{b.author ?? '-'}</TableCell>
                        <TableCell className="text-slate-600">{b.status ?? '-'}</TableCell>
                        <TableCell className="text-slate-600">{b.region ?? '-'}</TableCell>
                        <TableCell className="max-w-[180px]">
                          <div className="flex flex-wrap gap-1">
                            {b.tags
                              ? b.tags.split(',').slice(0, 3).map((t) => (
                                  <Badge key={t} variant="secondary" className="font-normal">
                                    {t}
                                  </Badge>
                                ))
                              : '-'}
                          </div>
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-slate-600">
                          {b.clicks?.toLocaleString() ?? '-'}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-slate-600">
                          {b.score ?? '-'}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">
                          {formatDateTime(b.updateTime)}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">
                          {formatDateTime(b.crawlTime)}
                        </TableCell>
                        <TableCell className="text-center tabular-nums">
                          {ingesting[b.id] ? (
                            <ProgressCell
                              done={ingesting[b.id].processed}
                              total={ingesting[b.id].total || b.chapterCount || 0}
                            />
                          ) : (
                            <ProgressCell done={b.imageDoneCount ?? 0} total={b.chapterCount ?? 0} />
                          )}
                        </TableCell>
                        <TableCell className="text-center tabular-nums">
                          <ProgressCell
                            done={b.downloadedImageCount ?? 0}
                            total={b.totalImageCount ?? 0}
                          />
                        </TableCell>
                        <TableCell className="text-center">
                          {b.pendingChapterCount === 0 && (b.imageDoneCount ?? 0) > 0 ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
                              ✔ 已完成
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400">
                              待 {b.pendingChapterCount ?? 0} 章
                            </span>
                          )}
                        </TableCell>
                        <TableCell className="text-center text-xs tabular-nums text-slate-500">
                          <EtaCell
                            book={b}
                            ingesting={ingesting[b.id]}
                          />
                        </TableCell>
                        <TableCell>
                          <span
                            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cs.className}`}
                          >
                            {cs.label}
                          </span>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleIngest(b)}
                              disabled={!!ingesting[b.id]}
                              className="text-emerald-600 hover:text-emerald-700"
                              title="对该漫画单独启动图片入库"
                            >
                              {ingesting[b.id] ? (
                                <Loader2 className="mr-1 size-4 animate-spin" />
                              ) : (
                                <ImageIcon className="mr-1 size-4" />
                              )}
                              入库
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => window.open(`/crawllog/${b.id}`, '_blank')}
                              className="text-zinc-600 hover:text-zinc-800"
                              title="查看入库详情 / 爬虫日志"
                            >
                              <Terminal className="mr-1 size-4" />
                              入库详情
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => setSelected(b)}
                              className="text-indigo-600 hover:text-indigo-700"
                            >
                              <Eye className="mr-1 size-4" />
                              详情
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>

                      {/* 展开行: 章节列表(点击可阅读) */}
                      {isOpen && (
                        <TableRow className="bg-slate-50/60 hover:bg-slate-50/60">
                          <TableCell colSpan={20} className="p-0">
                            <div className="px-10 py-3">
                              <div className="mb-2 flex items-center gap-1">
                                <span className="text-sm font-medium text-slate-600">
                                  章节列表（{es.total} 话）
                                </span>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  className="gap-1 text-slate-500"
                                  onClick={() => toggleChapterOrder(b.id)}
                                  disabled={!es.loaded || es.total === 0}
                                >
                                  {es.orderDir === 'asc' ? (
                                    <>
                                      正序 <ArrowDownWideNarrow className="size-3.5" />
                                    </>
                                  ) : (
                                    <>
                                      倒序 <ArrowUpWideNarrow className="size-3.5" />
                                    </>
                                  )}
                                </Button>
                              </div>
                              {es.loading ? (
                                <div className="flex items-center gap-2 py-4 text-sm text-slate-400">
                                  <Loader2 className="size-4 animate-spin" />
                                  加载章节中…
                                </div>
                              ) : es.list.length === 0 ? (
                                <div className="py-3 text-sm text-slate-400">暂无章节数据</div>
                              ) : (
                                <>
                                  <div className="flex flex-wrap gap-2">
                                    {es.list.map((c) => (
                                      <button
                                        key={c.id}
                                        type="button"
                                        onClick={() => window.open(`/reader/${b.id}/${c.id}`, '_blank')}
                                        className="group inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 transition hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
                                        title={`${c.title ?? `第${c.chapterNo}话`}（新页签打开）`}
                                      >
                                        <BookOpen className="size-3.5 text-slate-400 group-hover:text-indigo-500" />
                                        <span className="max-w-[220px] truncate">
                                          {c.title ?? `第${c.chapterNo}话`}
                                        </span>
                                        {c.imageCount ? (
                                          <span className="text-xs text-slate-400 group-hover:text-indigo-400">
                                            ({c.imageCount})
                                          </span>
                                        ) : null}
                                        <ImageIcon className="size-3.5 text-slate-300 group-hover:text-indigo-400" />
                                      </button>
                                    ))}
                                  </div>
                                  {/* 章节分页 */}
                                  <div className="mt-3 flex items-center justify-start gap-1 text-slate-500">
                                    <Button
                                      variant="outline"
                                      size="sm"
                                      disabled={es.page <= 1}
                                      onClick={() => loadChapters(b.id, es.page - 1, es.orderDir)}
                                    >
                                      上一页
                                    </Button>
                                    <span className="px-2 text-sm">
                                      第 {es.page} / {Math.max(1, Math.ceil(es.total / CHAPTER_PAGE_SIZE))} 页
                                    </span>
                                    <Button
                                      variant="outline"
                                      size="sm"
                                      disabled={es.page >= Math.ceil(es.total / CHAPTER_PAGE_SIZE)}
                                      onClick={() => loadChapters(b.id, es.page + 1, es.orderDir)}
                                    >
                                      下一页
                                    </Button>
                                  </div>
                                </>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  )
                })
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* 分页 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3 text-sm text-slate-500">
          <span>
            共 <span className="font-medium text-slate-700">{total}</span> 条
          </span>
          <Select
            value={String(pageSize)}
            onValueChange={(v) => {
              setPageSize(Number(v))
              setPage(1)
            }}
          >
            <SelectTrigger className="h-8 w-[90px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZES.map((s) => (
                <SelectItem key={s} value={String(s)}>
                  {s} / 页
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <span className="whitespace-nowrap">
            第 <span className="font-medium text-slate-700">{page}</span> / {totalPages} 页
          </span>
        </div>

        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => setPage(1)}
            title="首页"
          >
            首页
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          {pageNums.map((n) => (
            <Button
              key={n}
              variant={n === page ? 'default' : 'outline'}
              size="sm"
              className="w-9"
              onClick={() => setPage(n)}
            >
              {n}
            </Button>
          ))}
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => setPage(totalPages)}
            title="末页"
          >
            末页
          </Button>

          {/* 跳页：输入页码回车或点击跳转 */}
          <div className="ml-2 flex items-center gap-1.5 text-sm text-slate-500">
            <span className="whitespace-nowrap">跳至</span>
            <Input
              type="number"
              min={1}
              max={totalPages}
              value={jumpValue}
              onChange={(e) => setJumpValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleJump()
              }}
              className="h-8 w-16 text-center"
              placeholder={String(page)}
            />
            <span>页</span>
            <Button size="sm" variant="secondary" onClick={handleJump}>
              跳转
            </Button>
          </div>
        </div>
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent showCloseButton={!batchSubmitting}>
          <DialogHeader>
            <DialogTitle>确认一键入库</DialogTitle>
            <DialogDescription>
              将按当前列表排序顺序入库所有“未入库”和“入库中”的漫画，仅保存图片 URL，不下载图片文件。
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3 py-2">
            <div className="border border-slate-200 bg-slate-50 p-3">
              <div className="text-xs text-slate-500">即将入库漫画</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums text-slate-800">
                {ingestPreview?.bookCount ?? 0}
                <span className="ml-1 text-sm font-normal text-slate-500">部</span>
              </div>
            </div>
            <div className="border border-slate-200 bg-slate-50 p-3">
              <div className="text-xs text-slate-500">章节总数</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums text-slate-800">
                {(ingestPreview?.chapterCount ?? 0).toLocaleString()}
                <span className="ml-1 text-sm font-normal text-slate-500">章</span>
              </div>
            </div>
          </div>
          {ingestPreview?.running && (
            <p className="text-sm text-amber-700">已有一键入库任务正在执行，请等待其结束。</p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setPreviewOpen(false)} disabled={batchSubmitting}>
              取消
            </Button>
            <Button
              onClick={confirmBatchIngest}
              disabled={batchSubmitting || ingestPreview?.running || (ingestPreview?.bookCount ?? 0) === 0}
              className="bg-emerald-600 hover:bg-emerald-700"
            >
              {batchSubmitting && <Loader2 className="mr-1 size-4 animate-spin" />}
              确定入库
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {batchStatus?.running && (
        <div className="fixed right-6 top-20 z-[70] w-[min(24rem,calc(100vw-3rem))] animate-in fade-in slide-in-from-top-3 duration-300">
          <div className="border border-emerald-300 bg-white px-4 py-3 shadow-lg">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="text-sm font-semibold text-emerald-700">
                  {batchStatus.cancelRequested ? '正在停止一键入库' : '一键入库进行中'}
                </div>
                <div className="mt-1 text-sm text-slate-700">
                  已入库 <span className="font-semibold tabular-nums">{batchStatus.completedBooks}</span>
                  <span className="text-slate-400"> / </span>
                  <span className="font-semibold tabular-nums">{batchStatus.totalBooks}</span> 部漫画
                </div>
                {batchStatus.currentBookName && (
                  <div className="mt-1 truncate text-xs text-slate-500" title={batchStatus.currentBookName}>
                    正在入库：{batchStatus.currentBookName}
                  </div>
                )}
              </div>
              <button
                type="button"
                onClick={cancelCurrentBatch}
                disabled={cancellingBatch || batchStatus.cancelRequested}
                className="shrink-0 text-xs text-rose-600 underline underline-offset-2 hover:text-rose-700 disabled:cursor-not-allowed disabled:text-slate-400"
              >
                {cancellingBatch || batchStatus.cancelRequested ? '正在取消…' : '取消'}
              </button>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-emerald-100">
              <div
                className="h-full bg-emerald-500 transition-[width] duration-300"
                style={{ width: `${batchStatus.totalBooks > 0 ? (batchStatus.completedBooks / batchStatus.totalBooks) * 100 : 0}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {completionNotice && (
        <div key={completionNotice.id} className="fixed right-6 bottom-6 z-[70] max-w-md animate-in fade-in slide-in-from-bottom-3 duration-300">
          <div className="border border-emerald-300 bg-gradient-to-r from-emerald-500 to-teal-500 px-4 py-3 text-white shadow-lg">
            <div className="text-sm font-semibold">入库完成</div>
            <div className="mt-1 text-sm leading-6">
              漫画《{completionNotice.bookName ?? completionNotice.bookId}》共 {completionNotice.totalChapterCount} 章，入库完成，耗时{' '}
              {completionNotice.durationSeconds ?? 0} 秒
            </div>
          </div>
        </div>
      )}

      <ComicDetailDrawer book={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

/** 进度单元格: 已爬/总数 + 进度条 */
function ProgressCell({ done, total }: { done: number; total: number }) {
  const t = total || 0
  const d = done || 0
  const pct = t > 0 ? Math.round((d / t) * 100) : 0
  const full = t > 0 && d >= t
  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex items-center gap-1 text-xs tabular-nums">
        <span className={full ? 'font-medium text-emerald-600' : 'text-slate-600'}>{d}</span>
        <span className="text-slate-300">/</span>
        <span className="text-slate-500">{t}</span>
      </div>
      <div className="h-1.5 w-16 overflow-hidden rounded-full bg-slate-100">
        <div
          className={`h-full rounded-full ${full ? 'bg-emerald-500' : 'bg-indigo-500'}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}

/** 预计完成时间单元格: 根据入库期间实时速率估算剩余时间 */
function EtaCell({
  ingesting,
}: {
  book: Book
  ingesting?: {
    startedAt: number
    total: number
    processed: number
    success: number
    fail: number
    currentChapter: string
    progress: number
  }
}) {
  if (!ingesting) return <span className="text-slate-300">—</span>
  const total = ingesting.total || 0
  const processed = ingesting.processed || 0
  const remaining = Math.max(0, total - processed)
  if (remaining === 0) return <span className="text-emerald-600">即将完成</span>
  const elapsedMs = Date.now() - ingesting.startedAt
  if (processed <= 0 || elapsedMs <= 0) return <span className="text-slate-400">计算中…</span>
  const ratePerSec = processed / (elapsedMs / 1000)
  if (ratePerSec <= 0) return <span className="text-slate-400">计算中…</span>
  const etaSec = remaining / ratePerSec
  if (etaSec > 3600 * 24) return <span className="text-slate-400">{(etaSec / 86400).toFixed(1)} 天</span>
  if (etaSec > 3600) return <span className="text-slate-500">{(etaSec / 3600).toFixed(1)} 时</span>
  if (etaSec > 60) return <span className="text-slate-500">{(etaSec / 60).toFixed(1)} 分</span>
  return <span className="text-slate-500">{Math.ceil(etaSec)} 秒</span>
}
