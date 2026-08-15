import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft, Loader2, X } from 'lucide-react'
import { fetchImageLog, fetchImageProgress, type CrawlLogEntry } from '@/lib/api'

/**
 * 入库详情 / 爬虫日志页(新页签打开)。
 * 控制台风格实时打印爬虫日志: 正在爬取的章节完整名称、爬取完成的图片数量等, 自动滚动到底部。
 * 路由: /crawllog/:bookId
 */
export default function CrawlLogPage() {
  const { bookId } = useParams<{ bookId: string }>()
  const navigate = useNavigate()
  const bookIdNum = Number(bookId)

  const [logs, setLogs] = useState<CrawlLogEntry[]>([])
  const [running, setRunning] = useState(false)
  const [progress, setProgress] = useState<{
    total: number
    processed: number
    success: number
    fail: number
    currentChapter: string
    progress: number
  } | null>(null)
  const [autoScroll, setAutoScroll] = useState(true)
  const bottomRef = useRef<HTMLDivElement>(null)

  // 轮询日志与进度
  useEffect(() => {
    if (!bookIdNum) return
    let stopped = false
    const tick = async () => {
      try {
        const [logRes, progRes] = await Promise.all([
          fetchImageLog(bookIdNum),
          fetchImageProgress(bookIdNum),
        ])
        if (stopped) return
        setLogs(logRes.logs)
        setRunning(logRes.running)
        setProgress({
          total: progRes.total,
          processed: progRes.processed,
          success: progRes.success,
          fail: progRes.fail,
          currentChapter: progRes.currentChapter,
          progress: progRes.progress,
        })
      } catch {
        /* 忽略轮询错误 */
      }
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => {
      stopped = true
      clearInterval(timer)
    }
  }, [bookIdNum])

  // 自动滚动到底部
  useEffect(() => {
    if (autoScroll && bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [logs, autoScroll])

  const levelColor = (lvl: string) => {
    switch (lvl) {
      case 'ERROR':
        return 'text-red-400'
      case 'WARN':
        return 'text-amber-400'
      case 'INFO':
        return 'text-emerald-300'
      default:
        return 'text-slate-300'
    }
  }

  const fmtTime = (ts: number) =>
    new Date(ts).toLocaleTimeString('zh-CN', { hour12: false }) +
    '.' + String(ts % 1000).padStart(3, '0')

  return (
    <div className="flex h-screen flex-col bg-zinc-950 font-mono text-xs text-slate-200">
      {/* 顶部栏 */}
      <header className="flex items-center gap-2 border-b border-zinc-800 bg-zinc-900 px-4 py-2">
        <button
          type="button"
          onClick={() => navigate('/comics')}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-slate-300 hover:bg-zinc-800"
          title="返回漫画列表"
        >
          <X className="size-4" /> 关闭
        </button>
        <div className="flex-1 truncate text-sm font-semibold text-zinc-100">
          入库详情 · 漫画 #{bookIdNum}
        </div>
        <div className="flex items-center gap-2">
          {running ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-900/60 px-2 py-0.5 text-emerald-300">
              <Loader2 className="size-3 animate-spin" /> 爬取中
            </span>
          ) : (
            <span className="rounded-full bg-zinc-700 px-2 py-0.5 text-zinc-300">空闲</span>
          )}
          <label className="flex items-center gap-1 text-slate-400">
            <input
              type="checkbox"
              checked={autoScroll}
              onChange={(e) => setAutoScroll(e.target.checked)}
              className="accent-emerald-500"
            />
            自动滚动
          </label>
        </div>
      </header>

      {/* 进度摘要 */}
      {progress && (
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 border-b border-zinc-800 bg-zinc-900/60 px-4 py-2 text-[11px] sm:grid-cols-4">
          <div>
            进度：
            <span className="text-emerald-300">
              {progress.processed}/{progress.total}
            </span>{' '}
            ({progress.progress}%)
          </div>
          <div>
            成功：<span className="text-emerald-300">{progress.success}</span> 失败：
            <span className="text-red-400">{progress.fail}</span>
          </div>
          <div className="col-span-2 truncate text-slate-400">
            当前：{progress.currentChapter || '—'}
          </div>
          <div className="col-span-4 h-1.5 w-full overflow-hidden rounded-full bg-zinc-800">
            <div
              className="h-full rounded-full bg-emerald-500 transition-all"
              style={{ width: `${progress.progress}%` }}
            />
          </div>
        </div>
      )}

      {/* 日志控制台 */}
      <main className="flex-1 overflow-y-auto px-4 py-2 leading-relaxed">
        {logs.length === 0 ? (
          <div className="flex h-full items-center justify-center text-slate-600">
            {running ? '等待日志输出…' : '暂无日志。点击漫画列表的「入库」开始抓取。'}
          </div>
        ) : (
          logs.map((l, i) => (
            <div key={i} className="flex gap-2 whitespace-pre-wrap break-words">
              <span className="shrink-0 text-zinc-600">{fmtTime(l.ts)}</span>
              <span className={`shrink-0 font-bold ${levelColor(l.level)}`}>
                [{l.level}]
              </span>
              <span className={levelColor(l.level)}>{l.msg}</span>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </main>

      <footer className="border-t border-zinc-800 bg-zinc-900 px-4 py-1.5 text-[11px] text-zinc-500">
        日志实时刷新自后端爬虫 /api/crawl/image/log · 每 1 秒
      </footer>
    </div>
  )
}
