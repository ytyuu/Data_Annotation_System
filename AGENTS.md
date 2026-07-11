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

- `docs/数据标注系统业务文档.md`：业务流程文档，说明角色权限、核心对象、流程图对应规则和状态流转。
- `docs/数据标注数据库设计.sql`：PostgreSQL 数据库表设计，包含建表语句、字段注释和索引。

涉及业务流程、角色权限、数据库结构、接口语义或页面工作流的改动前，AI 必须先阅读 `docs/数据标注系统业务文档.md`；涉及表结构或字段含义时，还必须阅读 `docs/数据标注数据库设计.sql`。

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

## 开发约定

- 修改前先确认影响范围：根目录、`apps/api`、`apps/web` 分别使用各自脚本。
- 保持变更范围聚焦，避免顺手重构无关模块。
- 不要手动编辑生成产物，除非任务明确要求；常见生成目录包括 `.turbo/`、`node_modules/`、`apps/web/dist/`、`apps/api/target/`、`apps/api/build/`。
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
