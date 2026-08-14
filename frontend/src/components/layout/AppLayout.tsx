import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  BookOpen,
  ListFilter,
  Bot,
  ScrollText,
  Settings,
  Bug,
  ChevronRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: React.ComponentType<{ className?: string }>
  disabled?: boolean
}

interface NavGroup {
  title: string
  items: NavItem[]
}

const NAV: NavGroup[] = [
  {
    title: '概览',
    items: [{ to: '/', label: '仪表盘', icon: LayoutDashboard }],
  },
  {
    title: '漫画管理',
    items: [{ to: '/comics', label: '漫画列表', icon: ListFilter }],
  },
  {
    title: '爬虫中心',
    items: [
      { to: '/crawl/progress', label: '爬取进度', icon: Bot },
      { to: '/crawl/logs', label: '爬取日志', icon: ScrollText, disabled: true },
    ],
  },
  {
    title: '系统',
    items: [{ to: '/settings', label: '系统设置', icon: Settings, disabled: true }],
  },
]

export default function AppLayout() {
  const location = useLocation()

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      {/* 侧边栏 */}
      <aside className="flex w-64 flex-col bg-slate-900 text-slate-100">
        <div className="flex h-16 items-center gap-2 px-5">
          <div className="flex size-9 items-center justify-center rounded-lg bg-indigo-500/20 text-indigo-300">
            <Bug className="size-5" />
          </div>
          <div className="leading-tight">
            <div className="text-sm font-semibold">漫画爬虫管理</div>
            <div className="text-[11px] text-slate-400">Crawler Admin</div>
          </div>
        </div>

        <nav className="flex-1 space-y-6 overflow-y-auto px-3 py-4">
          {NAV.map((group) => (
            <div key={group.title}>
              <div className="px-3 pb-2 text-[11px] font-medium uppercase tracking-wider text-slate-500">
                {group.title}
              </div>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon
                  if (item.disabled) {
                    return (
                      <div
                        key={item.to}
                        className="flex cursor-not-allowed items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-500"
                        title="敬请期待"
                      >
                        <Icon className="size-4" />
                        <span>{item.label}</span>
                        <span className="ml-auto rounded bg-slate-700/50 px-1.5 py-0.5 text-[10px] text-slate-400">
                          待开发
                        </span>
                      </div>
                    )
                  }
                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.to === '/'}
                      className={({ isActive }: { isActive: boolean }) =>
                        cn(
                          'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                          isActive
                            ? 'bg-indigo-500/20 font-medium text-white'
                            : 'text-slate-300 hover:bg-slate-800 hover:text-white',
                        )
                      }
                    >
                      <Icon className="size-4" />
                      <span>{item.label}</span>
                      {location.pathname === item.to && (
                        <ChevronRight className="ml-auto size-4 text-indigo-300" />
                      )}
                    </NavLink>
                  )
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-slate-800 px-5 py-3 text-[11px] text-slate-500">
          Man3 Crawler · v1.0.0
        </div>
      </aside>

      {/* 主区域 */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-16 shrink-0 items-center gap-3 border-b border-slate-200 bg-white px-6">
          <BookOpen className="size-5 text-indigo-500" />
          <div>
            <h1 className="text-base font-semibold text-slate-800">漫画管理</h1>
            <p className="text-xs text-slate-400">管理已爬取的漫画主表数据</p>
          </div>
          <div className="ml-auto flex items-center gap-3">
            <div className="hidden items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-sm text-slate-500 sm:flex">
              <span className="size-2 rounded-full bg-emerald-500" />
              爬虫服务正常
            </div>
            <div className="flex size-9 items-center justify-center rounded-full bg-indigo-500 text-sm font-medium text-white">
              管
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
