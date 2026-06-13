package com.campus.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 嵌入式前端静态文件服务器 + API 反向代理，绑定 80 端口，无需 Nginx。
 * 仅在 standalone 模式下由启动脚本独立运行。
 * <p>
 * 用法: java FrontendServer [port] [frontendDir] [gatewayUrl]
 * 默认: port=80, frontendDir=frontend, gatewayUrl=http://localhost:8080
 */
public class FrontendServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 80;
        String frontendDir = args.length > 1 ? args[1] : "frontend";
        String gatewayUrl = args.length > 2 ? args[2] : "http://localhost:8080";

        // 去掉尾部斜杠
        if (gatewayUrl.endsWith("/")) {
            gatewayUrl = gatewayUrl.substring(0, gatewayUrl.length() - 1);
        }

        Path root = Paths.get(frontendDir).toAbsolutePath();

        if (!Files.isDirectory(root)) {
            System.err.println("[FrontendServer] 前端目录不存在: " + root);
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));

        server.createContext("/", new StaticFileHandler(root, gatewayUrl));
        server.start();
        System.out.println("[FrontendServer] 已启动: http://localhost:" + port
                + " | 静态目录: " + root
                + " | API 代理 → " + gatewayUrl);
    }

    record StaticFileHandler(Path root, String gatewayUrl) implements HttpHandler {

        private static final int PROXY_CONNECT_TIMEOUT = 5000;
        private static final int PROXY_READ_TIMEOUT = 30000;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();

            // 静态文件：/ 返回 index.html
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // API 请求 → 代理转发到 Gateway
            if (path.startsWith("/api/")) {
                proxyToGateway(exchange, method, path, query);
                return;
            }

            // 静态文件服务
            serveStaticFile(exchange, path);
        }

        /**
         * 将 /api/** 请求代理转发到 Gateway
         */
        private void proxyToGateway(HttpExchange exchange, String method,
                                     String path, String query) {
            try {
                String targetUrl = gatewayUrl + path;
                if (query != null && !query.isEmpty()) {
                    targetUrl += "?" + query;
                }

                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(PROXY_CONNECT_TIMEOUT);
                conn.setReadTimeout(PROXY_READ_TIMEOUT);
                conn.setInstanceFollowRedirects(false);

                // 转发原始请求头（排除 Host）
                for (Map.Entry<String, List<String>> header : exchange.getRequestHeaders().entrySet()) {
                    String key = header.getKey();
                    if (key == null || key.equalsIgnoreCase("Host")) continue;
                    for (String value : header.getValue()) {
                        conn.addRequestProperty(key, value);
                    }
                }

                // 转发请求体（POST/PUT/PATCH）
                if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                    conn.setDoOutput(true);
                    try (InputStream is = exchange.getRequestBody();
                         OutputStream os = conn.getOutputStream()) {
                        is.transferTo(os);
                    }
                }

                // 发送响应
                int statusCode = conn.getResponseCode();

                // 转发响应头
                for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                    String key = header.getKey();
                    if (key == null || key.equalsIgnoreCase("Transfer-Encoding")) continue;
                    for (String value : header.getValue()) {
                        exchange.getResponseHeaders().add(key, value);
                    }
                }

                // 读取响应体
                byte[] body;
                try (InputStream is = (statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream())) {
                    if (is != null) {
                        body = is.readAllBytes();
                    } else {
                        body = new byte[0];
                    }
                }

                // 发送响应
                exchange.sendResponseHeaders(statusCode, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }

            } catch (ConnectException e) {
                // Gateway 未启动
                sendError(exchange, 502, "Gateway 未启动或无法连接: " + gatewayUrl);
            } catch (SocketTimeoutException e) {
                sendError(exchange, 504, "Gateway 请求超时");
            } catch (IOException e) {
                sendError(exchange, 502, "代理转发失败: " + e.getMessage());
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) {
            try {
                byte[] body = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(code, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (IOException ignored) {
                // 无法发送错误响应，放弃
            }
        }

        /**
         * 提供静态文件服务，找不到时走 SPA fallback
         */
        private void serveStaticFile(HttpExchange exchange, String path) throws IOException {
            Path file = root.resolve(path.substring(1)).normalize();

            // 安全检查：防止路径穿越
            if (!file.startsWith(root)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            if (Files.isRegularFile(file)) {
                String ct = getContentType(file.toString());
                exchange.getResponseHeaders().set("Content-Type", ct);
                exchange.sendResponseHeaders(200, Files.size(file));
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file, os);
                }
            } else {
                // SPA fallback: 所有未知路由返回 index.html
                Path index = root.resolve("index.html");
                if (Files.isRegularFile(index)) {
                    exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
                    exchange.sendResponseHeaders(200, Files.size(index));
                    try (OutputStream os = exchange.getResponseBody()) {
                        Files.copy(index, os);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            }
        }

        private String getContentType(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html;charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css;charset=UTF-8";
            if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript;charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json;charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".woff")) return "font/woff";
            if (lower.endsWith(".woff2")) return "font/woff2";
            return "application/octet-stream";
        }
    }
}
