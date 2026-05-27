package com.example.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public final class Main {
    private static final int DEFAULT_PORT = 7000;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendNoContent(exchange);
                return;
            }

            if ("/".equals(path)) {
                sendJson(exchange, 200,
                    "{\"message\":\"API server is running\",\"port\":" + port + ",\"timestamp\":\"" + Instant.now() + "\"}");
                return;
            }

            sendJson(exchange, 404, "{\"error\":\"Not Found\",\"path\":\"" + escapeJson(path) + "\"}");
        });
        server.createContext("/api/health", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendNoContent(exchange);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.createContext("/api/hello", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendNoContent(exchange);
                return;
            }
            String name = queryParam(exchange.getRequestURI(), "name", "world");
            sendJson(exchange, 200, "{\"message\":\"Good Morning, " + escapeJson(name) + "!\"}");
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("API server started at http://localhost:" + port);
        Thread.currentThread().join();
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            Integer parsed = parsePort(args[0]);
            if (parsed != null) {
                return parsed;
            }
        }

        String envPort = System.getenv("PORT");
        Integer parsed = parsePort(envPort);
        return parsed != null ? parsed : DEFAULT_PORT;
    }

    private static Integer parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                return null;
            }
            return port;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        Objects.requireNonNull(body, "body");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "content-type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,OPTIONS");
    }

    private static String queryParam(URI uri, String key, String defaultValue) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return defaultValue;
        }

        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String candidateKey = index >= 0 ? pair.substring(0, index) : pair;
            if (!candidateKey.equals(key)) {
                continue;
            }

            String value = index >= 0 ? pair.substring(index + 1) : "";
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            return decoded.isBlank() ? defaultValue : decoded;
        }

        return defaultValue;
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}

