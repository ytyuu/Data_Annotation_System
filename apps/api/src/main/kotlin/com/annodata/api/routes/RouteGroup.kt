package com.annodata.api.routes

import com.annodata.api.middleware.RouteMiddleware
import com.annodata.api.middleware.chain
import io.javalin.apibuilder.ApiBuilder
import io.javalin.http.Handler

/**
 * 支持中间件链的路由组。
 *
 * 提供与 [ApiBuilder] 对应的 HTTP 方法注册函数，
 * 并在 handler 执行前按顺序运行关联的中间件链。
 *
 * @property middlewares 该路由组默认应用的中间件列表
 */
class RouteGroup(
    private val middlewares: List<RouteMiddleware> = emptyList(),
) {
    fun get(path: String, handler: Handler) {
        get(path, emptyList(), handler)
    }

    fun get(path: String, routeMiddlewares: List<RouteMiddleware>, handler: Handler) {
        ApiBuilder.get(path, chain(middlewares + routeMiddlewares, handler))
    }

    fun post(path: String, handler: Handler) {
        post(path, emptyList(), handler)
    }

    fun post(path: String, routeMiddlewares: List<RouteMiddleware>, handler: Handler) {
        ApiBuilder.post(path, chain(middlewares + routeMiddlewares, handler))
    }

    fun put(path: String, handler: Handler) {
        put(path, emptyList(), handler)
    }

    fun put(path: String, routeMiddlewares: List<RouteMiddleware>, handler: Handler) {
        ApiBuilder.put(path, chain(middlewares + routeMiddlewares, handler))
    }

    fun patch(path: String, handler: Handler) {
        patch(path, emptyList(), handler)
    }

    fun patch(path: String, routeMiddlewares: List<RouteMiddleware>, handler: Handler) {
        ApiBuilder.patch(path, chain(middlewares + routeMiddlewares, handler))
    }

    fun delete(path: String, handler: Handler) {
        delete(path, emptyList(), handler)
    }

    fun delete(path: String, routeMiddlewares: List<RouteMiddleware>, handler: Handler) {
        ApiBuilder.delete(path, chain(middlewares + routeMiddlewares, handler))
    }

    /**
     * 返回应用了额外中间件的新路由组。
     *
     * @param extraMiddlewares 追加的中间件
     */
    fun with(vararg extraMiddlewares: RouteMiddleware): RouteGroup {
        return RouteGroup(middlewares + extraMiddlewares.toList())
    }
}

/**
 * 创建一个带有指定中间件的路由组并注册路由。
 *
 * @param middlewares 该路由组应用的中间件列表
 * @param routes 路由注册闭包
 */
fun routeGroup(
    vararg middlewares: RouteMiddleware,
    routes: RouteGroup.() -> Unit,
) {
    RouteGroup(middlewares.toList()).routes()
}
