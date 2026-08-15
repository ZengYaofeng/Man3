import { useEffect, useState } from 'react'
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
import { Search, RefreshCw, ArrowUp, ArrowDown, ChevronsUpDown, Eye, ClipboardList } from 'lucide-react'
import CoverImage from '@/components/comic/CoverImage'
import ComicDetailDrawer from '@/components/comic/ComicDetailDrawer'
import { fetchBooks, fetchBookOptions, runInventory, type BookOptions } from '@/lib/api'
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
  const [crawlStatus, setCrawlStatus] = useState('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [jumpValue, setJumpValue] = useState('')
  const [sortBy, setSortBy] = useState<SortField>('inventory')
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc')
  const [loading, setLoading] = useState(false)
  const [inventoryRunning, setInventoryRunning] = useState(false)
  const [inventoryMsg, setInventoryMsg] = useState<string | null>(null)
  const [data, setData] = useState<Book[]>([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState<Book | null>(null)
  const [options, setOptions] = useState<BookOptions>({
    regions: [],
    statuses: ['连载中', '已完结'],
    sortFields: ['score', 'updateTime', 'clicks', 'createdAt'],
  })

  useEffect(() => {
    fetchBookOptions().then(setOptions)
  }, [])

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
        crawlStatus: crawlStatus === 'all' ? null : Number(crawlStatus),
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
  }, [keyword, region, status, crawlStatus, page, pageSize, sortBy, sortOrder])

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
          value={crawlStatus}
          onValueChange={(v) => {
            setCrawlStatus(v)
            setPage(1)
          }}
        >
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="爬取状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部爬取状态</SelectItem>
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
            setCrawlStatus('all')
            setPage(1)
          }}
        >
          重置
        </Button>

        {/* 入库盘点：触发一次盘点，更新章节/漫画入库字段并写入盘点记录 */}
        <Button
          variant="default"
          disabled={inventoryRunning}
          onClick={async () => {
            setInventoryRunning(true)
            setInventoryMsg(null)
            try {
              const rec = await runInventory()
              setInventoryMsg(
                `盘点完成：扫描 ${rec.scannedBookCount} 本，新增入库完成 ${rec.doneBookCount} 本，更新章节 ${rec.updatedChapterCount} 章`,
              )
              setPage(1)
            } catch (e) {
              setInventoryMsg(`盘点失败：${(e as Error).message}`)
            } finally {
              setInventoryRunning(false)
            }
          }}
        >
          <ClipboardList className="mr-1 size-4" />
          {inventoryRunning ? '盘点中…' : '入库盘点'}
        </Button>
        {inventoryMsg && (
          <span className="max-w-[360px] truncate text-sm text-emerald-600" title={inventoryMsg}>
            {inventoryMsg}
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
                    field="chapter"
                    label="章节进度"
                    active={sortBy === 'chapter'}
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
                <TableHead>爬取状态</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={19} className="h-32 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              ) : data.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={19} className="h-32 text-center text-slate-400">
                    暂无数据
                  </TableCell>
                </TableRow>
              ) : (
                data.map((b) => {
                  const cs = getCrawlStatus(b.crawlStatus)
                  return (
                    <TableRow key={b.id} className="hover:bg-slate-50">
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
                        <ProgressCell done={b.chapterCount ?? 0} total={b.chapterCount ?? 0} />
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
                      <TableCell>
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cs.className}`}
                        >
                          {cs.label}
                        </span>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelected(b)}
                          className="text-indigo-600 hover:text-indigo-700"
                        >
                          <Eye className="mr-1 size-4" />
                          详情
                        </Button>
                      </TableCell>
                    </TableRow>
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
