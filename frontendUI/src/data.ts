export type Comic = {
  id: number; title: string; author: string; score: number; status: '连载中' | '已完结'; category: string[]
  update: string; latest: string; color: string; desc: string; views: string; vip?: boolean
}

export const comics: Comic[] = [
  { id: 1, title: '苍穹下的冒险者', author: '星野遥', score: 9.6, status: '连载中', category: ['冒险', '奇幻'], update: '2小时前', latest: '第128话 失落的星图', color: '#7465c7', desc: '在浮空群岛尽头，一位见习航海士为寻找失踪的父亲踏上了穿越云海的旅程。', views: '128.5万' },
  { id: 2, title: '偶像的配对游戏', author: '木棉', score: 9.2, status: '连载中', category: ['都市', '恋爱'], update: '4小时前', latest: '第38话 约定的舞台', color: '#ef7d86', desc: '镜头前是完美偶像，镜头后却必须和竞争对手完成一场心动任务。', views: '86.2万' },
  { id: 3, title: '深海邮差', author: '南十字', score: 9.4, status: '已完结', category: ['科幻', '治愈'], update: '昨天', latest: '全52话', color: '#367fb9', desc: '送往海沟深处的一封封信，连接着陆地与从未见过阳光的居民。', views: '72.4万' },
  { id: 4, title: '怪物研究社', author: '青木', score: 8.9, status: '连载中', category: ['校园', '搞笑'], update: '昨天', latest: '第76话 新成员', color: '#f2a653', desc: '这所学校最神秘的社团，只研究那些藏在日常里的小怪物。', views: '46.9万' },
  { id: 5, title: '白昼的月亮', author: '弥生', score: 9.1, status: '已完结', category: ['恋爱', '治愈'], update: '3天前', latest: '全40话', color: '#43a894', desc: '无法入睡的插画师和只在白天出现的少年，共同画出了一轮月亮。', views: '63.1万' },
  { id: 6, title: '铁城警报', author: 'J. Lin', score: 8.7, status: '连载中', category: ['动作', '科幻'], update: '3天前', latest: '第91话 防线', color: '#4a90d9', desc: '巨型都市的防卫队，在第七码头遭遇了从地下苏醒的机械军团。', views: '55.0万', vip: true },
  { id: 7, title: '春日信箱', author: '铃兰', score: 9.0, status: '连载中', category: ['校园', '恋爱'], update: '5天前', latest: '第24话 雨天来信', color: '#cf83b2', desc: '匿名信箱里的一句问候，让两个不善表达的人慢慢靠近。', views: '31.8万' },
  { id: 8, title: '雾隐山神', author: '风间', score: 8.8, status: '连载中', category: ['奇幻', '冒险'], update: '5天前', latest: '第63话 山门', color: '#3c9580', desc: '山村少年继承古老神契，在迷雾中守护最后一座灵山。', views: '49.7万' },
]

export const chapters = Array.from({ length: 38 }, (_, i) => ({ id: i + 1, title: `第${i + 1}话 ${['初次登场', '练习室的夜晚', '镜头之外', '约定的舞台'][i % 4]}`, date: `${Math.max(1, 38 - i)}天前`, vip: i > 31 }))
