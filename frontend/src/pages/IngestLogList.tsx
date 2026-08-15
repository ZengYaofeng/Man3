import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, ScrollText } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { fetchIngestLogs, type IngestLog } from '@/lib/api'

const PAGE_SIZES = [10, 20, 50]

const STATUS: Record<number, { label: string; className: string }> = {
  1: { label: '进行中', className: 'bg-amber-100 text-amber-700' },
  2: { label: '完成', className: 'bg-emerald-100 text-emerald-700' },
  3: { label: '失败', className: 'bg-rose-100 text-rose-700' },
}

function formatTime(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

export default function IngestLogList() {
  const [logs, setLogs] = useState<IngestLog[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchIngestLogs(page, pageSize)
      setLogs(result.list)
      setTotal(result.total)
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    load()
  }, [load])

  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <ScrollText className="size-4" />
          记录每部漫画图片 URL 入库的执行结果、耗时与失败原因。
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={`mr-1 size-4 ${loading ? 'animate-spin' : ''}`} />
          刷新
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead>批次</TableHead>
                <TableHead>漫画</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="text-right">章节</TableHead>
                <TableHead className="text-right">待处理</TableHead>
                <TableHead className="text-right">成功 / 失败</TableHead>
                <TableHead className="text-right">图片 URL</TableHead>
                <TableHead className="text-right">耗时</TableHead>
                <TableHead>开始时间</TableHead>
                <TableHead>错误信息</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={10} className="h-32 text-center text-slate-400">加载中…</TableCell>
                </TableRow>
              ) : logs.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} className="h-32 text-center text-slate-400">暂无入库日志</TableCell>
                </TableRow>
              ) : (
                logs.map((log) => {
                  const status = STATUS[log.status] ?? STATUS[3]
                  return (
                    <TableRow key={log.id} className="hover:bg-slate-50">
                      <TableCell className="font-mono text-xs text-slate-500">{log.batchNo}</TableCell>
                      <TableCell className="min-w-[180px]">
                        <div className="font-medium text-slate-700">{log.bookName ?? '-'}</div>
                        <div className="text-xs text-slate-400">ID {log.bookId}</div>
                      </TableCell>
                      <TableCell><Badge className={status.className}>{status.label}</Badge></TableCell>
                      <TableCell className="text-right tabular-nums">{log.totalChapterCount}</TableCell>
                      <TableCell className="text-right tabular-nums">{log.pendingChapterCount}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        <span className="text-emerald-600">{log.successChapterCount}</span>
                        <span className="text-slate-300"> / </span>
                        <span className={log.failedChapterCount > 0 ? 'text-rose-600' : 'text-slate-500'}>{log.failedChapterCount}</span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{log.imageCount.toLocaleString()}</TableCell>
                      <TableCell className="text-right tabular-nums">{log.durationSeconds == null ? '-' : `${log.durationSeconds}s`}</TableCell>
                      <TableCell className="whitespace-nowrap text-slate-500">{formatTime(log.startTime)}</TableCell>
                      <TableCell className="max-w-[260px] truncate text-rose-600" title={log.errorMessage ?? ''}>{log.errorMessage ?? '-'}</TableCell>
                    </TableRow>
                  )
                })
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
        <div className="flex items-center gap-2">
          共 <span className="font-medium text-slate-700">{total}</span> 条
          <select
            value={pageSize}
            onChange={(event) => {
              setPageSize(Number(event.target.value))
              setPage(1)
            }}
            className="h-8 rounded-md border border-slate-200 bg-white px-2"
          >
            {PAGE_SIZES.map((size) => <option key={size} value={size}>{size} / 页</option>)}
          </select>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>上一页</Button>
          <span>{page} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>下一页</Button>
        </div>
      </div>
    </div>
  )
}
