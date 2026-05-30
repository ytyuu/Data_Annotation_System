/**
 * 演示接口路由注册。
 */
package com.example.api.routes.demo

import com.example.api.handlers.demo.GreetingHandler
import io.javalin.apibuilder.ApiBuilder.get

/**
 * 注册演示相关路由（/hello）。
 */
fun registerDemoRoutes() {
    get("/hello") { ctx -> GreetingHandler.show(ctx) }
}
