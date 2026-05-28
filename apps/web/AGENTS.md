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

## 构建工具

- 使用 Vite 作为构建工具和开发服务器。
- `vite` 启动开发服务器（默认端口 3000），支持 HMR 热更新。
- `vite build` 执行生产构建，输出到 `dist/`。
- `tsc --noEmit` 仅做类型检查，不输出文件。

## 样式说明

- 优先使用 Tailwind 工具类实现布局、间距、颜色、状态和响应式样式。
- `src/styles.css` 作为 Tailwind 入口文件，保留少量全局基础样式即可。
- Tailwind CSS 通过 `@tailwindcss/vite` 插件集成，无需单独的 CLI 进程。
- 不要手写大段组件 CSS，除非 Tailwind 工具类无法清晰表达。
- 不要手动编辑 `dist/` 下的构建产物。
- 修改前请参考项目中已有的 Tailwind 用法和风格，保持样式实现的一致性和可维护性。

## React 说明

- React 入口文件是 `src/app.tsx`。
- `index.html` 位于项目根目录（Vite 约定），只保留 React 挂载点和 `src/app.tsx` 模块脚本引用。
- 页面交互优先用 React state 和事件处理实现，不要再直接用 `document.getElementById` 操作业务 UI。
- 构建输出的 `dist/` 由 Vite 生成，不要手动编辑。
