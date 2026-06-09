/**
 * 演示接口路由注册。
 */
package com.annodata.api.routes.demo

import com.annodata.api.handlers.demo.GreetingHandler
import io.javalin.apibuilder.ApiBuilder.get

/**
 * 注册演示相关路由（/hello）。
 */
fun registerDemoRoutes() {
    get("/hello") { ctx -> GreetingHandler.show(ctx) }
}
