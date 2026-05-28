# 前端 AGENTS.md

## 常用命令

```powershell
pnpm --filter web dev
pnpm --filter web build
pnpm --filter web test
pnpm --filter web lint
```

在 `apps/web` 目录内也可以直接运行：

```powershell
pnpm dev
pnpm build
pnpm test
pnpm lint
```

## 样式说明

- 优先使用 Tailwind 工具类实现布局、间距、颜色、状态和响应式样式。
- `src/styles.css` 作为 Tailwind 入口文件，保留少量全局基础样式即可。
- 不要手写大段组件 CSS，除非 Tailwind 工具类无法清晰表达。
- 不要手动编辑 `dist/styles.css`；它由 Tailwind CLI 生成。
- 修改前请参考项目中已有的 Tailwind 用法和风格，保持样式实现的一致性和可维护性。

## React 说明

- React 入口文件是 `src/app.tsx`。
- `src/index.html` 只保留 React 挂载点和 `app.js` 模块脚本引用。
- 页面交互优先用 React state 和事件处理实现，不要再直接用 `document.getElementById` 操作业务 UI。
- 构建输出的 `dist/app.js` 由 esbuild 生成，不要手动编辑。
