# 数据标注系统 Monorepo

本项目基于 Turbo 构建的 monorepo。

## 目录结构

- `apps/api` - Kotlin API 服务端
- `apps/web` - 静态前端应用

## 快速启动

```powershell
# 安装所有依赖（根目录和各 apps）
pnpm install

# 启动开发服务器（通过 Turbo 并行启动 API 和前端）
pnpm dev
```

API 开发模式使用 `mvnd` 编译，保存 `apps/api/src/main/kotlin` 下的 Kotlin 源码后会自动重新编译并重启后端。

## 端口

- API: `http://localhost:7000`
- Web: `http://localhost:3000`

## 常用命令

```powershell
# 构建所有应用（通过 Turbo 并行构建）
pnpm build

# 运行所有应用的测试
pnpm test

# 运行代码检查
pnpm lint
```

## 说明

各应用的入口在 `apps/` 目录下。根目录的 `pnpm dev` 命令通过 Turbo 并行运行 API 和前端工作区的开发脚本。
