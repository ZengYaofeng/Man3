import { useEffect, useState } from 'react'
import { BookOpen, Layers, Image as ImageIcon, CheckCircle2, XCircle } from 'lucide-react'
import { fetchOverview } from '@/lib/api'

export default function Dashboard() {
  const [data, setData] = useState({
    bookCount: 0,
    chapterCount: 0,
    bookPageCount: 0,
    doneCount: 0,
    failedCount: 0,
  })

  useEffect(() => {
    fetchOverview().then(setData)
  }, [])

  const cards = [
    { label: '漫画总数', value: data.bookCount, icon: BookOpen, color: 'bg-indigo-500' },
    { label: '章节总数', value: data.chapterCount, icon: Layers, color: 'bg-sky-500' },
    { label: '图片总数', value: data.bookPageCount, icon: ImageIcon, color: 'bg-violet-500' },
    { label: '爬取完成', value: data.doneCount, icon: CheckCircle2, color: 'bg-emerald-500' },
    { label: '爬取失败', value: data.failedCount, icon: XCircle, color: 'bg-rose-500' },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-slate-800">仪表盘</h2>
        <p className="text-sm text-slate-400">爬虫系统运行概览</p>
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        {cards.map((c) => {
          const Icon = c.icon
          return (
            <div
              key={c.label}
              className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
            >
              <div className={`mb-3 inline-flex size-10 items-center justify-center rounded-lg ${c.color} text-white`}>
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
      <div className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        <p className="font-medium text-slate-700">快速开始</p>
        <p className="mt-2">
          进入左侧「漫画管理 → 漫画列表」可查看漫画主表（book）的全部字段，支持搜索、筛选与分页。
        </p>
      </div>
    </div>
  )
}
