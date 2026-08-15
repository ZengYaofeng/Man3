import { useEffect, useState, useCallback } from 'react'
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
import { ChevronDown, ChevronRight, BookOpen, Image as ImageIcon, Loader2, ArrowDownWideNarrow, ArrowUpWideNarrow } from 'lucide-react'
import CoverImage from '@/components/comic/CoverImage'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { fetchBooks, fetchChapters, type Chapter } from '@/lib/api'
import {
  type Book,
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

export default function ChapterList() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [jumpValue, setJumpValue] = useState('')
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<Book[]>([])
  const [total, setTotal] = useState(0)
  // 展开行的 bookId -> 章节分页状态
  const [expanded, setExpanded] = useState<Record<number, ExpandState>>({})

  const loadBooks = useCallback(async () => {
    setLoading(true)
    const res = await fetchBooks({
      page,
      pageSize,
      keyword,
      // crawlStatus=1 表示章节已爬取(即已有章节)
      crawlStatus: 1,
      sortBy: 'id',
      sortOrder: 'desc',
    })
    setData(res.data)
    setTotal(res.total)
    setLoading(false)
  }, [page, pageSize, keyword])

  useEffect(() => {
    let cancelled = false
    const t = setTimeout(async () => {
      setLoading(true)
      const res = await fetchBooks({
        page,
        pageSize,
        keyword,
        crawlStatus: 1,
        sortBy: 'id',
        sortOrder: 'desc',
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
  }, [page, pageSize, keyword])

  // 加载某本书的章节(指定页码与排序方向)
  const loadChapters = useCallback(
    async (bookId: number, chapterPage: number, orderDir: 'asc' | 'desc') => {
      setExpanded((prev) => ({
        ...prev,
        [bookId]: {
          ...(prev[bookId] ?? {
            list: [],
            total: 0,
            page: 1,
            loaded: false,
            orderDir: 'asc' as const,
          }),
          loading: true,
          page: chapterPage,
          orderDir,
        },
      }))
      try {
        const res = await fetchChapters(bookId, chapterPage, CHAPTER_PAGE_SIZE, orderDir)
        setExpanded((prev) => ({
          ...prev,
          [bookId]: {
            list: res.list,
            total: res.total,
            page: res.page,
            loading: false,
            loaded: true,
            orderDir,
          },
        }))
      } catch {
        setExpanded((prev) => ({
          ...prev,
          [bookId]: {
            ...(prev[bookId] ?? {
              list: [],
              total: 0,
              page: 1,
              loaded: false,
              orderDir: 'asc' as const,
            }),
            loading: false,
            loaded: true,
            orderDir,
          },
        }))
      }
    },
    [],
  )

  const toggleExpand = (book: Book) => {
    setExpanded((prev) => {
      const isOpen = !!prev[book.id]
      if (isOpen) {
        const next = { ...prev }
        delete next[book.id]
        return next
      }
      // 首次展开时加载章节(默认正序)
      loadChapters(book.id, 1, 'asc')
      return {
        ...prev,
        [book.id]: { list: [], total: 0, page: 1, loading: true, loaded: false, orderDir: 'asc' },
      }
    })
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const chapterTotalPages = (bookId: number) => {
    const st = expanded[bookId]
    return Math.max(1, Math.ceil((st?.total ?? 0) / CHAPTER_PAGE_SIZE))
  }

  const handleJump = () => {
    const n = parseInt(jumpValue, 10)
    if (!Number.isNaN(n)) {
      setPage(Math.min(Math.max(1, n), totalPages))
    }
    setJumpValue('')
  }

  const pageNums: number[] = []
  const start = Math.max(1, page - 2)
  const end = Math.min(totalPages, start + 4)
  for (let i = start; i <= end; i++) pageNums.push(i)

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-sm">
          <input
            placeholder="搜索名称 / 别名 / 作者"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(1)
            }}
            className="h-9 w-full rounded-md border border-slate-300 bg-white pl-9 pr-3 text-sm outline-none focus:border-indigo-400"
          />
        </div>
        <Button
          variant="outline"
          onClick={() => {
            setKeyword('')
            setPage(1)
          }}
        >
          重置
        </Button>
        <Button variant="outline" size="icon" onClick={loadBooks} title="刷新">
          <Loader2 className={`size-4 ${loading ? 'animate-spin' : ''}`} />
        </Button>
        <span className="ml-auto text-sm text-slate-500">
          仅展示已爬取章节的漫画（共 {total} 部）
        </span>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead className="w-[44px]"></TableHead>
                <TableHead className="w-[64px]">封面</TableHead>
                <TableHead>ID</TableHead>
                <TableHead>来源ID</TableHead>
                <TableHead className="min-w-[160px]">名称</TableHead>
                <TableHead>作者</TableHead>
                <TableHead>地区</TableHead>
                <TableHead>爬取状态</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">章节数</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={10} className="h-32 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              ) : data.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} className="h-32 text-center text-slate-400">
                    暂无已爬取章节的漫画
                  </TableCell>
                </TableRow>
              ) : (
                data.map((b) => {
                  const open = !!expanded[b.id]
                  const st = expanded[b.id]
                  const chPages = chapterTotalPages(b.id)
                  return (
                    <FragmentRow
                      key={b.id}
                      book={b}
                      open={open}
                      onToggle={() => toggleExpand(b)}
                      st={st}
                      chPageSize={CHAPTER_PAGE_SIZE}
                      chPages={chPages}
                      onChapterPage={(p) => loadChapters(b.id, p, st?.orderDir ?? 'asc')}
                      onToggleOrder={(dir) => loadChapters(b.id, 1, dir)}
                    />
                  )
                })
              )}
            </TableBody>
          </Table>
        </div>
      </div>

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
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(1)}>
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
          >
            末页
          </Button>
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
    </div>
  )
}

function FragmentRow({
  book,
  open,
  onToggle,
  st,
  chPageSize,
  chPages,
  onChapterPage,
  onToggleOrder,
}: {
  book: Book
  open: boolean
  onToggle: () => void
  st?: ExpandState
  chPageSize: number
  chPages: number
  onChapterPage: (p: number) => void
  onToggleOrder: (dir: 'asc' | 'desc') => void
}) {
  const cs = getCrawlStatus(book.crawlStatus)
  const chNums: number[] = []
  const cstart = Math.max(1, (st?.page ?? 1) - 2)
  const cend = Math.min(chPages, cstart + 4)
  for (let i = cstart; i <= cend; i++) chNums.push(i)

  return (
    <>
      <TableRow className="cursor-pointer hover:bg-slate-50" onClick={onToggle}>
        <TableCell>
          {open ? (
            <ChevronDown className="size-4 text-slate-500" />
          ) : (
            <ChevronRight className="size-4 text-slate-400" />
          )}
        </TableCell>
        <TableCell>
          <CoverImage src={book.coverUrl} name={book.name} className="size-12 rounded-md" />
        </TableCell>
        <TableCell className="font-medium text-slate-700">{book.id}</TableCell>
        <TableCell className="text-slate-500">{book.sourceBookId}</TableCell>
        <TableCell className="max-w-[200px] truncate font-medium" title={book.name}>
          {book.name}
        </TableCell>
        <TableCell className="text-slate-600">{book.author ?? '-'}</TableCell>
        <TableCell className="text-slate-600">{book.region ?? '-'}</TableCell>
        <TableCell>
          <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cs.className}`}>
            {cs.label}
          </span>
        </TableCell>
        <TableCell className="whitespace-nowrap text-slate-500">{formatDateTime(book.updateTime)}</TableCell>
        <TableCell className="text-right tabular-nums text-slate-600">
          {st?.loaded ? st.total : '-'}
        </TableCell>
      </TableRow>

      {open && (
        <TableRow className="bg-slate-50/60">
          <TableCell colSpan={10} className="p-0">
            <div className="px-6 py-4">
              {st?.loading ? (
                <div className="flex items-center gap-2 text-sm text-slate-400">
                  <Loader2 className="size-4 animate-spin" />
                  加载章节中…
                </div>
              ) : st && st.list.length > 0 ? (
                <div className="space-y-3">
                  <div className="text-xs font-medium text-slate-500">
                    共 {st.total} 章 · 第 {st.page} / {chPages} 页
                  </div>
                  <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                    {st.list.map((c) => (
                      <div
                        key={c.id}
                        className="flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      >
                        <BookOpen className="size-3.5 shrink-0 text-indigo-500" />
                        <span className="truncate text-slate-700" title={c.title ?? `第${c.chapterNo}话`}>
                          {c.title ?? `第${c.chapterNo}话`}
                        </span>
                        {c.imageCount != null && c.imageCount > 0 && (
                          <span className="ml-auto flex shrink-0 items-center gap-0.5 text-xs text-slate-400">
                            <ImageIcon className="size-3" />
                            {c.imageCount}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                  {/* 章节分页 */}
                  <div className="flex flex-wrap items-center gap-1 pt-1">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={st.page <= 1}
                      onClick={() => onChapterPage(Math.max(1, st.page - 1))}
                    >
                      上一页
                    </Button>
                    {chNums.map((n) => (
                      <Button
                        key={n}
                        variant={n === st.page ? 'default' : 'outline'}
                        size="sm"
                        className="w-9"
                        onClick={() => onChapterPage(n)}
                      >
                        {n}
                      </Button>
                    ))}
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={st.page >= chPages}
                      onClick={() => onChapterPage(Math.min(chPages, st.page + 1))}
                    >
                      下一页
                    </Button>
                    <Badge variant="secondary" className="ml-2 font-normal">
                      每页 {chPageSize} 章
                    </Badge>
                    <Button
                      variant="outline"
                      size="sm"
                      className="ml-1"
                      title={st?.orderDir === 'desc' ? '当前倒序，点击切为正序' : '当前正序，点击切为倒序'}
                      onClick={() =>
                        onToggleOrder(st?.orderDir === 'desc' ? 'asc' : 'desc')
                      }
                    >
                      {st?.orderDir === 'desc' ? (
                        <ArrowDownWideNarrow className="size-3.5" />
                      ) : (
                        <ArrowUpWideNarrow className="size-3.5" />
                      )}
                      {st?.orderDir === 'desc' ? '倒序' : '正序'}
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="text-sm text-slate-400">暂无章节数据</div>
              )}
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  )
}
