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
- `pnpm test` 当前也是编译检查，与 `build`/`lint` 相同。
- `pnpm dev` 通过 nodemon 监听 `src/` 下的 `.kt` 文件变化，变更后自动重新编译并重启服务。

## 开发约定

- 新增接口时优先放在 `routes/` 注册路由，并将具体处理逻辑放到 `handlers/`。
- 请求和响应模型优先放在 `models/`，避免在 handler 中散落匿名结构。
- `models/` 下的 DTO 按业务域拆分文件，不要新增“所有响应/请求集中放一起”的聚合文件。命名建议为 `AuthModels.kt`、`DatasetModels.kt`、`SystemModels.kt`、`DemoModels.kt`，后续业务继续按路由/领域命名，例如 `TaskModels.kt`、`AnnotationModels.kt`。
- 保持 Javalin 配置集中在 `http/`，不要把跨域、序列化等全局 HTTP 配置分散到业务 handler。
- 不要手动修改生成目录 `target/` 或 `build/`。

## 路由组与中间件链

- 路由按业务域拆分到 `routes/*/*Routes.kt`，由 `routes/ApiRoutes.kt` 统一聚合。
- 公开路由可以直接使用 Javalin `get` / `post` / `put` / `patch` / `delete` 注册。
- 需要认证、角色校验、审计等前置逻辑的路由，优先使用 `routeGroup(...)` 注册，不要在每个 handler 中重复手写前置判断。
- 通用链式中间件定义在 `middleware/MiddlewareChain.kt`，中间件类型为 `RouteMiddleware`，形态是 `(ctx, next) -> Unit`。
- `routeGroup(requireAuth(authMiddleware)) { ... }` 会为组内所有路由套上登录认证；认证通过时中间件调用 `next()`，否则直接写入错误响应并停止后续 handler。
- `routeGroup` 内的 `get` / `post` 等方法来自 `routes/RouteGroup.kt`，它会在注册到 Javalin 前把原始 handler 包装成 `chain(middlewares, handler)`。

示例：

```kotlin
routeGroup(requireAuth(authMiddleware)) {
    get("/me", Handler { ctx -> MeHandler.show(ctx) })
}
```

后续新增角色权限时，优先实现为新的 `RouteMiddleware`，再组合到业务路由组中：

```kotlin
routeGroup(requireAuth(authMiddleware), requireRole("provider")) {
    post("/provider/datasets", Handler { ctx -> datasetHandler.create(ctx) })
    get("/provider/datasets", Handler { ctx -> datasetHandler.list(ctx) })
}
```
