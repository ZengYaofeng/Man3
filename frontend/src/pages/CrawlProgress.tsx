import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Activity,
  BookOpen,
  CheckCircle2,
  Loader2,
  Play,
  RefreshCw,
  XCircle,
  Clock,
} from 'lucide-react'
import { fetchCrawlStatus, startDetailCrawl, type CrawlStatus } from '@/lib/api'

const EMPTY: CrawlStatus = {
  bookCount: 0,
  chapterCount: 0,
  bookPageCount: 0,
  detail: {
    running: false,
    total: 0,
    notCrawled: 0,
    chapterDone: 0,
    failed: 0,
    done: 0,
    progress: 0,
    lastCrawlTime: null,
  },
}

const POLL_INTERVAL = 3000

export default function CrawlProgress() {
  const [status, setStatus] = useState<CrawlStatus>(EMPTY)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)

  const load = useCallback(async () => {
    try {
      const data = await fetchCrawlStatus()
      setStatus(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    setLoading(true)
    load()
    timerRef.current = window.setInterval(load, POLL_INTERVAL)
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current)
    }
  }, [load])

  const handleStart = async () => {
    if (status.detail.running) return
    setStarting(true)
    try {
      const msg = await startDetailCrawl()
      setToast(msg)
      // 立即刷新一次，让“运行中”状态尽快反映
      await load()
      setTimeout(() => setToast(null), 4000)
    } catch (e) {
      setToast(e instanceof Error ? e.message : '启动失败')
    } finally {
      setStarting(false)
    }
  }

  const { detail } = status
  const pending = detail.notCrawled + detail.failed

  const cards = [
    {
      label: '漫画总数',
      value: status.bookCount,
      icon: BookOpen,
      color: 'bg-indigo-500',
    },
    {
      label: '章节总数',
      value: status.chapterCount,
      icon: Activity,
      color: 'bg-sky-500',
    },
    {
      label: '已补全章节',
      value: detail.chapterDone,
      icon: CheckCircle2,
      color: 'bg-emerald-500',
    },
    {
      label: '待处理',
      value: pending,
      icon: Loader2,
      color: 'bg-amber-500',
    },
    {
      label: '失败',
      value: detail.failed,
      icon: XCircle,
      color: 'bg-rose-500',
    },
  ]

  return (
    <div className="space-y-6">
      {/* 顶部标题 + 操作 */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-slate-800">爬取进度</h2>
          <p className="text-sm text-slate-400">
            实时展示详情爬虫运行状况，每 {POLL_INTERVAL / 1000} 秒自动刷新
          </p>
        </div>
        <div className="flex items-center gap-3">
          {detail.running ? (
            <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-medium text-emerald-700">
              <span className="relative flex size-2.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
              </span>
              爬虫运行中
            </span>
          ) : (
            <span className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-sm font-medium text-slate-500">
              <span className="size-2.5 rounded-full bg-slate-400" />
              空闲
            </span>
          )}
          <button
            onClick={handleStart}
            disabled={detail.running || starting}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {starting ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Play className="size-4" />
            )}
            启动详情爬虫
          </button>
          <button
            onClick={load}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50"
            title="手动刷新"
          >
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {toast && (
        <div className="rounded-lg border border-indigo-200 bg-indigo-50 px-4 py-2.5 text-sm text-indigo-700">
          {toast}
        </div>
      )}
      {error && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm text-rose-700">
          进度接口连接失败：{error}（后端未启动或代理未生效）
        </div>
      )}

      {/* 统计卡片 */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        {cards.map((c) => {
          const Icon = c.icon
          return (
            <div
              key={c.label}
              className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
            >
              <div
                className={`mb-3 inline-flex size-10 items-center justify-center rounded-lg ${c.color} text-white`}
              >
                <Icon className="size-5" />
              </div>
              <div className="text-2xl font-bold text-slate-800">
                {c.value.toLocaleString()}
              </div>
              <div className="mt-0.5 text-sm text-slate-400">{c.label}</div>
            </div>
          )
        })}
      </div>

      {/* 主进度条 */}
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="size-5 text-indigo-500" />
            <span className="font-medium text-slate-700">详情爬取总进度</span>
          </div>
          <span className="text-sm font-semibold text-indigo-600">
            {detail.progress}%
          </span>
        </div>
        <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-emerald-500 transition-all duration-700"
            style={{ width: `${detail.progress}%` }}
          />
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-slate-500">
          <span>
            已完成 <b className="text-slate-700">{detail.chapterDone}</b> /{' '}
            {detail.total}
          </span>
          <span className="inline-flex items-center gap-1">
            <Clock className="size-3.5 text-slate-400" />
            最近爬取：
            <b className="text-slate-700">{detail.lastCrawlTime ?? '—'}</b>
          </span>
        </div>
      </div>

      {/* 状态分布 */}
      <div className="grid gap-4 lg:grid-cols-3">
        <DistItem
          label="未爬取"
          value={detail.notCrawled}
          color="bg-slate-400"
          text="text-slate-500"
        />
        <DistItem
          label="章节已补全"
          value={detail.chapterDone}
          color="bg-emerald-500"
          text="text-emerald-600"
        />
        <DistItem
          label="失败待重试"
          value={detail.failed}
          color="bg-rose-500"
          text="text-rose-600"
        />
      </div>
    </div>
  )
}

function DistItem({
  label,
  value,
  color,
  text,
}: {
  label: string
  value: number
  color: string
  text: string
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center gap-2">
        <span className={`size-2.5 rounded-full ${color}`} />
        <span className="text-sm text-slate-400">{label}</span>
      </div>
      <div className={`mt-2 text-3xl font-bold ${text}`}>{value.toLocaleString()}</div>
    </div>
  )
}
