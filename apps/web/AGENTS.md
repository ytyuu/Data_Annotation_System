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

## 目录与入口

- 源码目录：`apps/web/src`
- 构建输出：`apps/web/dist`
- 默认开发端口：`3000`
- `index.html` 位于 `apps/web/` 根目录（Vite 约定），引用 `/src/app.tsx` 作为入口。
- React 入口为 `src/app.tsx`，由 Vite 打包。
- 样式通过 `@tailwindcss/vite` 插件集成；页面样式优先使用 Tailwind 工具类。
- 当前没有 Vue、Pinia、Vuetify、Tanstack Query 或 Zod；不要按这些框架的约定组织代码，除非先完成依赖和架构迁移。

## 可扩展性设计原则（标注类型）

本系统当前以文本分类标注为主，但数据库和业务架构已预留扩展空间。未来增加图片标注、目标检测、音频转写、文本实体识别（NER）等新标注类型时，前端应遵循以下原则：

前端已将标注相关功能拆分为独立组件，新增标注类型时按以下方式扩展：

```
src/components/shared/
  DataItemViewer.tsx        # 数据展示：根据 contentType 渲染文本/图片/音频/视频
  AnnotationEditor.tsx      # 标注编辑器：根据 schema.type 渲染不同编辑器
  AnnotationResultViewer.tsx # 结果展示：将 result JSON 映射为可读标签
  AnnotationResultBuilder.tsx # 结果构建：将 selection 序列化为 result JSON
  AnnotationCard.tsx        # 标注记录卡片：展示单条标注结果
```

**扩展示例**：

1. **新增图片目标检测**：
   - `DataItemViewer`：已支持 `image` 类型（显示 `<img>`）。
   - `AnnotationEditor`：需新增 `bounding-box` 类型分支，集成 Canvas 画框组件。
   - `AnnotationResultViewer`：需解析 `{boxes: [{x, y, w, h, label}]}` 并展示为 "猫 (x:10, y:20)" 等格式。
   - `AnnotationResultBuilder`：需将画框坐标序列化为 JSON。

2. **新增音频分类**：
   - `DataItemViewer`：需增加 `audio` 分支（显示 `<audio controls>`）。
   - `AnnotationEditor`：复用现有的 `classification` 编辑器即可。
   - 结果解析和构建逻辑无需改动。

3. **新增文本 NER**：
   - `DataItemViewer`：文本类型已支持。
   - `AnnotationEditor`：需新增 `ner` 类型分支，支持文本高亮和实体类型选择。
   - `AnnotationResultViewer`：需解析 `{entities: [{text, start, end, label}]}`。

**关键原则**：
- 数据展示、标注编辑、结果解析三者独立，新增类型时按需扩展对应组件。
- 不要在页面组件中硬编码标注逻辑（如 `AnnotatorTaskWorkspaceModal`、`ProviderDisputesPage` 已改用上述共享组件）。
- 保持 `annotation_schema` 的灵活性，用 `type` 字段区分标注类型，前端根据 `type` 路由到对应渲染器。
- **新增标注类型时**，优先扩展 `src/components/shared/` 下的独立组件，而非修改页面级组件。

## 组件提取与统一复用

### 已有共享组件

`src/components/shared/` 目录存放跨页面复用的通用组件，当前包括：

| 组件 | 用途 | 使用位置 |
|------|------|----------|
| `AppButton.tsx` | 统一按钮 | 所有页面 |
| `DataItemViewer.tsx` | 数据项内容展示 | 审核页、争议处理页、标注工作区 |
| `AnnotationEditor.tsx` | 标注编辑器 | 标注工作区 |
| `AnnotationResultViewer.tsx` | 标注结果展示 | 审核页、争议处理页 |
| `AnnotationResultBuilder.tsx` | 结果序列化 | 标注工作区 |
| `AnnotationCard.tsx` | 标注记录卡片 | 争议处理页 |
| `DonutChart.tsx` | 环形图表 | 数据集概览 |

### 复用原则

- **按钮**：所有按钮必须使用 `AppButton`，禁止在页面中直接写 `<button>` 元素。
- **状态胶囊**：所有状态标签使用 `app-badge` CSS 类（定义在 `styles.css`），保持视觉一致。
- **数据展示**：数据项内容展示统一使用 `DataItemViewer`，不要在不同页面中重复实现渲染逻辑。
- **表格行高**：表格中需要垂直居中的列统一添加 `align-middle`，保持行内元素对齐。

### 何时提取组件

出现以下情况时，应将代码提取为共享组件：

1. **同一功能在 2 个及以上页面中出现**（如数据项指标卡片 `DatasetItemMetric`）
2. **页面内重复出现的 UI 模式**（如状态标签、操作按钮组）
3. **与业务数据强相关的渲染逻辑**（如标注结果解析、schema 摘要生成）

提取步骤：
1. 在 `src/components/shared/` 下新建组件文件
2. 定义清晰的 Props 接口，避免与特定页面状态耦合
3. 在原有使用位置替换为组件引用
4. 确保构建通过

## 开发约定

- 前端改动涉及构建输出时通过脚本重新生成。
- 使用现有脚本和目录布局，不要引入新的构建工具或框架，除非用户明确要求。
