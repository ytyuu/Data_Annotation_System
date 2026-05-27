package app

import io.javalin.Javalin

fun main() {
    val app = Javalin.create { config ->
        config.defaultContentType = "application/json"
    }.start(7000)

    app.get("/") { ctx ->
        ctx.result("""{"message":"Hello from Javalin + Kotlin + Java 25!"}""")
    }

    app.get("/hello/:name") { ctx ->
        val name = ctx.pathParam("name")
        ctx.result("""{"message":"Hello, $name!"}""")
    }
}

