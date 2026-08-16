import { useEffect, useState } from 'react'
import { Loader2, Pause, Play, Save, Settings2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  fetchExternalIngestConfig,
  fetchExternalIngestStatus,
  fetchIkanmhCrawlerStatus,
  startExternalIngest,
  stopExternalIngest,
  updateExternalIngestConfig,
} from '@/lib/externalComicApi'
import type { ExternalComicSource, ExternalIngestConfig, ExternalIngestStatus, IkanmhCrawlerStatus } from '@/types/externalComic'

const SOURCES: Array<{ source: ExternalComicSource; name: string }> = [
  { source: 'niaoniaomh', name: 'Niaoniaomh' },
  { source: 'yuyumh', name: 'Yuyumh' },
]

export default function ExternalCrawlerManagementPage() {
  const [config, setConfig] = useState<ExternalIngestConfig>({ chapterWorkers: 4, pageWorkers: 12, ikanmhChapterWorkers: 16 })
  const [states, setStates] = useState<Record<string, ExternalIngestStatus>>({})
  const [ikanmh, setIkanmh] = useState<IkanmhCrawlerStatus | null>(null)
  const [saving, setSaving] = useState(false)

  const load = async () => {
    const [nextConfig, nextIkanmh, ...nextStates] = await Promise.all([
      fetchExternalIngestConfig(),
      fetchIkanmhCrawlerStatus(),
      ...SOURCES.map((item) => fetchExternalIngestStatus(item.source)),
    ])
    setConfig(nextConfig)
    setIkanmh(nextIkanmh)
    setStates(Object.fromEntries(SOURCES.map((item, index) => [item.source, nextStates[index]])))
  }

  useEffect(() => { load().catch(() => undefined) }, [])
  useEffect(() => {
    if (!ikanmh?.running && !Object.values(states).some((state) => state?.running)) return
    const timer = window.setInterval(() => load().catch(() => undefined), 1200)
    return () => window.clearInterval(timer)
  }, [ikanmh?.running, states])

  const save = async () => {
    setSaving(true)
    try {
      setConfig(await updateExternalIngestConfig(config.chapterWorkers, config.pageWorkers, config.ikanmhChapterWorkers))
    } finally {
      setSaving(false)
    }
  }

  const start = async (source: ExternalComicSource, stage: 'chapters' | 'images') => {
    const nextState = await startExternalIngest(source, stage)
    setStates((current) => ({ ...current, [source]: nextState }))
  }

  const stop = async (source: ExternalComicSource) => {
    const nextState = await stopExternalIngest(source)
    setStates((current) => ({ ...current, [source]: nextState }))
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3 border-b border-slate-200 pb-4">
        <WorkerInput label="ikanmh chapter workers" value={config.ikanmhChapterWorkers} onChange={(value) => setConfig({ ...config, ikanmhChapterWorkers: value })} />
        <WorkerInput label="External chapter workers" value={config.chapterWorkers} onChange={(value) => setConfig({ ...config, chapterWorkers: value })} />
        <WorkerInput label="Image URL workers" value={config.pageWorkers} onChange={(value) => setConfig({ ...config, pageWorkers: value })} />
        <Button variant="outline" onClick={save} disabled={saving}>
          {saving ? <Loader2 className="mr-1.5 size-4 animate-spin" /> : <Save className="mr-1.5 size-4" />}
          Save configuration
        </Button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div><h2 className="font-semibold text-slate-800">ikanmh</h2><p className="text-xs text-slate-500">Main-library image URL crawler</p></div>
            <Settings2 className="size-5 text-slate-400" />
          </div>
          <div className="mt-4 h-2 overflow-hidden bg-slate-100"><div className={`h-full transition-all ${ikanmh?.running ? 'w-full bg-emerald-500' : 'w-0 bg-slate-300'}`} /></div>
          <div className="mt-2 flex justify-between text-sm text-slate-600"><span>{ikanmh?.message || 'Loading'}</span><span className="tabular-nums">{ikanmh?.chapterWorkers ?? config.ikanmhChapterWorkers} workers</span></div>
          <div className="mt-1 text-xs text-slate-400">Uses the existing resume and chapter-claiming workflow. The new limit applies to the next run.</div>
        </div>

        {SOURCES.map(({ source, name }) => {
          const state = states[source]
          const progress = state?.total ? Math.round(state.processed * 100 / state.total) : 0
          return (
            <div key={source} className="border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <div><h2 className="font-semibold text-slate-800">{name}</h2><p className="text-xs text-slate-500">Only comics missing from the local library are ingested.</p></div>
                <Settings2 className="size-5 text-slate-400" />
              </div>
              <div className="mt-4 h-2 overflow-hidden bg-slate-100"><div className="h-full bg-indigo-500 transition-all" style={{ width: `${progress}%` }} /></div>
              <div className="mt-2 flex justify-between text-sm text-slate-600"><span>{state?.stage === 'images' ? 'Image URL ingestion' : 'Chapter ingestion'}</span><span className="tabular-nums">{state?.processed ?? 0}/{state?.total ?? 0}</span></div>
              <div className="mt-1 truncate text-xs text-slate-400">{state?.currentName || state?.message || 'Not started'}</div>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button size="sm" onClick={() => start(source, 'chapters')} disabled={state?.running}><Play className="mr-1 size-3.5" />Chapters</Button>
                <Button size="sm" variant="outline" onClick={() => start(source, 'images')} disabled={state?.running}><Play className="mr-1 size-3.5" />Image URLs</Button>
                {state?.running && <Button size="sm" variant="ghost" className="text-rose-600" onClick={() => stop(source)}><Pause className="mr-1 size-3.5" />Stop</Button>}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function WorkerInput({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return <div className="min-w-[150px]"><label className="mb-1 block text-sm text-slate-600">{label}</label><Input type="number" min={1} max={24} value={value} onChange={(event) => onChange(Number(event.target.value))} /></div>
}
