import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft, Bell, BookOpen, Bookmark, ChevronLeft, ChevronRight, Clock3, Eye,
  Heart, List, Menu, MessageCircle, Play, Search, Settings2, SlidersHorizontal,
  Sparkles, Star, X,
} from 'lucide-react'
import { chapters, comics, type Comic } from './data'

const categories = ['热血', '冒险', '奇幻', '恋爱', '校园', '搞笑', '科幻', '治愈']
const coverArt = (comic: Comic, hero = false) => ({ background: `linear-gradient(145deg, ${comic.color}, #241c45)`, '--art': comic.color } as React.CSSProperties)

function Shell({ children }: { children: React.ReactNode }) {
  const [search, setSearch] = useState('')
  const navigate = useNavigate()
  return <><header className="site-header"><div className="header-inner">
    <Link className="brand" to="/"><BookOpen size={22} /><span>ComicWeb</span></Link>
    <nav>{[['/', '首页'], ['/category', '分类'], ['/category?sort=score', '排行'], ['/topics', '专题'], ['/reader/2/1', '阅读中心']].map(([to, label]) => <NavLink key={label} to={to} className={({isActive}) => isActive ? 'active' : ''}>{label}</NavLink>)}</nav>
    <form className="search" onSubmit={(e) => { e.preventDefault(); navigate(`/category?q=${encodeURIComponent(search)}`) }}><Search size={16}/><input value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索漫画、作者"/><button aria-label="搜索">搜索</button></form>
    <button className="login">登录</button><button className="signup">注册</button>
  </div></header><main>{children}</main><footer><div><strong>ComicWeb</strong><span>发现每一页的惊喜</span></div><div>关于我们　帮助中心　联系我们　服务条款</div><small>Copyright 2026 ComicWeb. All rights reserved.</small></footer></>
}

function Cover({ comic, size = 'card' }: { comic: Comic; size?: 'card' | 'hero' | 'detail' }) {
  return <div className={`cover ${size}`} style={coverArt(comic, size === 'hero')}><span className="cover-glow"/><span className="cover-kicker">COMICWEB ORIGINAL</span><strong>{comic.title}</strong><small>{comic.author}</small><span className="cover-moon">◐</span>{comic.vip && <em>VIP</em>}<b className={comic.status === '连载中' ? 'serial' : 'finished'}>{comic.status}</b></div>
}

function ComicCard({ comic }: { comic: Comic }) { return <Link className="comic-card" to={`/detail/${comic.id}`}><Cover comic={comic}/><div className="card-copy"><h3>{comic.title}</h3><p><Star size={13} fill="#f2ae43"/> {comic.score} <span>{comic.author}</span></p><small>{comic.category.join(' · ')}</small></div></Link> }
function SectionTitle({ children, link = '查看更多' }: {children: React.ReactNode; link?: string}) { return <div className="section-title"><h2>{children}</h2><Link to="/category">{link} <ChevronRight size={16}/></Link></div> }

function Home() {
  const [slide, setSlide] = useState(0)
  useEffect(() => { const timer = window.setInterval(() => setSlide(v => (v + 1) % 3), 5000); return () => clearInterval(timer) }, [])
  const feature = comics[slide]
  return <Shell><div className="page home">
    <section className="hero-grid"><div className="hero-stage" style={coverArt(feature, true)}><AnimatePresence mode="wait"><motion.div key={feature.id} initial={{opacity:0, y:14}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-14}} className="hero-copy"><span>编辑精选</span><h1>{feature.title}</h1><p>{feature.desc}</p><Link to={`/detail/${feature.id}`}><Play size={16} fill="currentColor"/> 立即阅读</Link></motion.div></AnimatePresence><div className="hero-orb">{feature.title.slice(0,1)}</div><div className="dots">{[0,1,2].map(i => <button aria-label={`第${i+1}张`} onClick={() => setSlide(i)} className={i===slide?'on':''} key={i}/>)}</div></div>
      <aside className="mini-grid">{comics.slice(3,7).map((c,i) => <Link key={c.id} to={`/detail/${c.id}`} className="mini-card" style={coverArt(c)}><span>{i%2?'HOT':'NEW'}</span><strong>{c.title}</strong><small>{c.latest}</small></Link>)}</aside></section>
    <section><SectionTitle>热门推荐</SectionTitle><div className="comic-grid six">{comics.slice(0,6).map(c => <ComicCard comic={c} key={c.id}/>)}</div></section>
    <section className="updates"><SectionTitle>最新更新</SectionTitle><div className="update-grid">{[0,1].map(col => <div className="update-list" key={col}>{comics.slice(col*4,col*4+4).map(c => <Link key={c.id} to={`/detail/${c.id}`}><strong>{c.title}</strong><span>{c.latest}</span><time>{c.update}</time></Link>)}</div>)}</div></section>
  </div></Shell>
}

function Detail() {
  const { id } = useParams(); const comic = comics.find(c => c.id === Number(id)) ?? comics[1]
  const [fav, setFav] = useState(false); const [descOpen, setDescOpen] = useState(false); const [asc, setAsc] = useState(true); const [comment, setComment] = useState(''); const [comments, setComments] = useState(['节奏太好了，期待下一话！','画面细节很喜欢，舞台那一页太美了。'])
  const list = asc ? chapters : [...chapters].reverse()
  return <Shell><div className="page detail-page"><div className="crumb">首页 <span>›</span> {comic.category[0]} <span>›</span> <b>{comic.title}</b></div>
    <section className="detail-hero"><Cover comic={comic} size="detail"/><div className="detail-copy"><h1>{comic.title}</h1><p className="author">作者：{comic.author}</p><div className="tags">{comic.category.map(x => <span key={x}>{x}</span>)}<span>{comic.status}</span></div><div className="stats"><span><Star fill="#f2ae43"/> {comic.score}</span><span><Eye/> {comic.views}</span><span><Heart/> {fav ? '3.2万' : '3.1万'}</span></div><p className={descOpen?'description open':'description'}>{comic.desc} 这是一个关于勇气、选择与陪伴的故事。每一次登上舞台，都让他们离真正的梦想更近一些。</p><button className="text-button" onClick={() => setDescOpen(!descOpen)}>{descOpen?'收起':'展开'}</button><div className="actions"><Link className="primary" to={`/reader/${comic.id}/1`}><Play size={16} fill="currentColor"/> 开始阅读</Link><button className="secondary" onClick={() => setFav(!fav)}><Heart size={16} fill={fav?'currentColor':'none'}/>{fav?'已收藏':'收藏'}</button></div></div></section>
    <section className="chapter-section"><SectionTitle link="">章节列表 <button className="sort-toggle" onClick={() => setAsc(!asc)}>{asc?'正序':'倒序'}</button></SectionTitle><div className="chapter-list">{list.slice(0,20).map((ch,i) => <Link to={`/reader/${comic.id}/${ch.id}`} key={ch.id}><b>{String(asc?i+1:chapters.length-i).padStart(2,'0')}</b><span>{ch.title}</span>{ch.vip && <em>VIP</em>}<time>{ch.date}</time></Link>)}</div><div className="pager"><button>上一页</button><b>1</b><button>2</button><button>下一页</button></div></section>
    <section className="comments"><SectionTitle link="全部评论">评论区</SectionTitle><div className="comment-form"><textarea value={comment} onChange={e => setComment(e.target.value)} placeholder="说说你的阅读感受..."/><button className="primary" onClick={() => { if(comment.trim()) { setComments([comment, ...comments]); setComment('') }}}>发布</button></div>{comments.map((c,i) => <article className="comment" key={`${c}${i}`}><div className="avatar">{['L','雨','K'][i%3]}</div><div><strong>{['林间信使','雨天的海','Kiko'][i%3]} <small>LV{3+i}</small></strong><p>{c}</p><time>{i+1}小时前　 <button>点赞</button>　<button>回复</button></time></div></article>)}</section>
  </div></Shell>
}

function Category() {
  const [picked, setPicked] = useState<string[]>([]); const [status, setStatus] = useState('全部'); const [sort, setSort] = useState('人气'); const [vip, setVip] = useState('全部'); const [page,setPage]=useState(1)
  const shown = useMemo(() => comics.filter(c => (!picked.length || picked.some(x => c.category.includes(x))) && (status==='全部'||c.status===status) && (vip==='全部'||Boolean(c.vip))).sort((a,b) => sort==='评分'? b.score-a.score : sort==='更新时间'? a.update.localeCompare(b.update) : b.views.localeCompare(a.views)),[picked,status,sort,vip])
  const toggle = (name:string) => setPicked(v => v.includes(name)?v.filter(x=>x!==name):[...v,name])
  return <Shell><div className="page category-page"><aside className="filters"><div className="filter-title"><SlidersHorizontal size={17}/> 筛选漫画</div><h4>漫画分类</h4><div className="chips">{categories.map(x => <button onClick={() => toggle(x)} className={picked.includes(x)?'selected':''} key={x}>{x}</button>)}</div><FilterGroup label="连载状态" values={['全部','连载中','已完结']} value={status} set={setStatus}/><FilterGroup label="漫画类型" values={['全部','仅VIP']} value={vip} set={setVip}/>{picked.length>0&&<div className="selected-filter">已选：{picked.join('、')} <button onClick={() => setPicked([])}>清除</button></div>}<button className="reset" onClick={() => {setPicked([]);setStatus('全部');setVip('全部')}}>重置筛选</button></aside><section className="category-content"><div className="list-toolbar"><div><b>漫画列表</b><span>共 {shown.length} 部</span></div><div className="sorts">{['人气','更新时间','评分','最新上架'].map(x => <button className={sort===x?'active':''} onClick={() => setSort(x)} key={x}>{x}</button>)}</div></div><motion.div layout className="comic-grid four">{shown.concat(shown).slice((page-1)*8,page*8).map((c,i) => <ComicCard comic={c} key={`${c.id}-${i}`}/>)}</motion.div><div className="pager"><button disabled={page===1} onClick={() => setPage(1)}>首页</button><button disabled={page===1} onClick={() => setPage(1)}>上一页</button>{[1,2].map(x=><button onClick={() => setPage(x)} className={page===x?'current':''} key={x}>{x}</button>)}<button disabled={page===2} onClick={() => setPage(2)}>下一页</button></div></section></div></Shell>
}
function FilterGroup({label,values,value,set}:{label:string;values:string[];value:string;set:(v:string)=>void}) {return <div className="filter-group"><h4>{label}</h4>{values.map(x=><label key={x}><input checked={value===x} onChange={() => set(x)} type="radio" name={label}/>{x}</label>)}</div>}

function Reader() {
 const {comicId,chapterId}=useParams(); const navigate=useNavigate(); const [chapter,setChapter]=useState(Number(chapterId)||1); const [drawer,setDrawer]=useState(false); const [isSettingsOpen,setIsSettingsOpen]=useState(false); const [mode,setMode]=useState<'scroll'|'page'>('scroll'); const [night,setNight]=useState(false); const [brightness,setBrightness]=useState(100); const [marked,setMarked]=useState(false); const comic=comics.find(c=>c.id===Number(comicId))??comics[1]
 useEffect(()=>{setChapter(Number(chapterId)||1); window.scrollTo({top:0})},[chapterId])
 const move=(delta:number)=>{const next=Math.min(Math.max(1,chapter+delta),chapters.length);setChapter(next);navigate(`/reader/${comic.id}/${next}`)}
 const panels = [0,1,2,3]
 return <div className={night?'reader night':'reader'}><header className="reader-head"><button onClick={()=>navigate(`/detail/${comic.id}`)}><ArrowLeft size={17}/> 返回</button><Link to={`/detail/${comic.id}`}>{comic.title}</Link><div className="reader-nav"><button onClick={()=>move(-1)} disabled={chapter===1}>上一话</button><b>{chapters[chapter-1]?.title}</b><button onClick={()=>move(1)} disabled={chapter===chapters.length}>下一话</button></div><div><button onClick={()=>setDrawer(true)}><List size={17}/> 目录</button><button onClick={()=>setIsSettingsOpen(true)}><Settings2 size={17}/> 设置</button></div></header><main className={mode==='page'?'reader-content pages':'reader-content'} style={{filter:`brightness(${brightness}%)`}}>{panels.map(i=><motion.article initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:i*.08}} className={`comic-panel p${i}`} key={i}><span>第 {chapter} 话 / {i+1}</span><div className="speech">{['今天也要向舞台出发。','你真的准备好了吗？','当然，因为有人在等我。','下一站，会更耀眼。'][i]}</div><div className="panel-shape"/></motion.article>)}</main><aside className="reader-float"><button onClick={()=>setDrawer(true)} title="目录"><List/></button><button onClick={()=>setMarked(!marked)} title="书签"><Bookmark fill={marked?'currentColor':'none'}/></button><button onClick={()=>setIsSettingsOpen(true)} title="设置"><Settings2/></button><button title="评论" onClick={()=>navigate(`/detail/${comic.id}`)}><MessageCircle/></button></aside><footer className="reader-foot"><button onClick={()=>move(-1)} disabled={chapter===1}><ChevronLeft/> 上一话</button><div><input aria-label="阅读进度" type="range" min="1" max={chapters.length} value={chapter} onChange={e=>{const n=Number(e.target.value);setChapter(n);navigate(`/reader/${comic.id}/${n}`)}}/><small>第 {chapter} 话 / 共 {chapters.length} 话　预计还需 12 分钟</small></div><button onClick={()=>move(1)} disabled={chapter===chapters.length}>下一话 <ChevronRight/></button></footer><AnimatePresence>{drawer&&<motion.aside initial={{x:320}} animate={{x:0}} exit={{x:320}} className="drawer"><div><b>章节目录</b><button onClick={()=>setDrawer(false)}><X/></button></div>{chapters.map(c=><button className={c.id===chapter?'now':''} key={c.id} onClick={()=>{navigate(`/reader/${comic.id}/${c.id}`);setDrawer(false)}}>{c.title}</button>)}</motion.aside>}{isSettingsOpen&&<motion.div className="settings-pop" initial={{opacity:0,scale:.96}} animate={{opacity:1,scale:1}} exit={{opacity:0,scale:.96}}><div><b>阅读设置</b><button onClick={()=>setIsSettingsOpen(false)}><X/></button></div><label>阅读模式 <button onClick={()=>setMode(mode==='scroll'?'page':'scroll')}>{mode==='scroll'?'上下滚动':'左右分页'}</button></label><label>页面亮度 <input type="range" min="50" max="100" value={brightness} onChange={e=>setBrightness(Number(e.target.value))}/></label><label>背景 <button onClick={()=>setNight(!night)}>{night?'深色':'浅色'}</button></label></motion.div>}</AnimatePresence></div>
}

function NotFound(){return <Shell><div className="empty"><Sparkles/><h1>页面正在绘制中</h1><Link to="/">回到首页</Link></div></Shell>}
export default function App(){return <Routes><Route path="/" element={<Home/>}/><Route path="/detail/:id" element={<Detail/>}/><Route path="/category" element={<Category/>}/><Route path="/reader/:comicId/:chapterId" element={<Reader/>}/><Route path="*" element={<NotFound/>}/></Routes>}
