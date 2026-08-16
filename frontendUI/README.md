# ComicWeb Frontend UI

面向读者的漫画阅读网站前端原型，独立于现有的后台管理前端，使用本地模拟数据展示完整阅读流程。

## 页面

- `/`：首页，含轮播、推荐漫画与最新更新
- `/detail/:id`：漫画详情、章节、收藏与评论
- `/category`：分类筛选、排序与分页
- `/reader/:comicId/:chapterId`：阅读器、目录、书签、阅读设置和章节切换

## 启动

```bash
npm install
npm run dev
```

开发服务器启动后，访问终端显示的本地地址。当前项目不连接后台服务；展示数据集中在 `src/data.ts`，后续可按接口契约替换为真实 API 请求。

## 构建

```bash
npm run build
```
