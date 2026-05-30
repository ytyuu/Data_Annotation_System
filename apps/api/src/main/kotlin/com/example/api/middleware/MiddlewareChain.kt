package com.example.api.middleware

import io.javalin.http.Context
import io.javalin.http.Handler

/** 中间件链中的下一个执行点。 */
typealias Next = () -> Unit

/** 路由中间件类型，接收请求上下文和下一个执行点。 */
typealias RouteMiddleware = (Context, Next) -> Unit

/**
 * 将中间件列表和端点 handler 组合成一条执行链。
 *
 * @param middlewares 中间件列表，按顺序执行
 * @param endpoint 最终的端点 handler
 * @return 组合后的 handler
 */
fun chain(
    middlewares: List<RouteMiddleware>,
    endpoint: Handler,
): Handler {
    fun run(index: Int, ctx: Context) {
        if (index == middlewares.size) {
            endpoint.handle(ctx)
            return
        }

        middlewares[index](ctx) {
            run(index + 1, ctx)
        }
    }

    return Handler { ctx -> run(0, ctx) }
}

fun chain(
    vararg middlewares: RouteMiddleware,
    endpoint: Handler,
): Handler = chain(middlewares.toList(), endpoint)
