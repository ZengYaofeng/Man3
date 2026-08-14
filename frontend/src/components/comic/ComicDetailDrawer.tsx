import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
} from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { ExternalLink, RefreshCw } from 'lucide-react'
import CoverImage from '@/components/comic/CoverImage'
import {
  type Book,
  getCrawlStatus,
  formatDateTime,
} from '@/types/book'

interface Props {
  book: Book | null
  onClose: () => void
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[96px_1fr] gap-3 border-b border-slate-100 py-2.5 text-sm">
      <div className="text-slate-400">{label}</div>
      <div className="min-w-0 break-words text-slate-700">{children}</div>
    </div>
  )
}

export default function ComicDetailDrawer({ book, onClose }: Props) {
  const status = book ? getCrawlStatus(book.crawlStatus) : null

  return (
    <Sheet open={!!book} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[480px]">
        {book && status && (
          <>
            <SheetHeader className="border-b border-slate-200 p-5">
              <div className="flex items-center gap-3">
                <CoverImage
                  src={book.coverUrl}
                  name={book.name}
                  className="size-14 shrink-0 rounded-md"
                />
                <div className="min-w-0">
                  <SheetTitle className="truncate text-lg">{book.name}</SheetTitle>
                  <SheetDescription className="truncate">
                    ID #{book.id} · 来源ID {book.sourceBookId}
                  </SheetDescription>
                </div>
              </div>
              <div className="pt-1">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${status.className}`}
                >
                  {status.label}
                </span>
              </div>
            </SheetHeader>

            <div className="flex-1 overflow-y-auto px-5 py-2">
              <Field label="漫画名称">{book.name}</Field>
              <Field label="别名">{book.alias ?? '-'}</Field>
              <Field label="作者">{book.author ?? '-'}</Field>
              <Field label="连载状态">{book.status ?? '-'}</Field>
              <Field label="地区">{book.region ?? '-'}</Field>
              <Field label="标签">
                {book.tags
                  ? book.tags.split(',').map((t) => (
                      <Badge key={t} variant="secondary" className="mr-1 mb-1">
                        {t}
                      </Badge>
                    ))
                  : '-'}
              </Field>
              <Field label="点击量">{book.clicks?.toLocaleString() ?? '-'}</Field>
              <Field label="评分">{book.score ?? '-'}</Field>
              <Field label="更新时间">{formatDateTime(book.updateTime)}</Field>
              <Field label="来源主页">
                {book.sourceUrl ? (
                  <a
                    href={book.sourceUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-indigo-600 hover:underline"
                  >
                    {book.sourceUrl}
                    <ExternalLink className="size-3" />
                  </a>
                ) : (
                  '-'
                )}
              </Field>
              <Field label="封面链接">
                {book.coverUrl ? (
                  <a
                    href={book.coverUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-indigo-600 hover:underline"
                  >
                    查看原图
                    <ExternalLink className="size-3" />
                  </a>
                ) : (
                  '-'
                )}
              </Field>
              <Field label="爬取状态">{status.label}</Field>
              <Field label="爬取时间">{formatDateTime(book.crawlTime)}</Field>
              <Field label="创建时间">{formatDateTime(book.createdAt)}</Field>
              <Field label="修改时间">{formatDateTime(book.updatedAt)}</Field>
              <Field label="简介">
                <p className="leading-relaxed">{book.description ?? '-'}</p>
              </Field>
            </div>

            <SheetFooter className="flex-row gap-2 border-t border-slate-200 p-4">
              <Button variant="outline" className="flex-1" onClick={onClose}>
                关闭
              </Button>
              <Button className="flex-1">
                <RefreshCw className="mr-1.5 size-4" />
                重新爬取
              </Button>
            </SheetFooter>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}
