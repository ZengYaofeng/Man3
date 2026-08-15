import { useEffect, useState } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ChevronDown, ChevronRight, ClipboardList, Loader2 } from 'lucide-react'
import {
  fetchInventoryRecords,
  fetchInventoryRecordBooks,
  type InventoryRecord,
  type InventoryRecordBook,
} from '@/lib/api'

const PAGE_SIZES = [10, 20, 50]

const STATUS_BADGE: Record<number, { text: string; cls: string }> = {
  1: { text: '进行中', cls: 'bg-amber-100 text-amber-700' },
  2: { text: '已完成', cls: 'bg-emerald-100 text-emerald-700' },
  3: { text: '失败', cls: 'bg-rose-100 text-rose-700' },
}

function fmt(ts: string | null | undefined): string {
  if (!ts) return '-'
  return ts.replace('T', ' ').slice(0, 19)
}

export default function InventoryHistory() {
  const [loading, setLoading] = useState(false)
  const [records, setRecords] = useState<InventoryRecord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  // 展开状态: 已展开的批次ID -> 该批次漫画明细
  const [expanded, setExpanded] = useState<Record<number, InventoryRecordBook[] | 'loading'>>({})

  const load = async () => {
    setLoading(true)
    try {
      const list = await fetchInventoryRecords()
      setRecords(list)
      setTotal(list.length)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const pageRows = records.slice((page - 1) * pageSize, page * pageSize)

  const toggle = async (rec: InventoryRecord) => {
    if (expanded[rec.id]) {
      setExpanded((m) => {
        const n = { ...m }
        delete n[rec.id]
        return n
      })
      return
    }
    setExpanded((m) => ({ ...m, [rec.id]: 'loading' }))
    try {
      const books = await fetchInventoryRecordBooks(rec.id)
      setExpanded((m) => ({ ...m, [rec.id]: books }))
    } catch {
      setExpanded((m) => ({ ...m, [rec.id]: [] }))
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <ClipboardList className="size-4" />
        每次「入库盘点」会生成一条记录，展开可查看本次新判定为入库完成的漫画明细
      </div>

      {/* 表格 */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead className="w-12"></TableHead>
                <TableHead className="w-20">批次ID</TableHead>
                <TableHead className="w-[160px]">批次号</TableHead>
                <TableHead className="w-[160px]">开始时间</TableHead>
                <TableHead className="w-[160px]">结束时间</TableHead>
                <TableHead className="w-20">状态</TableHead>
                <TableHead className="w-20 text-right">扫描本数</TableHead>
                <TableHead className="w-24 text-right">新增完成</TableHead>
                <TableHead className="w-24 text-right">更新章节</TableHead>
                <TableHead className="w-24 text-right">更新漫画</TableHead>
                <TableHead className="min-w-[200px]">备注</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={11} className="h-32 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              ) : pageRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={11} className="h-32 text-center text-slate-400">
                    暂无盘点记录，请到「漫画列表」点击「入库盘点」
                  </TableCell>
                </TableRow>
              ) : (
                pageRows.map((rec) => {
                  const books = expanded[rec.id]
                  const isOpen = !!books
                  return (
                    <>
                      <TableRow key={rec.id} className="hover:bg-slate-50">
                        <TableCell>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => toggle(rec)}
                            title={isOpen ? '收起' : '展开详情'}
                          >
                            {isOpen ? (
                              <ChevronDown className="size-4" />
                            ) : (
                              <ChevronRight className="size-4" />
                            )}
                          </Button>
                        </TableCell>
                        <TableCell className="font-medium text-slate-700">{rec.id}</TableCell>
                        <TableCell className="font-mono text-xs text-slate-600">{rec.batchNo}</TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">{fmt(rec.startTime)}</TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">{fmt(rec.endTime)}</TableCell>
                        <TableCell>
                          <Badge className={(STATUS_BADGE[rec.status] ?? STATUS_BADGE[1]).cls}>
                            {(STATUS_BADGE[rec.status] ?? STATUS_BADGE[1]).text}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-slate-600">{rec.scannedBookCount}</TableCell>
                        <TableCell className="text-right tabular-nums font-medium text-emerald-600">{rec.doneBookCount}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-600">{rec.updatedChapterCount}</TableCell>
                        <TableCell className="text-right tabular-nums text-slate-600">{rec.updatedBookCount}</TableCell>
                        <TableCell className="max-w-[200px] truncate text-slate-500" title={rec.remark ?? ''}>
                          {rec.remark ?? '-'}
                        </TableCell>
                      </TableRow>

                      {/* 下拉展开的漫画明细 */}
                      {isOpen && (
                        <TableRow key={`${rec.id}-detail`} className="bg-slate-50/60">
                          <TableCell colSpan={11} className="p-0">
                            <div className="px-4 py-3">
                              <div className="mb-2 text-sm font-medium text-slate-600">
                                本次新增入库完成漫画（{Array.isArray(books) ? books.length : 0} 本）
                              </div>
                              {books === 'loading' ? (
                                <div className="flex items-center gap-2 py-4 text-sm text-slate-400">
                                  <Loader2 className="size-4 animate-spin" />
                                  加载明细中…
                                </div>
                              ) : books && books.length > 0 ? (
                                <div className="max-h-80 overflow-auto rounded-lg border border-slate-200">
                                  <Table>
                                    <TableHeader>
                                      <TableRow className="hover:bg-transparent">
                                        <TableHead className="w-16">漫画ID</TableHead>
                                        <TableHead>漫画名称</TableHead>
                                        <TableHead className="w-32">来源ID</TableHead>
                                        <TableHead className="w-20 text-right">章节数</TableHead>
                                        <TableHead className="w-24 text-right">图片数</TableHead>
                                      </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                      {books.map((b) => (
                                        <TableRow key={b.id} className="hover:bg-white">
                                          <TableCell className="font-medium text-slate-700">{b.bookId}</TableCell>
                                          <TableCell className="max-w-[260px] truncate text-slate-700" title={b.bookName ?? ''}>
                                            {b.bookName ?? '-'}
                                          </TableCell>
                                          <TableCell className="font-mono text-xs text-slate-500">{b.sourceBookId ?? '-'}</TableCell>
                                          <TableCell className="text-right tabular-nums text-slate-600">{b.chapterCount}</TableCell>
                                          <TableCell className="text-right tabular-nums text-slate-600">{b.imageCount}</TableCell>
                                        </TableRow>
                                      ))}
                                    </TableBody>
                                  </Table>
                                </div>
                              ) : (
                                <div className="py-3 text-sm text-slate-400">本次无新增入库完成漫画</div>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      )}
                    </>
                  )
                })
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* 分页 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm text-slate-500">
          共 <span className="font-medium text-slate-700">{total}</span> 条
          <select
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value))
              setPage(1)
            }}
            className="h-8 rounded-md border border-slate-200 bg-white px-2 text-sm"
          >
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s} / 页
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <span className="px-2 text-sm text-slate-500">
            第 {page} / {totalPages} 页
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </Button>
        </div>
      </div>
    </div>
  )
}
