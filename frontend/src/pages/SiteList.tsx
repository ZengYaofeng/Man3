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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Search,
  Plus,
  MoreHorizontal,
  Pencil,
  Trash2,
  Power,
  PowerOff,
  Globe,
  Link2,
  Image as ImageIcon,
  FileText,
} from 'lucide-react'
import SiteFormDialog from '@/components/site/SiteFormDialog'
import {
  fetchSites,
  deleteSite,
  setSiteEnabled,
} from '@/lib/siteApi'
import type { SiteSource, SiteSourcePayload } from '@/types/siteSource'

// 工具: 固定宽度 + 单行截断 + hover 显示全文
function Truncated({ text, className }: { text?: string | null; className?: string }) {
  const value = text ?? '-'
  return (
    <div className={`truncate ${className ?? ''}`} title={value}>
      {value}
    </div>
  )
}

const PAGE_SIZES = [10, 20, 50]

export default function SiteList() {
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [sites, setSites] = useState<SiteSource[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<SiteSource | null>(null)
  const [toDelete, setToDelete] = useState<SiteSource | null>(null)
  const [isMock, setIsMock] = useState(false)

  const load = async () => {
    setLoading(true)
    const res = await fetchSites(keyword, page, pageSize)
    setSites(res.data)
    setTotal(res.total)
    setIsMock(!!res.mock)
    setLoading(false)
  }

  useEffect(() => {
    const t = setTimeout(load, 200)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, page, pageSize])

  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  const openCreate = () => {
    setEditing(null)
    setDialogOpen(true)
  }
  const openEdit = (site: SiteSource) => {
    setEditing(site)
    setDialogOpen(true)
  }
  const handleSubmit = async (payload: SiteSourcePayload & { id?: number }) => {
    const { saveSite } = await import('@/lib/siteApi')
    await saveSite(payload)
    setDialogOpen(false)
    await load()
  }
  const confirmDelete = async () => {
    if (!toDelete) return
    await deleteSite(toDelete.id)
    setToDelete(null)
    await load()
  }
  const toggleEnabled = async (site: SiteSource) => {
    await setSiteEnabled(site.id, !site.isEnabled)
    await load()
  }

  return (
    <div className="space-y-4">
      {isMock && (
        <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-700">
          <span className="size-2 rounded-full bg-amber-500" />
          后端接口不可达，当前展示的是前端演示数据（新增/编辑/删除不会真正保存）
        </div>
      )}
      {/* 工具栏 */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-sm">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="搜索网站名称 / 网址 / 备注"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(1)
            }}
            className="pl-9"
          />
        </div>
        <Button className="ml-auto" onClick={openCreate}>
          <Plus className="mr-1.5 size-4" />
          新增站点
        </Button>
      </div>

      {/* 表格 */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableHead className="w-16">ID</TableHead>
                <TableHead className="min-w-[120px]">网站名称</TableHead>
                <TableHead className="w-[180px]">网址</TableHead>
                <TableHead className="w-[200px]">图片规则</TableHead>
                <TableHead className="w-[210px]">漫画详情网址</TableHead>
                <TableHead className="w-[210px]">漫画内容网址</TableHead>
                <TableHead className="w-[88px]">状态</TableHead>
                <TableHead className="w-20 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={8} className="h-32 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              ) : sites.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="h-32 text-center text-slate-400">
                    暂无站点，点击右上角「新增站点」
                  </TableCell>
                </TableRow>
              ) : (
                sites.map((s) => (
                  <TableRow key={s.id} className="hover:bg-slate-50">
                    <TableCell className="font-medium text-slate-700">{s.id}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2 font-medium text-slate-800">
                        <Globe className="size-4 shrink-0 text-indigo-400" />
                        <Truncated text={s.name} className="max-w-[120px]" />
                      </div>
                    </TableCell>
                    <TableCell>
                      <a
                        href={s.url}
                        target="_blank"
                        rel="noreferrer"
                        className="flex items-center gap-1 text-indigo-600 hover:underline"
                        title={s.url}
                      >
                        <Link2 className="size-3.5 shrink-0" />
                        <span className="truncate">{s.url}</span>
                      </a>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-start gap-1.5 text-slate-600" title={s.imageRule}>
                        <ImageIcon className="mt-0.5 size-3.5 shrink-0 text-slate-400" />
                        <Truncated text={s.imageRule} className="max-w-[180px]" />
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-start gap-1.5 font-mono text-xs text-slate-600" title={s.detailUrl}>
                        <FileText className="mt-0.5 size-3.5 shrink-0 text-slate-400" />
                        <Truncated text={s.detailUrl} className="max-w-[190px]" />
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="block truncate font-mono text-xs text-slate-500" title={s.contentUrl}>
                        {s.contentUrl}
                      </span>
                    </TableCell>
                    <TableCell>
                      {s.isEnabled ? (
                        <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">
                          已启用
                        </Badge>
                      ) : (
                        <Badge variant="secondary">已停用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8">
                            <MoreHorizontal className="size-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-36">
                          <DropdownMenuItem onClick={() => openEdit(s)}>
                            <Pencil className="mr-2 size-4" />
                            编辑
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => toggleEnabled(s)}>
                            {s.isEnabled ? (
                              <>
                                <PowerOff className="mr-2 size-4" />
                                停用
                              </>
                            ) : (
                              <>
                                <Power className="mr-2 size-4" />
                                启用
                              </>
                            )}
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={() => setToDelete(s)}
                          >
                            <Trash2 className="mr-2 size-4" />
                            删除
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
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

      <SiteFormDialog
        open={dialogOpen}
        site={editing}
        onOpenChange={setDialogOpen}
        onSubmit={handleSubmit}
      />

      <AlertDialog open={!!toDelete} onOpenChange={(o) => !o && setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除站点？</AlertDialogTitle>
            <AlertDialogDescription>
              即将删除「{toDelete?.name}」(<span className="font-mono">{toDelete?.url}</span>)。
              此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDelete}
              className="bg-rose-600 hover:bg-rose-700"
            >
              确认删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
