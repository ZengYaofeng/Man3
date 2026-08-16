import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowDownWideNarrow, ArrowUpWideNarrow, BookOpen, Image as ImageIcon, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface ComicChapterItem {
  id: number
  chapterNo: number
  title: string | null
  imageCount: number | null
}

export interface ComicChapterPage<T extends ComicChapterItem = ComicChapterItem> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

interface ChapterListPanelProps<T extends ComicChapterItem> {
  loadPage: (page: number, orderDir: 'asc' | 'desc') => Promise<ComicChapterPage<T>>
  onRead: (chapter: T) => void
  pageSize?: number
}

export default function ChapterListPanel<T extends ComicChapterItem>({
  loadPage,
  onRead,
  pageSize = 20,
}: ChapterListPanelProps<T>) {
  const loadPageRef = useRef(loadPage)
  loadPageRef.current = loadPage
  const [result, setResult] = useState<ComicChapterPage<T>>({ list: [], total: 0, page: 1, pageSize })
  const [orderDir, setOrderDir] = useState<'asc' | 'desc'>('asc')
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async (page: number, nextOrderDir: 'asc' | 'desc') => {
    setLoading(true)
    setFailed(false)
    try {
      setResult(await loadPageRef.current(page, nextOrderDir))
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(1, 'asc')
  }, [load])

  const switchOrder = () => {
    const nextOrderDir = orderDir === 'asc' ? 'desc' : 'asc'
    setOrderDir(nextOrderDir)
    void load(1, nextOrderDir)
  }

  const totalPages = Math.max(1, Math.ceil(result.total / result.pageSize))

  return (
    <div className="px-10 py-3">
      <div className="mb-2 flex items-center gap-1">
        <span className="text-sm font-medium text-slate-600">章节列表（{result.total} 话）</span>
        <Button variant="ghost" size="sm" className="gap-1 text-slate-500" onClick={switchOrder} disabled={loading || result.total === 0}>
          {orderDir === 'asc' ? <><span>正序</span><ArrowDownWideNarrow className="size-3.5" /></> : <><span>倒序</span><ArrowUpWideNarrow className="size-3.5" /></>}
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 py-4 text-sm text-slate-400"><Loader2 className="size-4 animate-spin" />加载章节中...</div>
      ) : failed ? (
        <div className="flex items-center gap-2 py-3 text-sm text-rose-600"><span>章节加载失败</span><Button variant="link" size="sm" className="h-auto p-0" onClick={() => void load(result.page, orderDir)}>重试</Button></div>
      ) : result.list.length === 0 ? (
        <div className="py-3 text-sm text-slate-400">暂无章节数据，请先完成章节入库。</div>
      ) : (
        <>
          <div className="flex flex-wrap gap-2">
            {result.list.map((chapter) => (
              <button
                key={chapter.id}
                type="button"
                onClick={() => onRead(chapter)}
                className="group inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 transition hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
                title={chapter.title ?? `第 ${chapter.chapterNo} 话`}
              >
                <BookOpen className="size-3.5 text-slate-400 group-hover:text-indigo-500" />
                <span className="max-w-[220px] truncate">{chapter.title ?? `第 ${chapter.chapterNo} 话`}</span>
                {chapter.imageCount ? <span className="text-xs text-slate-400 group-hover:text-indigo-400">({chapter.imageCount})</span> : null}
                <ImageIcon className="size-3.5 text-slate-300 group-hover:text-indigo-400" />
              </button>
            ))}
          </div>
          <div className="mt-3 flex items-center justify-start gap-1 text-slate-500">
            <Button variant="outline" size="sm" disabled={result.page <= 1} onClick={() => void load(result.page - 1, orderDir)}>上一页</Button>
            <span className="px-2 text-sm">第 {result.page} / {totalPages} 页</span>
            <Button variant="outline" size="sm" disabled={result.page >= totalPages} onClick={() => void load(result.page + 1, orderDir)}>下一页</Button>
          </div>
        </>
      )}
    </div>
  )
}
