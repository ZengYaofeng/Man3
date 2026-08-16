import { Fragment, useCallback, useEffect, useState } from 'react'
import { CheckCircle2, ChevronDown, ChevronRight, CircleSlash, ExternalLink, Loader2, Pause, Play, Search } from 'lucide-react'
import ChapterListPanel from '@/components/comic/ChapterListPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  fetchExternalComicCrawlStatus,
  fetchExternalComics,
  fetchExternalComicStats,
  fetchExternalChapters,
  fetchExternalIngestStatus,
  startExternalComicCrawl,
  startExternalIngest,
  stopExternalIngest,
} from '@/lib/externalComicApi'
import type {
  ExternalComic,
  ExternalComicCrawlStatus,
  ExternalComicSource,
  ExternalComicStats,
  ExternalIngestStatus,
} from '@/types/externalComic'

const PAGE_SIZES = [20, 50, 100]
interface ChapterRange {
  value: string
  label: string
  min?: number
  max?: number
}
const CHAPTER_RANGES: ChapterRange[] = [
  { value: 'all', label: '全部章节数' },
  { value: '0-50', label: '0-50 章', min: 0, max: 50 },
  { value: '50-100', label: '50-100 章', min: 50, max: 100 },
  { value: '100-300', label: '100-300 章', min: 100, max: 300 },
  { value: '300+', label: '300 章以上', min: 300 },
]

const SOURCE_META: Record<ExternalComicSource, { title: string; url: string }> = {
  niaoniaomh: { title: '鸟鸟韩漫', url: 'https://nnhm7.com/comics' },
  yuyumh: { title: '汙汙漫畫', url: 'https://www.comicbox.xyz/booklist?page=1' },
}

export default function ExternalComicListPage({ source }: { source: ExternalComicSource }) {
  const meta = SOURCE_META[source]
  const [keyword, setKeyword] = useState('')
  const [same, setSame] = useState<number | null>(null)
  const [ingestFilter, setIngestFilter] = useState('all')
  const [chapterRange, setChapterRange] = useState('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [list, setList] = useState<ExternalComic[]>([])
  const [total, setTotal] = useState(0)
  const [stats, setStats] = useState<ExternalComicStats>({ total: 0, same: 0, different: 0 })
  const [status, setStatus] = useState<ExternalComicCrawlStatus | null>(null)
  const [ingestStatus, setIngestStatus] = useState<ExternalIngestStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  const load = useCallback(async () => {
    setLoading(true)
    const selectedChapterRange = CHAPTER_RANGES.find((item) => item.value === chapterRange)
    try {
      const [result, nextStats, crawlStatus, nextIngestStatus] = await Promise.all([
        fetchExternalComics(source, page, pageSize, keyword, same, selectedChapterRange?.min, selectedChapterRange?.max, ingestFilter === 'all' ? null : Number(ingestFilter)),
        fetchExternalComicStats(source),
        fetchExternalComicCrawlStatus(source),
        fetchExternalIngestStatus(source),
      ])
      setList(result.list)
      setTotal(result.total)
      setStats(nextStats)
      setStatus(crawlStatus)
      setIngestStatus(nextIngestStatus)
      setError('')
    } catch (e) {
      setError((e as Error).message || '加载失败')
    } finally {
      setLoading(false)
    }
  }, [chapterRange, ingestFilter, keyword, page, pageSize, same, source])

  useEffect(() => {
    const timer = window.setTimeout(load, 180)
    return () => window.clearTimeout(timer)
  }, [load])

  useEffect(() => {
    if (!status?.running && !ingestStatus?.running) return
    const timer = window.setInterval(load, 1800)
    return () => window.clearInterval(timer)
  }, [load, status?.running, ingestStatus?.running])

  const startCrawl = async () => {
    setStarting(true)
    try {
      setStatus(await startExternalComicCrawl(source))
      setError('')
    } catch (e) {
      setError((e as Error).message || '提交抓取任务失败')
    } finally {
      setStarting(false)
    }
  }

  const startIngest = async (stage: 'chapters' | 'images', externalComicId?: number) => {
    try {
      setIngestStatus(await startExternalIngest(source, stage, externalComicId))
      setError('')
    } catch (e) {
      setError((e as Error).message || '提交入库任务失败')
    }
  }

  const stopIngest = async () => {
    try {
      setIngestStatus(await stopExternalIngest(source))
    } catch (e) {
      setError((e as Error).message || '停止入库任务失败')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const changeSame = (value: number | null) => {
    setSame(value)
    setPage(1)
  }

  const toggleChapters = (comicId: number) => {
    setExpanded((current) => ({ ...current, [comicId]: !current[comicId] }))
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[240px] flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            className="pl-9"
            placeholder={`搜索${meta.title}漫画名称 / 作者`}
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPage(1)
            }}
          />
        </div>
        <div className="flex items-center rounded-lg border border-slate-200 bg-white p-1">
          {[
            { value: null, label: '全部' },
            { value: 0, label: '本地没有' },
            { value: 1, label: '本地已有' },
          ].map((item) => (
            <button
              key={item.label}
              type="button"
              onClick={() => changeSame(item.value)}
              className={`rounded-md px-3 py-1.5 text-sm transition ${
                same === item.value ? 'bg-indigo-50 font-medium text-indigo-700' : 'text-slate-500 hover:bg-slate-50'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
        <Select value={ingestFilter} onValueChange={(value) => { setIngestFilter(value); setPage(1) }}>
          <SelectTrigger className="w-[140px]"><SelectValue placeholder="入库状态" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部入库状态</SelectItem>
            <SelectItem value="0">未入库</SelectItem>
            <SelectItem value="1">入库中</SelectItem>
            <SelectItem value="2">已入库</SelectItem>
          </SelectContent>
        </Select>
        {(source === 'niaoniaomh' || source === 'yuyumh') && (
          <Select
            value={chapterRange}
            onValueChange={(value) => {
              setChapterRange(value)
              setPage(1)
            }}
          >
            <SelectTrigger className="w-[150px]">
              <SelectValue placeholder="章节数" />
            </SelectTrigger>
            <SelectContent>
              {CHAPTER_RANGES.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <Button className="ml-auto" onClick={startCrawl} disabled={starting || status?.running}>
          {starting || status?.running ? <Loader2 className="mr-1.5 size-4 animate-spin" /> : <Play className="mr-1.5 size-4" />}
          {status?.running ? '主表抓取中…' : '抓取漫画主表'}
        </Button>
        <Button variant="outline" onClick={() => startIngest('chapters')} disabled={!!ingestStatus?.running}>
          <Play className="mr-1.5 size-4" />章节入库
        </Button>
        <Button variant="outline" onClick={() => startIngest('images')} disabled={!!ingestStatus?.running}>
          <Play className="mr-1.5 size-4" />图片 URL 入库
        </Button>
        {ingestStatus?.running && <Button variant="ghost" className="text-rose-600" onClick={stopIngest}><Pause className="mr-1.5 size-4" />停止</Button>}
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard label="来源漫画" value={stats.total} />
        <StatCard label="本地已有" value={stats.same} tone="emerald" />
        <StatCard label="本地没有" value={stats.different} tone="amber" />
      </div>

      {status && (
        <div className={`flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg border px-4 py-2 text-sm ${
          status.running ? 'border-sky-200 bg-sky-50 text-sky-700' : 'border-slate-200 bg-white text-slate-500'
        }`}>
          {status.running && <Loader2 className="size-4 animate-spin" />}
          <span>{status.message}</span>
          {status.running && <span>已处理 {status.processed} 部 / {status.pages} 页</span>}
          {status.running && status.currentName && <span className="max-w-[280px] truncate">当前：{status.currentName}</span>}
        </div>
      )}

      {ingestStatus && (
        <div className={`flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg border px-4 py-2 text-sm ${
          ingestStatus.running ? 'border-violet-200 bg-violet-50 text-violet-700' : 'border-slate-200 bg-white text-slate-500'
        }`}>
          {ingestStatus.running && <Loader2 className="size-4 animate-spin" />}
          <span>{ingestStatus.message}</span>
          <span>已处理 {ingestStatus.processed} / {ingestStatus.total}</span>
          <span>成功 {ingestStatus.success}，失败 {ingestStatus.failed}</span>
          {ingestStatus.currentName && <span className="max-w-[280px] truncate">当前：{ingestStatus.currentName}</span>}
        </div>
      )}

      {error && <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-700">{error}</div>}

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead className="w-12" />
                <TableHead className="w-16">封面</TableHead>
                <TableHead className="w-24">来源ID</TableHead>
                <TableHead className="min-w-[180px]">漫画名称</TableHead>
                <TableHead className="min-w-[130px]">作者</TableHead>
                <TableHead className="min-w-[150px]">标签</TableHead>
                <TableHead className="w-20 text-right">章节</TableHead>
                <TableHead className="w-28">是否相同</TableHead>
                <TableHead className="w-28">本地主表ID</TableHead>
                <TableHead className="w-28">来源更新</TableHead>
                <TableHead className="min-w-[130px]">入库详情</TableHead>
                <TableHead className="w-20 text-right">原站</TableHead>
                <TableHead className="min-w-[160px] text-right">入库操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <EmptyRow text="加载中…" />
              ) : list.length === 0 ? (
                <EmptyRow text="暂无数据，点击“抓取漫画主表”开始同步" />
              ) : list.map((comic) => {
                const isOpen = !!expanded[comic.id]
                return (
                <Fragment key={comic.id}>
                <TableRow className="hover:bg-slate-50">
                  <TableCell className="pr-0">
                    <Button variant="ghost" size="icon" className="size-7" onClick={() => toggleChapters(comic.id)} title={isOpen ? 'Collapse chapters' : 'Expand chapters'}>
                      {isOpen ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                    </Button>
                  </TableCell>
                  <TableCell>
                    {comic.coverUrl ? <img src={comic.coverUrl} alt="" className="h-12 w-9 rounded object-cover bg-slate-100" referrerPolicy="no-referrer" /> : <div className="h-12 w-9 rounded bg-slate-100" />}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-slate-500">{comic.sourceBookId}</TableCell>
                  <TableCell className="font-medium text-slate-800"><div className="max-w-[260px] truncate" title={comic.name}>{comic.name}</div></TableCell>
                  <TableCell><div className="max-w-[180px] truncate text-slate-600" title={comic.author ?? ''}>{comic.author || '-'}</div></TableCell>
                  <TableCell><div className="max-w-[210px] truncate text-slate-500" title={comic.tags ?? ''}>{comic.tags || '-'}</div></TableCell>
                  <TableCell className="text-right tabular-nums text-slate-600">{comic.chapterCount ?? '-'}</TableCell>
                  <TableCell>
                    {comic.isSame === 1 ? <Badge className="gap-1 bg-emerald-100 text-emerald-700 hover:bg-emerald-100"><CheckCircle2 className="size-3" />相同</Badge> : <Badge className="gap-1 bg-amber-100 text-amber-700 hover:bg-amber-100"><CircleSlash className="size-3" />不同</Badge>}
                  </TableCell>
                  <TableCell className="text-slate-600">{comic.matchedBookId ?? '-'}</TableCell>
                  <TableCell className="text-xs text-slate-500">{comic.sourceUpdateText || '-'}</TableCell>
                  <TableCell className="text-xs text-slate-600">
                    <IngestStatusBadge status={comic.ingestStatus} />
                    <div>章节 {comic.ingestedChapterCount ?? 0}/{comic.chapterCount ?? 0}</div>
                    <div className="text-slate-400">图片完成 {comic.imageDoneChapterCount ?? 0} 章 · {comic.ingestedImageCount ?? 0} 张</div>
                  </TableCell>
                  <TableCell className="text-right">
                    <a href={comic.sourceUrl} target="_blank" rel="noreferrer" className="inline-flex rounded-md p-1.5 text-indigo-600 hover:bg-indigo-50" title="打开来源页面"><ExternalLink className="size-4" /></a>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => startIngest('chapters', comic.id)} disabled={comic.isSame === 1 || !!ingestStatus?.running} title="同步该漫画的章节">
                        章节
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => startIngest('images', comic.id)} disabled={comic.isSame === 1 || !!ingestStatus?.running || !comic.ingestedChapterCount} title="解析该漫画每章的图片 URL">
                        图片
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
                {isOpen && (
                  <TableRow className="bg-slate-50/60 hover:bg-slate-50/60">
                    <TableCell colSpan={13} className="p-0">
                      <ChapterListPanel
                        loadPage={(chapterPage, orderDir) => fetchExternalChapters(source, comic.id, chapterPage, 20, orderDir)}
                        onRead={(chapter) => window.open(`/reader/${source}/${comic.id}/${chapter.id}`, '_blank')}
                      />
                    </TableCell>
                  </TableRow>
                )}
                </Fragment>
                )
              })}
            </TableBody>
          </Table>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          共 <span className="font-medium text-slate-700">{total}</span> 条
          <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1) }} className="h-8 rounded-md border border-slate-200 bg-white px-2 text-sm">
            {PAGE_SIZES.map((size) => <option key={size} value={size}>{size} / 页</option>)}
          </select>
        </div>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</Button>
          <span className="px-2 text-sm text-slate-500">第 {page} / {totalPages} 页</span>
          <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((current) => current + 1)}>下一页</Button>
        </div>
      </div>

      <a href={meta.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-indigo-600">
        来源列表：{meta.url}<ExternalLink className="size-3" />
      </a>
    </div>
  )
}

function StatCard({ label, value, tone = 'slate' }: { label: string; value: number; tone?: 'slate' | 'emerald' | 'amber' }) {
  const tones = {
    slate: 'border-slate-200 bg-white text-slate-700',
    emerald: 'border-emerald-200 bg-emerald-50 text-emerald-700',
    amber: 'border-amber-200 bg-amber-50 text-amber-700',
  }
  return <div className={`rounded-xl border px-4 py-3 ${tones[tone]}`}><div className="text-xs opacity-70">{label}</div><div className="mt-1 text-xl font-semibold tabular-nums">{value.toLocaleString()}</div></div>
}

function EmptyRow({ text }: { text: string }) {
  return <TableRow><TableCell colSpan={13} className="h-32 text-center text-slate-400">{text}</TableCell></TableRow>
}

function IngestStatusBadge({ status }: { status?: 0 | 1 | 2 | 3 }) {
  const config = status === 2
    ? { label: '已入库', className: 'bg-emerald-100 text-emerald-700' }
    : status === 1
      ? { label: '入库中', className: 'bg-sky-100 text-sky-700' }
      : status === 3
        ? { label: '无需入库', className: 'bg-slate-100 text-slate-500' }
        : { label: '未入库', className: 'bg-slate-100 text-slate-600' }
  return <span className={`mb-1 inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${config.className}`}>{config.label}</span>
}
