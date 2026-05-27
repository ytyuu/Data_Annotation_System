# 数据标注系统 Monorepo

本项目基于 Turbo 构建的 monorepo。

## 目录结构

- `apps/api` - Java API 服务端
- `apps/web` - 静态前端应用

## 快速启动

```powershell
pnpm install
pnpm dev
```

## 端口

- API: `http://localhost:7000`
- Web: `http://localhost:3000`

## 常用命令

```powershell
pnpm build
pnpm test
pnpm lint
```

## 说明

各应用的入口在 `apps/` 目录下。根目录的 `pnpm dev` 命令通过 Turbo 并行运行 API 和前端工作区的开发脚本。
