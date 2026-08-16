import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import Dashboard from '@/pages/Dashboard'
import ComicList from '@/pages/ComicList'
import CrawlProgress from '@/pages/CrawlProgress'
import ChapterList from '@/pages/ChapterList'
import SiteList from '@/pages/SiteList'
import SiteDedup from '@/pages/SiteDedup'
import InventoryHistory from '@/pages/InventoryHistory'
import IngestLogList from '@/pages/IngestLogList'
import MangaReaderPage from '@/pages/MangaReaderPage'
import CrawlLogPage from '@/pages/CrawlLogPage'
import ExternalComicListPage from '@/pages/ExternalComicListPage'
import ExternalCrawlerManagementPage from '@/pages/ExternalCrawlerManagementPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="comics" element={<ComicList />} />
          <Route path="chapters" element={<ChapterList />} />
          <Route path="sites" element={<SiteList />} />
          <Route path="dedup" element={<SiteDedup />} />
          <Route path="sources/niaoniaomh" element={<ExternalComicListPage source="niaoniaomh" />} />
          <Route path="sources/yuyumh" element={<ExternalComicListPage source="yuyumh" />} />
          <Route path="external-crawlers" element={<ExternalCrawlerManagementPage />} />
          <Route path="inventory" element={<InventoryHistory />} />
          <Route path="ingest-logs" element={<IngestLogList />} />
          <Route path="crawl/progress" element={<CrawlProgress />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
        {/* 独立漫画阅读页(新页签打开, 脱离侧边栏布局) */}
        <Route path="reader/:bookId/:chapterId" element={<MangaReaderPage />} />
        <Route path="reader/:source/:bookId/:chapterId" element={<MangaReaderPage />} />
        {/* 入库详情 / 爬虫日志页(新页签打开, 控制台风格) */}
        <Route path="crawllog/:bookId" element={<CrawlLogPage />} />
      </Routes>
    </BrowserRouter>
  )
}
