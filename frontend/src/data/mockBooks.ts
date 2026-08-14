import type { Book } from '@/types/book'

// 生成确定性的伪随机数（保证每次刷新数据一致，便于演示）
const pseudo = (seed: number) => {
  const x = Math.sin(seed * 9999) * 10000
  return x - Math.floor(x)
}

const BASE_NAMES = [
  '进击的巨人',
  '海贼王',
  '火影忍者',
  '死神',
  '鬼灭之刃',
  '一拳超人',
  '咒术回战',
  '间谍过家家',
  '电锯人',
  '东京喰种',
  '钢之炼金术师',
  '我的英雄学院',
  '辉夜大小姐想让我告白',
  '刃牙',
  '排球少年',
  '名侦探柯南',
  '食戟之灵',
  '银魂',
  '全职猎人',
  'JOJO的奇妙冒险',
  'Fate/stay night',
  '凉宫春日的忧郁',
  'CLANNAD',
  '刀剑神域',
  '某科学的超电磁炮',
  '龙珠',
  '圣斗士星矢',
  '灌篮高手',
]

const SUFFIX = ['', ' 外传', ' 续章', ' 番外篇', ' 新章', ' 重制版']

const AUTHORS = [
  '尾田荣一郎',
  '岸本齐史',
  '谏山创',
  '吾峠呼世晴',
  'ONE',
  '芥见下下',
  '远藤达哉',
  '藤本树',
  '久保带人',
  '空知英秋',
  '富坚义博',
  '青山刚昌',
]

const REGIONS = ['日本', '韩国', '中国', '欧美']
const TAGS = ['热血', '冒险', '搞笑', '科幻', '恋爱', '战斗', '奇幻', '日常', '悬疑', '运动', '治愈', '后宫']
const STATUSES = ['连载中', '已完结']
const CRAWL_STATUSES = [-1, 0, 1, 2, 3]

const pick = <T,>(arr: T[], seed: number) => arr[Math.floor(pseudo(seed) * arr.length)]

const DESCRIPTION =
  '这是一部讲述少年成长与冒险的漫画作品，剧情紧凑、人物丰满，画面表现力极强，' +
  '在连载期间收获了极高的人气与口碑，是近年来不可多得的佳作。'

function buildBook(i: number): Book {
  const id = i + 1
  const sourceBookId = String(10000 + i * 37)
  const name = BASE_NAMES[i % BASE_NAMES.length] + (SUFFIX[Math.floor(i / BASE_NAMES.length)] ?? '')
  const status = pick(STATUSES, i + 3)
  const author = pick(AUTHORS, i + 5)
  const region = pick(REGIONS, i + 7)
  const tagCount = 2 + Math.floor(pseudo(i + 11) * 2)
  const tags = Array.from({ length: tagCount })
    .map((_, k) => pick(TAGS, i + 13 + k * 3))
    .filter((v, idx, self) => self.indexOf(v) === idx)
    .join(',')
  const clicks = Math.floor(pseudo(i + 17) * 990000) + 1000
  const score = Number((7 + pseudo(i + 19) * 3).toFixed(1))
  const crawlStatus = CRAWL_STATUSES[Math.floor(pseudo(i + 23) * CRAWL_STATUSES.length)]
  const updatedAt = new Date(
    Date.now() - Math.floor(pseudo(i + 29) * 1000 * 60 * 60 * 24 * 120),
  ).toISOString()

  return {
    id,
    sourceBookId,
    name,
    alias: pseudo(i + 31) > 0.6 ? `${name}（台译）` : undefined,
    author,
    status,
    region,
    tags,
    description: DESCRIPTION,
    coverUrl: `https://picsum.photos/seed/manga${id}/200/280`,
    updateTime: updatedAt,
    clicks,
    score,
    sourceUrl: `https://ikanmh.com/comic/${sourceBookId}`,
    crawlStatus,
    crawlTime: crawlStatus !== 0 ? updatedAt : undefined,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 200).toISOString(),
    updatedAt,
  }
}

export const MOCK_BOOKS: Book[] = Array.from({ length: 56 }, (_, i) => buildBook(i))
