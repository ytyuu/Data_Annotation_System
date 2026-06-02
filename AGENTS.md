# 数据标注系统 AGENTS.md

本仓库是一个基于 pnpm workspace 和 Turbo 的 monorepo。请按实际项目结构工作，不要套用其他项目的技术栈假设。

## 项目结构

```text
apps/
  api/   后端 API，Kotlin + Javalin + Maven/mvnd
  web/   前端静态应用，React + TypeScript + Tailwind CSS + Vite
docs/    项目业务文档和数据库设计
```

子目录也放置了对应的 `AGENTS.md` 文件：

- 后端规则：`apps/api/AGENTS.md`
- 前端规则：`apps/web/AGENTS.md`

前后端子目录文件均已包含对应范围的专用规则。修改对应范围代码前必须先阅读并遵守更深层的说明。

## 文档目录

- `docs/business-process.md`：业务流程文档，说明角色权限、核心对象、流程图对应规则和状态流转。
- `docs/database-design.sql`：PostgreSQL 数据库表设计，包含建表语句、字段注释和索引。

涉及业务流程、角色权限、数据库结构、接口语义或页面工作流的改动前，AI 必须先阅读 `docs/business-process.md`；涉及表结构或字段含义时，还必须阅读 `docs/database-design.sql`。

## 根目录常用命令

根目录脚本通过 Turbo 调度各 workspace：

```powershell
pnpm dev
pnpm build
pnpm test
pnpm lint
```

按单个应用运行时优先使用 pnpm filter：

```powershell
pnpm --filter api build
pnpm --filter api test
pnpm --filter api lint

pnpm --filter web build
pnpm --filter web test
pnpm --filter web lint
```

本项目当前没有根目录 `format` / `format:check` 脚本，不要在说明或流程中假设它们存在。

## 环境要求

- pnpm：仓库声明使用 `pnpm@10.29.2`
- Node.js：用于 Turbo、workspace 脚本和前端 TypeScript 编译
- JDK 25：后端 Maven 配置的目标版本
- mvnd：后端脚本默认调用 Maven Daemon，命令名为 `mvnd` / `mvnd.exe`

## 后端规则（`apps/api`）

- 技术栈：Kotlin、Javalin 7、Maven、mvnd。
- 源码目录：`apps/api/src/main/kotlin`。
- 入口类：`com.example.api.Main`。
- 默认开发端口：`7000`。
- 构建、lint、test 都是 mvnd 编译检查，功能相同。
- 不要把后端描述为 Spring Boot、MyBatis、PostgreSQL、Redis 或 S3，除非代码中实际引入并配置了这些依赖。

常用命令：

```powershell
cd apps/api
pnpm build
pnpm test
pnpm lint
mvnd -q compile
mvnd exec:java
mvnd exec:java -Dexec.args=8080
```

## 前端规则（`apps/web`）

- 技术栈：React + TypeScript + Tailwind CSS + Vite。
- 源码目录：`apps/web/src`。
- 构建输出：`apps/web/dist`。
- 默认开发端口：`3000`。
- `index.html` 位于 `apps/web/` 根目录（Vite 约定），引用 `/src/app.tsx` 作为入口。
- React 入口为 `src/app.tsx`，由 Vite 打包。
- 样式通过 `@tailwindcss/vite` 插件集成；页面样式优先使用 Tailwind 工具类。
- 当前没有 Vue、Pinia、Vuetify、Tanstack Query 或 Zod；不要按这些框架的约定组织代码，除非先完成依赖和架构迁移。

常用命令：

```powershell
cd apps/web
pnpm build
pnpm test
pnpm lint
```

## 可扩展性设计原则（标注类型）

本系统当前以文本分类标注为主，但数据库和业务架构已预留扩展空间。未来增加图片标注、目标检测、音频转写、文本实体识别（NER）等新标注类型时，应遵循以下原则：

### 数据库层

- `data_items.content_type` 已支持 `text`、`image`、`audio`、`video`、`json`，新增多媒体类型无需改表。
- `data_items.content` 存储文本或资源 URL，`metadata`（JSONB）可存放尺寸、时长、文件名等扩展信息。
- `annotations.result` 为 JSONB，可存任意结构化结果（坐标、多边形、时序区间等）。
- `datasets.annotation_schema` 定义标注配置，当前为分类结构，未来可扩展为检测框、分割掩码、实体标签等配置。

### 后端层

- **一致性比对**：`DatasetQueryHelper.areAnnotationResultsConsistent` 当前只比对 `value`/`values` 字段。新增标注类型时必须添加对应比对策略（如目标检测用 IoU、NER 用实体重叠率），不能简单回退到 JSON 字符串匹配。
- **结果解析**：`extractSelectionValues` 只提取选择值。新增类型时需扩展结果提取逻辑，或按类型路由到独立解析器。
- **状态机**：互查后的 `finalizeReviewedItem` 状态推进逻辑与标注类型无关，可直接复用。

### 前端层

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

## 开发约定

- 修改前先确认影响范围：根目录、`apps/api`、`apps/web` 分别使用各自脚本。
- 保持变更范围聚焦，避免顺手重构无关模块。
- 不要手动编辑生成产物，除非任务明确要求；常见生成目录包括 `.turbo/`、`node_modules/`、`apps/web/dist/`、`apps/api/target/`、`apps/api/build/`。
- 后端新增接口时同步考虑测试脚本是否需要覆盖；前端改动涉及构建输出时通过脚本重新生成。
- 使用现有脚本和目录布局，不要引入新的构建工具或框架，除非用户明确要求。
- **新增标注类型时**，优先扩展 `src/components/shared/` 下的独立组件，而非修改页面级组件。

## 提交前检查建议

根据改动范围运行最小必要检查：

```powershell
# 只改后端
pnpm --filter api build
pnpm --filter api test

# 只改前端
pnpm --filter web build
pnpm --filter web test

# 跨应用或根目录变更
pnpm build
pnpm test
```
