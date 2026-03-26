# FlowForge Console

前端管理台第一版使用：

- React
- TypeScript
- Vite
- React Router
- Framer Motion

## 启动

```bash
cd flowforge-console
npm install
npm run dev
```

默认地址：

- http://localhost:5173

默认会请求：

- `http://localhost:8080`

如果你的后端地址不同，可以这样指定：

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

## 当前页面

- 控制台首页
- 工作流详情页
- 实例详情页

## 当前设计方向

- 偏控制塔而不是普通后台
- 强排版、弱卡片
- 左侧固定导航 + 右侧主工作区
- 轻量动效，不做噪音式动画
