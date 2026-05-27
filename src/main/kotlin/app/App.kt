package app

import io.javalin.Javalin

fun main(args: Array<String>) {
    println("Starting Kotlin + Javalin application...")
    if (args.isNotEmpty()) {
        println("Arguments: ${args.joinToString()}")
    }

    startServer()
}

fun startServer(): Javalin {
    return Javalin.create { config ->
        config.defaultContentType = "application/json"
    }.apply {
        get("/") { ctx ->
            ctx.result("""{"message":"Hello from Kotlin!"}""")
        }

        get("/hello/:name") { ctx ->
            val name = ctx.pathParam("name")
            ctx.result("""{"message":"Hello, $name!"}""")
        }
    }.start(7000)
}
