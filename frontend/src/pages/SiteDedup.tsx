import { useEffect, useState } from 'react'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Search, Repeat, Globe, Info, CircleSlash, BookOpenCheck } from 'lucide-react'
import { fetchAllSites, runDedup } from '@/lib/siteApi'
import type { SiteSource, DedupResult } from '@/types/siteSource'

export default function SiteDedup() {
  const [sites, setSites] = useState<SiteSource[]>([])
  const [siteId, setSiteId] = useState<string>('')
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<DedupResult[] | null>(null)

  useEffect(() => {
    fetchAllSites().then((list) => {
      setSites(list)
      if (list.length) setSiteId(String(list[0].id))
    })
  }, [])

  const handleRun = async () => {
    if (!siteId) return
    setRunning(true)
    setResult(null)
    const res = await runDedup(Number(siteId))
    setResult(res)
    setRunning(false)
  }

  const selectedSite = sites.find((s) => String(s.id) === siteId)

  return (
    <div className="space-y-5">
      {/* 操作区 */}
      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
              <Globe className="size-4 text-indigo-400" />
              选择站点
            </label>
            <Select value={siteId} onValueChange={setSiteId}>
              <SelectTrigger className="w-[240px]">
                <SelectValue placeholder="请选择站点" />
              </SelectTrigger>
              <SelectContent>
                {sites.map((s) => (
                  <SelectItem key={s.id} value={String(s.id)}>
                    {s.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button onClick={handleRun} disabled={running || !siteId}>
            <Repeat className="mr-1.5 size-4" />
            {running ? '查重中…' : '执行查重'}
          </Button>

          <div className="ml-auto flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
            <Info className="size-4 text-sky-500" />
            查重范围：当前漫画表中「未匹配」的漫画，按所选站点逐一比对名称
          </div>
        </div>
        {selectedSite && (
          <p className="mt-3 text-xs text-slate-400">
            所选站点：<span className="font-medium text-slate-600">{selectedSite.name}</span>
            （{selectedSite.url}）
          </p>
        )}
      </div>

      {/* 结果区 */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
            <BookOpenCheck className="size-4 text-indigo-400" />
            未匹配漫画列表
          </div>
          {result && (
            <Badge variant="secondary">共 {result.length} 条</Badge>
          )}
        </div>

        {result === null ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-slate-400">
            <CircleSlash className="size-8" />
            <p className="text-sm">尚未执行查重，请选择站点后点击「执行查重」</p>
          </div>
        ) : result.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-slate-400">
            <Info className="size-8 text-sky-500" />
            <p className="text-sm">查重功能开发中，暂未返回匹配结果</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead>漫画ID</TableHead>
                <TableHead>漫画名称</TableHead>
                <TableHead>来源站点</TableHead>
                <TableHead>已匹配站点</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {result.map((r) => (
                <TableRow key={r.bookId}>
                  <TableCell className="font-medium text-slate-700">{r.bookId}</TableCell>
                  <TableCell className="text-slate-800">{r.bookName}</TableCell>
                  <TableCell className="text-slate-500">{r.sourceSite}</TableCell>
                  <TableCell className="text-slate-500">{r.matchedSiteName ?? '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
}
