// 站点来源表 site_source 字段定义
// 表设计: 网站名称 / 网址 / 图片规则 / 漫画详情网址 / 漫画内容网址

export interface SiteSource {
  /** 主键ID */
  id: number
  /** 网站名称 */
  name: string
  /** 网址(站点根域名) */
  url: string
  /** 图片规则(图片URL解析/替换规则, 支持占位符说明) */
  imageRule: string
  /** 漫画详情网址模板(可含 {id} / {bookId} 占位符) */
  detailUrl: string
  /** 漫画内容网址模板(章节阅读页, 可含 {id} / {chapterId} 占位符) */
  contentUrl: string
  /** 启用状态(后端原始: 1启用 0停用) */
  enabled?: number
  /** 启用状态(派生布尔, 便于 UI 使用) */
  isEnabled?: boolean
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

// 新增/编辑表单载荷(不含 id 与时间戳), enabled 以数字提交(1/0)
export type SiteSourcePayload = Omit<SiteSource, 'id' | 'createdAt' | 'updatedAt' | 'isEnabled'>

// 查重结果(全站查重: 当前漫画表中未匹配的漫画名称)
export interface DedupResult {
  bookId: number
  bookName: string
  sourceSite: string
  matchedSiteName?: string
}
