# 数据标注系统 AGENTS.md

本仓库是一个基于 pnpm workspace 和 Turbo 的 monorepo。请按实际项目结构工作，不要套用其他项目的技术栈假设。

## 项目结构

```text
apps/
  api/   后端 API，Kotlin + Javalin + Maven/mvnd
  web/   前端静态应用，React + TypeScript + Tailwind CSS + Node 脚本
```

子目录也放置了对应的 `AGENTS.md` 文件：

- 后端规则：`apps/api/AGENTS.md`
- 前端规则：`apps/web/AGENTS.md`

前后端子目录文件均已包含对应范围的专用规则。修改对应范围代码前必须先阅读并遵守更深层的说明。

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
- 测试脚本默认使用 `PORT=7071`，会启动服务并检查 `/api/health`、`/api/hello` 和 `/`。
- 构建、lint、test 都会先通过 mvnd 编译 Kotlin；当前 lint 脚本本质上是编译检查。
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

- 技术栈：React + TypeScript + Tailwind CSS，使用自定义 Node 脚本构建和启动静态服务。
- 源码目录：`apps/web/src`。
- 构建输出：`apps/web/dist`。
- 默认开发端口：`3000`。
- 测试脚本默认使用 `PORT=3001`，会构建后启动静态服务并检查首页与 `app.js`。
- 当前没有 Vite、Vue、Pinia、Vuetify、Tanstack Query 或 Zod；不要按这些框架的约定组织代码，除非先完成依赖和架构迁移。
- React 入口为 `src/app.tsx`，由 esbuild 打包到 `dist/app.js`。
- 样式通过 Tailwind CSS CLI 从 `src/styles.css` 构建到 `dist/styles.css`；页面样式优先使用 Tailwind 工具类。
- `index.html` 需要加载 `./app.js`，lint 脚本会检查这一点。

常用命令：

```powershell
cd apps/web
pnpm build
pnpm test
pnpm lint
```

## 开发约定

- 修改前先确认影响范围：根目录、`apps/api`、`apps/web` 分别使用各自脚本。
- 保持变更范围聚焦，避免顺手重构无关模块。
- 不要手动编辑生成产物，除非任务明确要求；常见生成目录包括 `.turbo/`、`node_modules/`、`apps/web/dist/`、`apps/api/target/`、`apps/api/build/`。
- 后端新增接口时同步考虑测试脚本是否需要覆盖；前端改动涉及构建输出时通过脚本重新生成。
- 使用现有脚本和目录布局，不要引入新的构建工具或框架，除非用户明确要求。

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
