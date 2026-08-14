import { useEffect, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { SiteSource, SiteSourcePayload } from '@/types/siteSource'

interface Props {
  open: boolean
  /** 编辑时传入已有站点，新增时为 null */
  site: SiteSource | null
  onOpenChange: (open: boolean) => void
  onSubmit: (payload: SiteSourcePayload & { id?: number }) => void
}

// 表单内部状态: enabled 用布尔, 提交时再转为后端需要的数字
interface FormState {
  name: string
  url: string
  imageRule: string
  detailUrl: string
  contentUrl: string
  enabled: boolean
  remark: string
}

const EMPTY: FormState = {
  name: '',
  url: '',
  imageRule: '',
  detailUrl: '',
  contentUrl: '',
  enabled: true,
  remark: '',
}

export default function SiteFormDialog({ open, site, onOpenChange, onSubmit }: Props) {
  const [form, setForm] = useState<FormState>(EMPTY)

  useEffect(() => {
    if (open) {
      setForm(
        site
          ? {
              name: site.name,
              url: site.url,
              imageRule: site.imageRule,
              detailUrl: site.detailUrl,
              contentUrl: site.contentUrl,
              enabled: site.isEnabled ?? site.enabled === 1,
              remark: site.remark ?? '',
            }
          : EMPTY,
      )
    }
  }, [open, site])

  const set = (key: keyof FormState, value: string | boolean) =>
    setForm((f) => ({ ...f, [key]: value }))

  const valid = form.name.trim() && form.url.trim()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{site ? '编辑站点来源' : '新增站点来源'}</DialogTitle>
          <DialogDescription>
            配置站点的基本信息与抓取规则，带 <span className="font-medium">{'{id}'}</span> /{' '}
            <span className="font-medium">{'{bookId}'}</span> /{' '}
            <span className="font-medium">{'{chapterId}'}</span> 占位符的网址模板。
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-1">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="name">
                网站名称 <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => set('name', e.target.value)}
                placeholder="如 爱看漫画"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="url">
                网址 <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="url"
                value={form.url}
                onChange={(e) => set('url', e.target.value)}
                placeholder="https://www.ikanmh.top"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="imageRule">图片规则</Label>
            <Textarea
              id="imageRule"
              value={form.imageRule}
              onChange={(e) => set('imageRule', e.target.value)}
              placeholder="描述图片 URL 的解析 / 替换规则"
              className="min-h-12"
            />
          </div>

          <div className="grid gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="detailUrl">漫画详情网址</Label>
              <Input
                id="detailUrl"
                value={form.detailUrl}
                onChange={(e) => set('detailUrl', e.target.value)}
                placeholder="https://www.ikanmh.top/book/{bookId}"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="contentUrl">漫画内容网址</Label>
              <Input
                id="contentUrl"
                value={form.contentUrl}
                onChange={(e) => set('contentUrl', e.target.value)}
                placeholder="https://www.ikanmh.top/chapter/{chapterId}"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="remark">备注</Label>
            <Input
              id="remark"
              value={form.remark}
              onChange={(e) => set('remark', e.target.value)}
              placeholder="选填"
            />
          </div>

          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.target.checked)}
              className="size-4 rounded border-slate-300"
            />
            启用该站点参与爬取
          </label>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            disabled={!valid}
            onClick={() =>
              onSubmit({
                id: site?.id,
                name: form.name,
                url: form.url,
                imageRule: form.imageRule,
                detailUrl: form.detailUrl,
                contentUrl: form.contentUrl,
                enabled: form.enabled ? 1 : 0,
                remark: form.remark,
              })
            }
          >
            {site ? '保存修改' : '确认新增'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
