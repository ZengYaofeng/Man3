import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Loader2,
  ImageOff,
  List,
  X,
  BookOpen,
} from 'lucide-react'
import { fetchBookPages, fetchChapters, type BookPage, type Chapter } from '@/lib/api'

const PAGES_PER_BATCH = 10
const CHAPTER_PAGE_SIZE = 20

/**
 * 漫画浏览页(仿漫画站阅读页, 普通网页风格, 新页签打开)。
 * 路由: /reader/:bookId/:chapterId
 * 顶部面包屑 + 标题 + 上一章/下一章 + 章节目录; 竖向滚动图片流 + 底部翻页。
 */
export default function MangaReaderPage() {
  const { bookId, chapterId } = useParams<{ bookId: string; chapterId: string }>()
  const navigate = useNavigate()

  const [chapters, setChapters] = useState<Chapter[]>([])
  const [currentChapterId, setCurrentChapterId] = useState<number | null>(null)
  const [pages, setPages] = useState<BookPage[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loadingChapter, setLoadingChapter] = useState(true)
  const [loadingImg, setLoadingImg] = useState(false)
  const [chapterError, setChapterError] = useState<string | null>(null)
  const [showToc, setShowToc] = useState(false)

  const bookIdNum = Number(bookId)
  const chapterIdNum = Number(chapterId)

  const currentIndex = chapters.findIndex((c) => c.id === currentChapterId)
  const current = currentIndex >= 0 ? chapters[currentIndex] : null

  // 加载章节列表(按正序)
  useEffect(() => {
    let cancelled = false
    if (!bookIdNum) {
      setChapterError('缺少漫画参数')
      setLoadingChapter(false)
      return
    }
    setLoadingChapter(true)
    setChapterError(null)
    const loadAll = async () => {
      const first = await fetchChapters(bookIdNum, 1, CHAPTER_PAGE_SIZE, 'asc')
      let all = [...first.list]
      const pages = Math.max(1, Math.ceil(first.total / CHAPTER_PAGE_SIZE))
      for (let p = 2; p <= pages; p++) {
        const r = await fetchChapters(bookIdNum, p, CHAPTER_PAGE_SIZE, 'asc')
        all = all.concat(r.list)
      }
      return all
    }
    loadAll()
      .then((all) => {
        if (cancelled) return
        setChapters(all)
        setLoadingChapter(false)
        // 若路由未指章节, 默认打开第一章
        if (!chapterIdNum && all.length > 0) {
          navigate(`/reader/${bookIdNum}/${all[0].id}`, { replace: true })
        } else if (chapterIdNum) {
          setCurrentChapterId(chapterIdNum)
        }
      })
      .catch((e) => {
        if (cancelled) return
        setChapterError((e as Error).message || '章节加载失败')
        setLoadingChapter(false)
      })
    return () => {
      cancelled = true
    }
  }, [bookIdNum, chapterIdNum, navigate])

  const loadPages = useCallback(async (chId: number, p: number) => {
    setLoadingImg(true)
    try {
      const res = await fetchBookPages(chId, p, PAGES_PER_BATCH)
      setPages(res.list)
      setTotal(res.total)
      setPage(res.page)
      window.scrollTo({ top: 0 })
    } finally {
      setLoadingImg(false)
    }
  }, [])

  // 切换章节时重置图片
  useEffect(() => {
    if (currentChapterId != null) loadPages(currentChapterId, 1)
    else {
      setPages([])
      setTotal(0)
    }
  }, [currentChapterId, loadPages])

  const goChapter = (delta: number) => {
    const ni = currentIndex + delta
    if (ni >= 0 && ni < chapters.length) {
      const c = chapters[ni]
      setCurrentChapterId(c.id)
      navigate(`/reader/${bookIdNum}/${c.id}`, { replace: true })
    }
  }

  const imgTotalPages = Math.max(1, Math.ceil(total / PAGES_PER_BATCH))
  const firstImgNo = (page - 1) * PAGES_PER_BATCH + 1
  const lastImgNo = Math.min(page * PAGES_PER_BATCH, total)

  // 键盘导航
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      if (e.key === 'ArrowLeft') {
        if (page > 1) loadPages(currentChapterId!, page - 1)
        else goChapter(-1)
      } else if (e.key === 'ArrowRight') {
        if (page < imgTotalPages) loadPages(currentChapterId!, page + 1)
        else goChapter(1)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [page, imgTotalPages, currentChapterId, chapters, currentIndex, loadPages])

  const back = () => navigate('/comics')

  const chapterTitle = current?.title ?? (current ? `第${current.chapterNo}话` : '')

  if (loadingChapter) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-400">
        <Loader2 className="size-6 animate-spin" />
        <span className="ml-2">加载章节中…</span>
      </div>
    )
  }

  if (chapterError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 text-slate-500">
        <span>{chapterError}</span>
        <button
          type="button"
          onClick={back}
          className="rounded-lg border border-slate-300 px-4 py-1.5 text-sm hover:bg-slate-100"
        >
          返回列表
        </button>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      {/* 顶部工具条: 面包屑 + 章节切换 + 目录 */}
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center gap-2 px-4 py-2">
          <button
            type="button"
            onClick={back}
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-slate-500 hover:bg-slate-100"
            title="返回漫画列表"
          >
            <X className="size-4" /> 关闭
          </button>
          <button
            type="button"
            onClick={() => setShowToc((v) => !v)}
            className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-indigo-600 hover:bg-indigo-50"
            title="章节目录"
          >
            <List className="size-4" /> 目录
          </button>

          <nav className="mx-1 hidden min-w-0 flex-1 items-center gap-1 truncate text-sm text-slate-400 sm:flex">
            <button type="button" onClick={back} className="hover:text-slate-700">
              漫画列表
            </button>
            <ChevronRight className="size-3.5" />
            <span className="truncate text-slate-600">{chapterTitle}</span>
          </nav>

          <div className="flex items-center gap-1">
            <button
              type="button"
              disabled={currentIndex <= 0}
              onClick={() => goChapter(-1)}
              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1 text-sm disabled:opacity-40 hover:bg-slate-100"
            >
              <ChevronsLeft className="size-4" /> 上一章
            </button>
            <button
              type="button"
              disabled={currentIndex >= chapters.length - 1}
              onClick={() => goChapter(1)}
              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1 text-sm disabled:opacity-40 hover:bg-slate-100"
            >
              下一章 <ChevronsRight className="size-4" />
            </button>
          </div>
        </div>
      </header>

      {/* 标题区 */}
      <div className="mx-auto max-w-5xl px-4 pt-4">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-slate-800">
          <BookOpen className="size-5 text-indigo-500" />
          {chapterTitle}
        </h1>
        <div className="mt-1 text-sm text-slate-400">
          图片 {firstImgNo}-{lastImgNo} / {total}
        </div>
      </div>

      {/* 章节目录(可折叠) */}
      {showToc && (
        <div className="mx-auto mt-3 max-w-5xl px-4">
          <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-medium text-slate-600">
                目录（共 {chapters.length} 话）
              </span>
              <button
                type="button"
                onClick={() => setShowToc(false)}
                className="text-xs text-slate-400 hover:text-slate-600"
              >
                收起
              </button>
            </div>
            <div className="flex flex-wrap gap-2">
              {chapters.map((c, i) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => {
                    setCurrentChapterId(c.id)
                    navigate(`/reader/${bookIdNum}/${c.id}`, { replace: true })
                    setShowToc(false)
                  }}
                  className={`rounded-lg border px-3 py-1.5 text-sm transition ${
                    i === currentIndex
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                      : 'border-slate-200 bg-white text-slate-600 hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700'
                  }`}
                  title={c.title ?? `第${c.chapterNo}话`}
                >
                  {c.title ?? `第${c.chapterNo}话`}
                  {c.imageCount ? (
                    <span className="ml-1 text-xs text-slate-400">({c.imageCount})</span>
                  ) : null}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 图片流 */}
      <main className="mx-auto max-w-3xl px-4 py-4">
        {loadingImg ? (
          <div className="flex h-64 items-center justify-center text-slate-400">
            <Loader2 className="size-6 animate-spin" />
          </div>
        ) : pages.length === 0 ? (
          <div className="flex h-64 flex-col items-center justify-center gap-2 text-slate-400">
            <ImageOff className="size-10" />
            <span>本章暂无图片数据</span>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-0">
            {pages.map((p) => (
              <figure key={p.id} className="m-0 w-full">
                <img
                  src={p.imgUrl}
                  alt={`第${p.pageNo}页`}
                  loading="lazy"
                  className="mx-auto block w-full bg-white"
                  referrerPolicy="no-referrer"
                />
              </figure>
            ))}
          </div>
        )}
      </main>

      {/* 底部翻页 */}
      <footer className="sticky bottom-0 z-10 border-t border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-center gap-3 px-4 py-2">
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => currentChapterId != null && loadPages(currentChapterId, page - 1)}
            className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-100"
          >
            <ChevronLeft className="size-4" /> 上一页
          </button>
          <span className="text-sm text-slate-500">
            {page} / {imgTotalPages}
          </span>
          <button
            type="button"
            disabled={page >= imgTotalPages}
            onClick={() => currentChapterId != null && loadPages(currentChapterId, page + 1)}
            className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40 hover:bg-slate-100"
          >
            下一页 <ChevronRight className="size-4" />
          </button>
        </div>
      </footer>
    </div>
  )
}
