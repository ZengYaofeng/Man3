import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import Dashboard from '@/pages/Dashboard'
import ComicList from '@/pages/ComicList'
import CrawlProgress from '@/pages/CrawlProgress'
import ChapterList from '@/pages/ChapterList'
import SiteList from '@/pages/SiteList'
import SiteDedup from '@/pages/SiteDedup'

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
          <Route path="crawl/progress" element={<CrawlProgress />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
