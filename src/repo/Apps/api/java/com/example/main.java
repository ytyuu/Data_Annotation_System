package com.example;

import io.javalin.Javalin;

public class main {
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7000);

        // 定义根路由
        app.get("/", ctx -> ctx.result("Hello from Javalin!"));

        // 定义 hello 路由
        app.get("/hello/{name}", ctx -> {
            String name = ctx.pathParam("name");
            ctx.result("Hello, " + name + "!");
        });

        System.out.println("Javalin server started at http://localhost:7000");
    }
}