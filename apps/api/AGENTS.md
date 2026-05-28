# 后端 AGENTS.md

本目录是数据标注系统的后端 API。

## 技术栈

- Kotlin 2.3
- Javalin 7
- Maven + mvnd
- JDK 25

当前没有 Spring Boot、MyBatis、PostgreSQL、Redis 或 S3 依赖；不要按这些框架或服务的约定组织代码，除非任务明确要求引入。

## 目录与入口

- 源码目录：`src/main/kotlin`
- 应用入口：`com.example.api.Main`
- Javalin 应用创建：`com.example.api.ApplicationKt#createApp`
- 路由注册：`routes/ApiRoutes.kt`
- HTTP 配置：`http/HttpConfig.kt`
- 默认开发端口：`7000`

## 常用命令

```powershell
pnpm --filter api dev
pnpm --filter api build
pnpm --filter api test
pnpm --filter api lint
```

在 `apps/api` 目录内也可以直接运行：

```powershell
pnpm dev
pnpm build
pnpm test
pnpm lint
mvnd -q compile
mvnd exec:java
mvnd exec:java -Dexec.args=8080
```

## 构建与测试说明

- `pnpm build` 会通过 mvnd 编译 Kotlin。
- `pnpm lint` 当前也是编译检查，不是独立的格式化或静态分析工具。
- `pnpm test` 会编译后启动 API 服务，默认使用 `PORT=7071`，并检查 `/api/health`、`/api/hello` 和 `/`。
- `pnpm dev` 会监听 `src/main/kotlin` 下的 `.kt` 文件变化，变更后重新编译并重启服务。

## 开发约定

- 新增接口时优先放在 `routes/` 注册路由，并将具体处理逻辑放到 `handlers/`。
- 响应模型优先放在 `models/`，避免在 handler 中散落匿名结构。
- 保持 Javalin 配置集中在 `http/`，不要把跨域、序列化等全局 HTTP 配置分散到业务 handler。
- 不要手动修改生成目录 `target/` 或 `build/`。
