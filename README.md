# 数据标注系统 Monorepo

本项目基于 Turbo 构建的 monorepo。

## 目录结构

```
apps/
  api/                    - 后端 API（Kotlin + Javalin）
    src/main/kotlin/      - Kotlin 源码
    pom.xml               - Maven 构建配置
  web/                    - 前端静态应用
    src/                  - HTML/CSS/JS 源码
```

## 环境要求

- [mvnd](https://github.com/apache/maven-mvnd)（Maven Daemon，用于后端编译）
- JDK 25
- pnpm

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

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/` | 服务状态 |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/health/database` | PostgreSQL 数据库健康检查 |
| `GET` | `/api/hello?name=xxx` | 问候接口 |
| `GET` | `/api/me` | 当前登录用户信息 |
| `POST` | `/api/register` | 用户注册 |
| `POST` | `/api/login` | 用户登录并签发 JWT |

## 常用命令

```powershell
# 构建所有应用（通过 Turbo 并行构建）
pnpm build

# 运行所有应用的测试
pnpm test

# 运行代码检查
pnpm lint
```

## 单独操作后端

```powershell
cd apps/api

# 编译
mvnd compile

# 运行
mvnd exec:java

# 指定端口运行
mvnd exec:java -Dexec.args=8080
```

## 说明

各应用的入口在 `apps/` 目录下。根目录的 `pnpm dev` 命令通过 Turbo 并行运行 API 和前端工作区的开发脚本。
