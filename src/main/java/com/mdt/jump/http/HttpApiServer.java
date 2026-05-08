package com.mdt.jump.http;

import com.mdt.jump.api.ComIdRecord;
import com.mdt.jump.api.JumpComIdApi;
import com.mdt.jump.config.PluginConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class HttpApiServer implements AutoCloseable {
    private final PluginConfiguration configuration;
    private final JumpComIdApi api;
    private HttpServer server;
    private ExecutorService executorService;

    public HttpApiServer(PluginConfiguration configuration, JumpComIdApi api) {
        this.configuration = configuration;
        this.api = api;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(configuration.getApiHost(), configuration.getApiPort()), 0);
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            private int index;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mdt-jump-http-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });
        server.setExecutor(executorService);
        server.createContext("/api/v1/health", new HealthHandler());
        server.createContext("/api/v1/com-id", new QueryByUuidHandler());
        server.createContext("/api/v1/com-id/reverse", new QueryByComIdHandler());
        server.start();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private final class QueryByUuidHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String uuid = query.get("uuid");
            if (uuid == null || uuid.trim().isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"uuid_required\"}");
                return;
            }

            boolean create = !"false".equalsIgnoreCase(query.getOrDefault("create", "true"));
            Optional<ComIdRecord> record = create ? Optional.of(api.getOrCreate(uuid)) : api.findByUuid(uuid);
            if (!record.isPresent()) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }

            sendJson(exchange, 200, toJson(record.get()));
        }
    }

    private final class QueryByComIdHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String comId = query.get("comId");
            if (comId == null || comId.trim().isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"com_id_required\"}");
                return;
            }

            Optional<ComIdRecord> record = api.findByComId(comId);
            if (!record.isPresent()) {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
                return;
            }

            sendJson(exchange, 200, toJson(record.get()));
        }
    }

    private boolean authorize(HttpExchange exchange) {
        if (!configuration.isApiRequireToken()) {
            return true;
        }

        String token = exchange.getRequestHeaders().getFirst("X-Api-Token");
        if (token == null || token.isEmpty()) {
            token = parseQuery(exchange.getRequestURI().getRawQuery()).get("token");
        }
        return configuration.getApiToken().equals(token);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] split = pair.split("=", 2);
            String key = decode(split[0]);
            String value = split.length > 1 ? decode(split[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 解码失败。", exception);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String toJson(ComIdRecord record) {
        return "{\"uuid\":\"" + escape(record.getUuid()) + "\",\"comId\":\"" + escape(record.getComId()) + "\"}";
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
